package io.provenance.client.grpc

import com.google.protobuf.ByteString
import cosmos.base.tendermint.v1beta1.getLatestBlockRequest
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastMode
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastTxRequest
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastTxResponse
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.tx.v1beta1.TxOuterClass.TxRaw
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.StatusRuntimeException
import io.grpc.stub.AbstractStub
import io.grpc.stub.MetadataUtils
import io.provenance.client.common.exceptions.TransactionTimeoutException
import io.provenance.client.common.extensions.txHash
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.protobuf.extensions.getBaseAccount
import io.provenance.client.protobuf.extensions.getTx
import io.provenance.msgfees.v1.QueryParamsRequest
import java.io.Closeable
import java.net.URI
import java.util.concurrent.TimeUnit

open class AbstractPbClient<T : ManagedChannelBuilder<T>>(
    open val chainId: String,
    open val channelUri: URI,
    open val gasEstimationMethod: GasEstimator,
    open val fromAddress: (String, Int) -> T,
    channel: ManagedChannel,
) : Closeable {

    // Graceful shutdown of the grpc managed channel.
    private val channelClose: () -> Unit = { channel.shutdown().awaitTermination(10, TimeUnit.SECONDS) }

    override fun close() = channelClose()

    // Service clients
    val cosmosService = cosmos.tx.v1beta1.ServiceGrpc.newBlockingStub(channel)
    val tendermintService = cosmos.base.tendermint.v1beta1.ServiceGrpc.newBlockingStub(channel)

    // Query clients
    val attributeClient = io.provenance.attribute.v1.QueryGrpc.newBlockingStub(channel)
    val authClient = cosmos.auth.v1beta1.QueryGrpc.newBlockingStub(channel)
    val authzClient = cosmos.authz.v1beta1.QueryGrpc.newBlockingStub(channel)
    val bankClient = cosmos.bank.v1beta1.QueryGrpc.newBlockingStub(channel)
    val channelClient = ibc.core.channel.v1.QueryGrpc.newBlockingStub(channel)
    val clientClient = ibc.core.client.v1.QueryGrpc.newBlockingStub(channel)
    val connectionClient = ibc.core.connection.v1.QueryGrpc.newBlockingStub(channel)
    val distributionClient = cosmos.distribution.v1beta1.QueryGrpc.newBlockingStub(channel)
    val evidenceClient = cosmos.evidence.v1beta1.QueryGrpc.newBlockingStub(channel)
    val exchangeClient = io.provenance.exchange.v1.QueryGrpc.newBlockingStub(channel)
    val feegrantClient = cosmos.feegrant.v1beta1.QueryGrpc.newBlockingStub(channel)
    val govClient = cosmos.gov.v1beta1.QueryGrpc.newBlockingStub(channel)
    val groupClient = cosmos.group.v1.QueryGrpc.newBlockingStub(channel)
    val holdClient = io.provenance.hold.v1.QueryGrpc.newBlockingStub(channel)
    val markerClient = io.provenance.marker.v1.QueryGrpc.newBlockingStub(channel)
    val metadataClient = io.provenance.metadata.v1.QueryGrpc.newBlockingStub(channel)
    val mintClient = cosmos.mint.v1beta1.QueryGrpc.newBlockingStub(channel)

    // @TestnetFeaturePreview
    val msgFeeClient = io.provenance.msgfees.v1.QueryGrpc.newBlockingStub(channel)

    val nameClient = io.provenance.name.v1.QueryGrpc.newBlockingStub(channel)
    val paramsClient = cosmos.params.v1beta1.QueryGrpc.newBlockingStub(channel)
    val quarantineClient = cosmos.quarantine.v1beta1.QueryGrpc.newBlockingStub(channel)
    val sanctionClient = cosmos.sanction.v1beta1.QueryGrpc.newBlockingStub(channel)
    val slashingClient = cosmos.slashing.v1beta1.QueryGrpc.newBlockingStub(channel)
    val stakingClient = cosmos.staking.v1beta1.QueryGrpc.newBlockingStub(channel)
    val transferClient = ibc.applications.transfer.v1.QueryGrpc.newBlockingStub(channel)
    val upgradeClient = cosmos.upgrade.v1beta1.QueryGrpc.newBlockingStub(channel)
    val wasmClient = cosmwasm.wasm.v1.QueryGrpc.newBlockingStub(channel)

    // @TestnetFeaturePreview
    val nodeFeeParams = lazy { msgFeeClient.params(QueryParamsRequest.getDefaultInstance()).params }

    // @TestnetFeaturePreview
    val nodeGasPrice = lazy { nodeFeeParams.value.floorGasPrice.amount.toDouble() }

    fun baseRequest(
        txBody: TxOuterClass.TxBody,
        signers: List<BaseReqSigner>,
        gasAdjustment: Double? = null,
        feeGranter: String? = null,
        feePayer: String? = null,
    ): BaseReq =
        signers.map {
            BaseReqSigner(
                signer = it.signer,
                sequenceOffset = it.sequenceOffset,
                account = it.account ?: this.authClient.getBaseAccount(it.signer.address())
            )
        }.let {
            BaseReq(
                signers = it,
                body = txBody,
                chainId = chainId,
                gasAdjustment = gasAdjustment,
                feeGranter = feeGranter,
                feePayer = feePayer,
            )
        }

    fun estimateTx(baseReq: BaseReq): GasEstimate {
        val tx = TxOuterClass.Tx.newBuilder()
            .setBody(baseReq.body)
            .setAuthInfo(baseReq.buildAuthInfo())
            // signatures are not used for estimates, but a value is required for each signer
            .addAllSignatures(baseReq.signers.map { ByteString.EMPTY })
            .build()
        val gasAdjustment = baseReq.gasAdjustment ?: GasEstimate.DEFAULT_FEE_ADJUSTMENT

        return gasEstimationMethod(tx, gasAdjustment)
    }

    /**
     * Broadcast a transaction.
     *
     * @param baseReq request information
     * @param gasEstimate the approved gas estimate for the transaction
     * @param mode broadcast mode
     * @param txHashHandler function called before broadcast with computed txhash value
     * @param signatures ordered list of signed bytes provided by each signer;
     *                   any null/empty values will be signed using the Signer implementation
     */
    fun broadcastTx(
        baseReq: BaseReq,
        gasEstimate: GasEstimate,
        mode: BroadcastMode = BroadcastMode.BROADCAST_MODE_SYNC,
        txHashHandler: PreBroadcastTxHashHandler? = null,
        signatures: List<ByteArray?> = List(baseReq.signers.size) { null },
    ): BroadcastTxResponse {
        val authInfoBytes = baseReq.buildAuthInfo(gasEstimate).toByteString()
        val txBodyBytes = baseReq.body.toByteString()

        require(signatures.size == baseReq.signers.size) {
            "The number of signatures must match the number of signers. A null/empty signature entry will sign using the Signer implementation."
        }

        val txRaw = baseReq.signers.mapIndexed { index, baseReqSigner ->
            signatures[index]?.takeIf { it.isNotEmpty() }
                ?: baseReqSigner.signer.sign(
                    baseReq.buildSignDoc(baseReqSigner, authInfoBytes, txBodyBytes).toByteArray()
                )
        }
            .map { ByteString.copyFrom(it) }
            .let {
                TxRaw.newBuilder()
                    .setAuthInfoBytes(authInfoBytes)
                    .setBodyBytes(txBodyBytes)
                    .addAllSignatures(it)
                    .build()
            }

        txHashHandler?.let { it(txRaw.txHash()) }

        return txRaw.emulateBlockMode(mode, baseReq.body.timeoutHeight) { req ->
            cosmosService.broadcastTx(req)
        }
    }

    /**
     * Estimate and broadcast a transaction.
     *
     * @param txBody complete transaction body
     * @param signers list of required signers of the transaction
     * @param mode broadcast mode
     * @param gasAdjustment gas adjustment factor
     * @param feeGranter address of fee granter
     * @param feePayer address of fee payer
     * @param txHashHandler function called before broadcast with computed txhash value
     * @param signatures ordered list of signed bytes provided by each signer;
     *                   any null/empty values will be signed using the Signer implementation
     */
    fun estimateAndBroadcastTx(
        txBody: TxOuterClass.TxBody,
        signers: List<BaseReqSigner>,
        mode: BroadcastMode = BroadcastMode.BROADCAST_MODE_SYNC,
        gasAdjustment: Double? = null,
        feeGranter: String? = null,
        feePayer: String? = null,
        txHashHandler: PreBroadcastTxHashHandler? = null,
        signatures: List<ByteArray?> = List(signers.size) { null },
    ): BroadcastTxResponse =
        baseRequest(
            txBody = txBody,
            signers = signers,
            gasAdjustment = gasAdjustment,
            feeGranter = feeGranter,
            feePayer = feePayer,
        ).let { baseReq ->
            broadcastTx(
                baseReq = baseReq,
                gasEstimate = estimateTx(baseReq),
                mode = mode,
                txHashHandler = txHashHandler,
                signatures = signatures
            )
        }

    private fun TxRaw.emulateBlockMode(
        mode: BroadcastMode,
        providedTimeoutHeight: Long,
        handler: (BroadcastTxRequest) -> BroadcastTxResponse
    ): BroadcastTxResponse {
        val (actualMode, simulateBlock) = if (mode == BroadcastMode.BROADCAST_MODE_BLOCK) {
            BroadcastMode.BROADCAST_MODE_SYNC to true
        } else {
            mode to false
        }
        return handler(BroadcastTxRequest.newBuilder()
            .setTxBytes(this.toByteString())
            .setMode(actualMode)
            .build()
        ).let { res ->
            if (simulateBlock && res.txResponse.code == 0) {
                val timeoutHeight = providedTimeoutHeight.takeIf { it > 0 } ?: (latestHeight() + 10) // default to 10 block timeout for polling if no height set
                val txHash = res.txResponse.txhash
                do {
                    try {
                        val tx = cosmosService.getTx(txHash)
                        return res.toBuilder()
                            .setTxResponse(tx.txResponse)
                            .build()
                    } catch (e: StatusRuntimeException) {
                        if (e.message?.contains("not found") == true) {
                            Thread.sleep(1000)
                            continue
                        }
                        throw e
                    }
                } while (latestHeight() <= timeoutHeight)
                throw TransactionTimeoutException("Failed to complete transaction with hash $txHash by height $timeoutHeight")
            } else res
        }
    }

    fun latestHeight() = this@AbstractPbClient.tendermintService.getLatestBlock(getLatestBlockRequest {  }).block.header.height
}

/**
 * Fetch the floor gas price a node supports.
 */
fun nodeFloorGasPrice(channel: Channel) = io.provenance.msgfees.v1.QueryGrpc.newBlockingStub(channel)
    .params(QueryParamsRequest.getDefaultInstance())
    .params
    .floorGasPrice

/**
 * CosmosSDK header to include to target a block height on a grpc call.
 */
const val BLOCK_HEIGHT = "x-cosmos-block-height"

/**
 * Add a block height to a stub to allow fetching data at certain heights
 *
 * NOTE: For best results, use this against a full archival node. This addition
 * requires a node with the data at [blockHeight] to not be pruned.
 *
 * @param blockHeight The block height to target.
 * @return The grpc stub with the block header interceptor added.
 */
fun <S : AbstractStub<S>> S.addBlockHeight(blockHeight: String): S {
    val metadata = Metadata()
    metadata.put(Metadata.Key.of(BLOCK_HEIGHT, Metadata.ASCII_STRING_MARSHALLER), blockHeight)
    return withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
}

typealias PreBroadcastTxHashHandler = (String) -> Unit

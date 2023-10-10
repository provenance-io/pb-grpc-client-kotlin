package io.provenance.client.coroutines

import com.google.protobuf.ByteString
import cosmos.auth.v1beta1.QueryGrpcKt
import cosmos.auth.v1beta1.QueryOuterClass
import cosmos.base.tendermint.v1beta1.getLatestBlockRequest
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastMode
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastTxRequest
import cosmos.tx.v1beta1.ServiceOuterClass.BroadcastTxResponse
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import cosmos.tx.v1beta1.getTxRequest
import cosmos.tx.v1beta1.tx
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.grpc.netty.NettyChannelBuilder
import io.provenance.client.common.exceptions.TransactionTimeoutException
import io.provenance.client.common.extensions.txHash
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.grpc.BaseReq
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.ChannelOpts
import io.provenance.client.grpc.grpcChannel
import kotlinx.coroutines.delay
import java.io.Closeable
import java.net.URI
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds


open class PbCoroutinesClient(
    val chainId: String,
    val channelUri: URI,
    val gasEstimationMethod: GasEstimator,
    val opts: ChannelOpts = ChannelOpts(),
    channelConfigLambda: (NettyChannelBuilder) -> Unit = { }
) : Closeable {

    private val channel = grpcChannel(channelUri, opts, NettyChannelBuilder::forAddress)

    override fun close() {
        channel.shutdown().awaitTermination(10, TimeUnit.SECONDS)
        if (opts.executor is ThreadPoolExecutor) {
            (opts.executor as ThreadPoolExecutor).shutdown()
        }
    }

    // Service clients
    val cosmosService = cosmos.tx.v1beta1.ServiceGrpcKt.ServiceCoroutineStub(channel)
    val tendermintService = cosmos.base.tendermint.v1beta1.ServiceGrpcKt.ServiceCoroutineStub(channel)

    // Kotlin things.
    val authClient = cosmos.auth.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val attributesClient = io.provenance.attribute.v1.QueryGrpcKt.QueryCoroutineStub(channel)
    val authzClient = cosmos.authz.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val bankClient = cosmos.bank.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val channelClient = ibc.core.channel.v1.QueryGrpcKt.QueryCoroutineStub(channel)
    val clientClient = ibc.core.client.v1.QueryGrpcKt.QueryCoroutineStub(channel)
    val connectionClient = ibc.core.connection.v1.QueryGrpcKt.QueryCoroutineStub(channel)
    val distributionClient = cosmos.distribution.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val evidenceClient = cosmos.evidence.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val feegrantClient = cosmos.feegrant.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val govClient = cosmos.gov.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val markerClient = io.provenance.marker.v1.QueryGrpcKt.QueryCoroutineStub(channel)
    val metadataClient = io.provenance.metadata.v1.QueryGrpcKt.QueryCoroutineStub(channel)
    val mintClient = cosmos.mint.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val msgFeeClient = io.provenance.msgfees.v1.QueryGrpcKt.QueryCoroutineStub(channel)
    val nameClient = io.provenance.name.v1.QueryGrpcKt.QueryCoroutineStub(channel)
    val paramsClient = cosmos.params.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val slashingClient = cosmos.slashing.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val stakingClient = cosmos.staking.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val transferClient = ibc.applications.transfer.v1.QueryGrpcKt.QueryCoroutineStub(channel)
    val upgradeClient = cosmos.upgrade.v1beta1.QueryGrpcKt.QueryCoroutineStub(channel)
    val wasmClient = cosmwasm.wasm.v1.QueryGrpcKt.QueryCoroutineStub(channel)

    suspend fun baseRequest(
        txBody: TxBody,
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

    suspend fun estimateTx(baseReq: BaseReq): GasEstimate {
        val transaction = tx {
            body = baseReq.body
            authInfo = baseReq.buildAuthInfo()
            signatures.add(ByteString.EMPTY)
        }

        val gasAdjustment = baseReq.gasAdjustment ?: GasEstimate.DEFAULT_FEE_ADJUSTMENT
        return gasEstimationMethod(this, transaction, gasAdjustment)
    }

    private fun buildTx(
        baseReq: BaseReq,
        gasEstimate: GasEstimate,
    ): TxOuterClass.TxRaw {
        val authInfoBytes = baseReq.buildAuthInfo(gasEstimate).toByteString()
        val txBodyBytes = baseReq.body.toByteString()

        val signedTxs = baseReq.buildSignDocBytesList(authInfoBytes, txBodyBytes).mapIndexed { index, signDocBytes ->
            baseReq.signers[index].signer.sign(signDocBytes).let { ByteString.copyFrom(it) }
        }

        return TxOuterClass.TxRaw.newBuilder()
            .setAuthInfoBytes(authInfoBytes)
            .setBodyBytes(txBodyBytes)
            .addAllSignatures(signedTxs)
            .build()
    }

    suspend fun broadcastTx(
        baseReq: BaseReq,
        gasEstimate: GasEstimate,
        mode: BroadcastMode = BroadcastMode.BROADCAST_MODE_SYNC,
        txHashHandler: PreBroadcastTxHashHandler? = null,
    ): BroadcastTxResponse {
        return buildTx(baseReq, gasEstimate)
            .also { txRaw ->
                txHashHandler?.let { it(txRaw.txHash()) }
            }
            .emulateBlockMode(mode, baseReq.body.timeoutHeight) {
               cosmosService.broadcastTx(it)
            }
    }

    suspend fun estimateAndBroadcastTx(
        txBody: TxBody,
        signers: List<BaseReqSigner>,
        mode: BroadcastMode = BroadcastMode.BROADCAST_MODE_SYNC,
        gasAdjustment: Double? = null,
        feeGranter: String? = null,
        feePayer: String? = null,
        txHashHandler: PreBroadcastTxHashHandler? = null,
    ): BroadcastTxResponse = baseRequest(
        txBody = txBody,
        signers = signers,
        gasAdjustment = gasAdjustment,
        feeGranter = feeGranter,
        feePayer = feePayer,
    ).let { baseReq -> broadcastTx(baseReq, estimateTx(baseReq), mode, txHashHandler) }

    private suspend fun TxOuterClass.TxRaw.emulateBlockMode(
        mode: BroadcastMode,
        providedTimeoutHeight: Long,
        handler: suspend (BroadcastTxRequest) -> BroadcastTxResponse
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
            if (simulateBlock) {
                val timeoutHeight = providedTimeoutHeight.takeIf { it > 0 } ?: (latestHeight() + 10) // default to 10 block timeout for polling if no height set
                val txHash = res.txResponse.txhash
                do {
                    try {
                        val tx = cosmosService.getTx(getTxRequest { hash = txHash })
                        return res.toBuilder()
                            .setTxResponse(tx.txResponse)
                            .build()
                    } catch (e: StatusException) {
                        if (e.message?.contains("not found") == true) {
                            delay(1000.milliseconds)
                            continue
                        }
                        throw e
                    }
                } while (latestHeight() <= timeoutHeight)
                throw TransactionTimeoutException("Failed to complete transaction with hash $txHash by height $timeoutHeight")
            } else res
        }
    }

    suspend fun latestHeight() = this.tendermintService.getLatestBlock(getLatestBlockRequest {  }).block.header.height
}

/**
 * Given an address, get the base account associated with it.
 *
 * See [Accounts](https://github.com/FigureTechnologies/service-wallet/blob/v45/pb-client/src/main/kotlin/com/figure/wallet/pbclient/client/grpc/Accounts.kt#L18).
 *
 * @param bech32Address The bech32 address to fetch.
 * @return [cosmos.auth.v1beta1.Auth.BaseAccount] or throw [IllegalArgumentException] if the account type is not supported.
 */
suspend fun QueryGrpcKt.QueryCoroutineStub.getBaseAccount(bech32Address: String): cosmos.auth.v1beta1.Auth.BaseAccount =
    account(QueryOuterClass.QueryAccountRequest.newBuilder().setAddress(bech32Address).build()).account.run {
        when {
            this.`is`(cosmos.auth.v1beta1.Auth.BaseAccount::class.java) -> unpack(cosmos.auth.v1beta1.Auth.BaseAccount::class.java)
            else -> throw IllegalArgumentException("Account type not handled:$typeUrl")
        }
    }

typealias PreBroadcastTxHashHandler = suspend (String) -> Unit
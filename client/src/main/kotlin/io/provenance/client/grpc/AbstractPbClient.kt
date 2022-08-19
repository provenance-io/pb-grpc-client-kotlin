package io.provenance.client.grpc

import com.google.protobuf.ByteString
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.stub.AbstractStub
import io.grpc.stub.MetadataUtils
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.protobuf.extensions.getBaseAccount
import io.provenance.msgfees.v1.QueryParamsRequest
import java.io.Closeable
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 *
 */
open class AbstractPbClient<T : ManagedChannelBuilder<T>>(
    open val chainId: String,
    open val channelUri: URI,
    open val gasEstimationMethod: GasEstimator,
    open val fromAddress: (String, Int) -> T,
    opts: ChannelOpts = ChannelOpts(),
    channel: ManagedChannel,
) : Closeable {

    // Graceful shutdown of the grpc managed channel.
    private val channelClose: () -> Unit = {
        channel.shutdown()
        channel.shutdown().awaitTermination(10, TimeUnit.SECONDS)
    }

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
    val feegrantClient = cosmos.feegrant.v1beta1.QueryGrpc.newBlockingStub(channel)
    val govClient = cosmos.gov.v1beta1.QueryGrpc.newBlockingStub(channel)
    val markerClient = io.provenance.marker.v1.QueryGrpc.newBlockingStub(channel)
    val metadataClient = io.provenance.metadata.v1.QueryGrpc.newBlockingStub(channel)
    val mintClient = cosmos.mint.v1beta1.QueryGrpc.newBlockingStub(channel)

    // @TestnetFeaturePreview
    val msgFeeClient = io.provenance.msgfees.v1.QueryGrpc.newBlockingStub(channel)

    val nameClient = io.provenance.name.v1.QueryGrpc.newBlockingStub(channel)
    val paramsClient = cosmos.params.v1beta1.QueryGrpc.newBlockingStub(channel)
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
                feeGranter = feeGranter
            )
        }

    fun estimateTx(baseReq: BaseReq): GasEstimate {
        val tx = TxOuterClass.Tx.newBuilder()
            .setBody(baseReq.body)
            .setAuthInfo(baseReq.buildAuthInfo())
            .build()

        return baseReq.buildSignDocBytesList(tx.authInfo.toByteString(), tx.body.toByteString())
            .mapIndexed { index, signDocBytes ->
                baseReq.signers[index].signer.sign(signDocBytes).let { ByteString.copyFrom(it) }
            }.let { signatures ->
                val signedTx = tx.toBuilder().addAllSignatures(signatures).build()
                val gasAdjustment = baseReq.gasAdjustment ?: GasEstimate.DEFAULT_FEE_ADJUSTMENT
                gasEstimationMethod(signedTx, gasAdjustment)
            }
    }

    fun broadcastTx(
        baseReq: BaseReq,
        gasEstimate: GasEstimate,
        mode: ServiceOuterClass.BroadcastMode = ServiceOuterClass.BroadcastMode.BROADCAST_MODE_SYNC
    ): ServiceOuterClass.BroadcastTxResponse {

        val authInfoBytes = baseReq.buildAuthInfo(gasEstimate).toByteString()
        val txBodyBytes = baseReq.body.toByteString()

        val txRaw = baseReq.buildSignDocBytesList(authInfoBytes, txBodyBytes).mapIndexed { index, signDocBytes ->
            baseReq.signers[index].signer.sign(signDocBytes).let { ByteString.copyFrom(it) }
        }.let {
            TxOuterClass.TxRaw.newBuilder()
                .setAuthInfoBytes(authInfoBytes)
                .setBodyBytes(txBodyBytes)
                .addAllSignatures(it)
                .build()
        }

        return cosmosService.broadcastTx(
            ServiceOuterClass.BroadcastTxRequest.newBuilder().setTxBytes(txRaw.toByteString()).setMode(mode).build()
        )
    }

    fun estimateAndBroadcastTx(
        txBody: TxOuterClass.TxBody,
        signers: List<BaseReqSigner>,
        mode: ServiceOuterClass.BroadcastMode = ServiceOuterClass.BroadcastMode.BROADCAST_MODE_SYNC,
        gasAdjustment: Double? = null,
        feeGranter: String? = null
    ): ServiceOuterClass.BroadcastTxResponse = baseRequest(
        txBody = txBody,
        signers = signers,
        gasAdjustment = gasAdjustment,
        feeGranter = feeGranter
    ).let { baseReq -> broadcastTx(baseReq, estimateTx(baseReq), mode) }
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
    val metadata = io.grpc.Metadata()
    metadata.put(io.grpc.Metadata.Key.of(BLOCK_HEIGHT, Metadata.ASCII_STRING_MARSHALLER), blockHeight)
    return withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
}

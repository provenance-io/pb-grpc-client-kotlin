package io.provenance.client.grpc

import com.google.protobuf.ByteString
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.provenance.client.protobuf.extensions.getBaseAccount
import io.provenance.msgfees.v1.QueryParamsRequest
import java.io.Closeable
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ChannelOpts(
    val inboundMessageSize: Int = 40 * 1024 * 1024, // ~ 20 MB
    val idleTimeout: Pair<Long, TimeUnit> = 5L to TimeUnit.MINUTES,
    val keepAliveTime: Pair<Long, TimeUnit> = 60L to TimeUnit.SECONDS, // ~ 12 pbc block cuts
    val keepAliveTimeout: Pair<Long, TimeUnit> = 20L to TimeUnit.SECONDS,
    val executor: ExecutorService = Executors.newFixedThreadPool(8)
)

open class PbClient(
    val chainId: String,
    val channelUri: URI,
    val gasEstimationMethod: PbGasEstimator,
    opts: ChannelOpts = ChannelOpts(),
    channelConfigLambda: (NettyChannelBuilder) -> Unit = { }
) : Closeable {

    companion object {
        private val SECURE_URL_SCHEMES = listOf("https", "grpcs")
    }

    val channel = NettyChannelBuilder.forAddress(channelUri.host, channelUri.port)
        .apply {
            if (channelUri.scheme in SECURE_URL_SCHEMES) {
                useTransportSecurity()
            } else {
                usePlaintext()
            }
        }
        .executor(opts.executor)
        .maxInboundMessageSize(opts.inboundMessageSize)
        .idleTimeout(opts.idleTimeout.first, opts.idleTimeout.second)
        .keepAliveTime(opts.keepAliveTime.first, opts.keepAliveTime.second)
        .keepAliveTimeout(opts.keepAliveTimeout.first, opts.keepAliveTimeout.second)
        .also { builder -> channelConfigLambda(builder) }
        .build()

    override fun close() {
        channel.shutdown()
    }

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
        txBody: TxBody,
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
                val gasEstimator = gasEstimationMethod()
                gasEstimator(signedTx, gasAdjustment)
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
        txBody: TxBody,
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

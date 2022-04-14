package io.provenance.client.grpc

import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.provenance.client.grpc.channel.NETTY_CHANNEL
import com.google.protobuf.ByteString
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.protobuf.extensions.getBaseAccount
import io.provenance.msgfees.v1.QueryParamsRequest
import java.io.Closeable
import java.net.URI

/**
 *
 */
fun nettyPbClient(
    chainId: String,
    channelUri: URI,
    gasEstimationMethod: PbGasEstimator,
    opts: ChannelOpts = ChannelOpts(),
    channelConfigLambda: (NettyChannelBuilder) -> Unit = { },
    channel: ManagedChannel = grpcChannel(channelUri, opts, NETTY_CHANNEL, channelConfigLambda),
) = PbClient(chainId, channelUri, gasEstimationMethod, opts, channelConfigLambda, channel)

val SECURE_URL_SCHEMES = listOf("https", "grpcs", "tcp+tls")

fun createChannel(uri: URI, opts: ChannelOpts, config: NettyChannelBuilder.() -> Unit): ManagedChannel {
    return NettyChannelBuilder.forAddress(uri.host, uri.port)
        .apply {
            if (uri.scheme in SECURE_URL_SCHEMES) {
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
        .apply { config() }
        .build()
}

data class ChannelOpts(
    val inboundMessageSize: Int = 40 * 1024 * 1024, // ~ 20 MB
    val idleTimeout: Pair<Long, TimeUnit> = 5L to TimeUnit.MINUTES,
    val keepAliveTime: Pair<Long, TimeUnit> = 60L to TimeUnit.SECONDS, // ~ 12 pbc block cuts
    val keepAliveTimeout: Pair<Long, TimeUnit> = 20L to TimeUnit.SECONDS,
    val executor: ExecutorService = Executors.newFixedThreadPool(8)
)

/**
 * Netty
 */
open class PbClient(
    override val chainId: String,
    override val channelUri: URI,
    override val gasEstimationMethod: PbGasEstimator,
    opts: ChannelOpts = ChannelOpts(),
    channelConfigLambda: (NettyChannelBuilder) -> Unit = { },
    channel: ManagedChannel = grpcChannel(channelUri, opts, NETTY_CHANNEL, channelConfigLambda),
) : AbstractPbClient<NettyChannelBuilder>(chainId, channelUri, gasEstimationMethod, NETTY_CHANNEL, opts, channel)

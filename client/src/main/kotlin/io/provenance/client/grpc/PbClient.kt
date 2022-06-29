package io.provenance.client.grpc

import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 *
 */
fun nettyPbClient(
    chainId: String,
    channelUri: URI,
    gasEstimationMethod: GasEstimator,
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
    val executor: ExecutorService = Executors.newFixedThreadPool(8),
    val shutdownWait: Duration = 10.seconds
)

/**
 * Netty
 */
open class PbClient(
    override val chainId: String,
    override val channelUri: URI,
    override val gasEstimationMethod: GasEstimator,
    opts: ChannelOpts = ChannelOpts(),
    channelConfigLambda: (NettyChannelBuilder) -> Unit = { },
    channel: ManagedChannel = grpcChannel(channelUri, opts, NETTY_CHANNEL, channelConfigLambda),
) : AbstractPbClient<NettyChannelBuilder>(chainId, channelUri, gasEstimationMethod, NETTY_CHANNEL, opts, channel)

package io.provenance.client.grpc

import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder
import java.net.URI
import java.util.concurrent.TimeUnit


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
        .idleTimeout(opts.idleTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .keepAliveTime(opts.keepAliveTime.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .keepAliveTimeout(opts.keepAliveTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        .apply { config() }
        .build()
}

/**
 * Netty
 */
open class PbClient(
    override val chainId: String,
    override val channelUri: URI,
    override val gasEstimationMethod: GasEstimator,
    opts: ChannelOpts = ChannelOpts(),
    channelConfigLambda: (NettyChannelBuilder) -> Unit = { },
    channel: ManagedChannel = grpcChannel(channelUri, opts, NettyChannelBuilder::forAddress),
) : AbstractPbClient<NettyChannelBuilder>(chainId, channelUri, gasEstimationMethod, NETTY_CHANNEL, opts, channel)

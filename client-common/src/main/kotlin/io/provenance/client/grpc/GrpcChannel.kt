package io.provenance.client.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.netty.NettyChannelBuilder
import java.net.URI
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val SECURE_URL_SCHEMES = listOf("https", "grpcs", "tcp+tls")

/**
 * Options to apply to a [ManagedChannelBuilder] instance when creating a [ManagedChannel].
 *
 * @param inboundMessageSize See [ManagedChannelBuilder.maxInboundMessageSize]
 * @param idleTimeout See [ManagedChannelBuilder.idleTimeout]
 * @param keepAliveTime See [ManagedChannelBuilder.keepAliveTime]
 * @param keepAliveTimeout See [ManagedChannelBuilder.keepAliveTimeout]
 * @param shutdownWait See [ManagedChannel.awaitTermination]
 * @param executor See [ManagedChannelBuilder.executor]
 */
data class ChannelOpts(
    val inboundMessageSize: Int = 40 * 1024 * 1024, // ~ 20 MB
    val idleTimeout: Duration = 5.minutes,
    val keepAliveTime: Duration = 60.seconds, // ~ 12 pbc block cuts
    val keepAliveTimeout: Duration = 20.seconds,
    val executor: Executor = Executors.newFixedThreadPool(8)
)

/**
 * Utility to create a grpc managed channel for us within grpc clients.
 *
 * @param uri The remote host uri to connect to.
 * @param opts The grpc [ChannelOpts] to apply to this channel.
 * @param fromAddress The [ManagedChannelBuilder.forAddress] to use to create the channel builder.
 * @param init Initial configuration to apply after the [ChannelOpts] are applied.
 * @return The newly created [ManagedChannel]
 */
fun grpcChannel(
    uri: URI,
    opts: ChannelOpts = ChannelOpts(),
    channelConfigLambda: (NettyChannelBuilder) -> Unit = { }
): ManagedChannel {
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
        .also { builder -> channelConfigLambda(builder) }
        .build()
}

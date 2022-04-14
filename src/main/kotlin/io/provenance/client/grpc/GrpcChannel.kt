package io.provenance.client.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val SECURE_URL_SCHEMES = listOf("https", "grpcs", "tcp+tls")

/**
 * Options to apply to a [ManagedChannelBuilder] instance when creating a [ManagedChannel].
 *
 * @param inboundMessageSize See [ManagedChannelBuilder.maxInboundMessageSize]
 * @param idleTimeout See [ManagedChannelBuilder.idleTimeout]
 * @param keepAliveTime See [ManagedChannelBuilder.keepAliveTime]
 * @param keepAliveTimeout See [ManagedChannelBuilder.keepAliveTimeout]
 * @param executor See [ManagedChannelBuilder.executor]
 */
data class ChannelOpts(
    val inboundMessageSize: Int = 40 * 1024 * 1024, // ~ 20 MB
    val idleTimeout: Pair<Long, TimeUnit> = 5L to TimeUnit.MINUTES,
    val keepAliveTime: Pair<Long, TimeUnit> = 60L to TimeUnit.SECONDS, // ~ 12 pbc block cuts
    val keepAliveTimeout: Pair<Long, TimeUnit> = 20L to TimeUnit.SECONDS,
    val shutdownWait: Duration = 10.seconds,
    val executor: ExecutorService = Executors.newFixedThreadPool(8)
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
fun <T : ManagedChannelBuilder<T>> grpcChannel(
    uri: URI,
    opts: ChannelOpts = ChannelOpts(),
    fromAddress: (String, Int) -> T,
    init: T.() -> Unit = {}
): ManagedChannel {
    return fromAddress(uri.host, uri.port)
        .apply {
            if (uri.scheme in SECURE_URL_SCHEMES) useTransportSecurity()
            else usePlaintext()
        }
        .executor(opts.executor)
        .maxInboundMessageSize(opts.inboundMessageSize)
        .idleTimeout(opts.idleTimeout.first, opts.idleTimeout.second)
        .keepAliveTime(opts.keepAliveTime.first, opts.keepAliveTime.second)
        .keepAliveTimeout(opts.keepAliveTimeout.first, opts.keepAliveTimeout.second)
        .also(init)
        .build()
}
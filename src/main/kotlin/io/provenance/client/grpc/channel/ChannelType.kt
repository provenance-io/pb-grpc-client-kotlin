package io.provenance.client.grpc.channel

import io.grpc.ManagedChannelBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder

/**
 *
 */
val NETTY_CHANNEL = ChannelType.from(NettyChannelBuilder::forAddress)

/**
 *
 */
interface ChannelType<T : ManagedChannelBuilder<T>> : (String, Int) -> T {
    companion object {
        fun <T : ManagedChannelBuilder<T>> from(block: (String, Int) -> T): (String, Int) -> T = block
    }
}

package io.provenance.client.grpc

import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.provenance.client.grpc.channel.NETTY_CHANNEL
import com.google.protobuf.ByteString
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.tx.v1beta1.TxOuterClass.TxBody
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

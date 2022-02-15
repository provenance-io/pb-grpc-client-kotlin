package io.provenance.client.grpc

import com.google.protobuf.ByteString
import cosmos.auth.v1beta1.Auth
import cosmos.auth.v1beta1.QueryOuterClass
import cosmos.bank.v1beta1.Tx
import cosmos.base.v1beta1.CoinOuterClass
import cosmos.gov.v1beta1.Gov
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.tx.v1beta1.TxOuterClass.TxBody
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.provenance.client.wallet.WalletSigner
import io.provenance.client.wallet.toAny
import io.provenance.client.wallet.toTxBody
import io.provenance.msgfees.v1.CalculateTxFeesRequest
import io.provenance.msgfees.v1.MsgFee
import io.provenance.msgfees.v1.QueryAllMsgFeesRequest
import java.io.Closeable
import java.io.File
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

class PbClient(
    val chainId: String,
    val channelUri: URI,
    opts: ChannelOpts = ChannelOpts(),
    channelConfigLambda: (NettyChannelBuilder) -> Unit = { }
) : Closeable {

    val channel = NettyChannelBuilder.forAddress(channelUri.host, channelUri.port)
        .apply {
            if (channelUri.scheme == "grpcs") {
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

    // msg fee client
    val msgFeeClient = io.provenance.msgfees.v1.QueryGrpc.newBlockingStub(channel)
    val metadataClient = io.provenance.metadata.v1.QueryGrpc.newBlockingStub(channel)
    val mintClient = cosmos.mint.v1beta1.QueryGrpc.newBlockingStub(channel)
    val nameClient = io.provenance.name.v1.QueryGrpc.newBlockingStub(channel)
    val paramsClient = cosmos.params.v1beta1.QueryGrpc.newBlockingStub(channel)
    val slashingClient = cosmos.slashing.v1beta1.QueryGrpc.newBlockingStub(channel)
    val stakingClient = cosmos.staking.v1beta1.QueryGrpc.newBlockingStub(channel)
    val transferClient = ibc.applications.transfer.v1.QueryGrpc.newBlockingStub(channel)
    val upgradeClient = cosmos.upgrade.v1beta1.QueryGrpc.newBlockingStub(channel)
    val wasmClient = cosmwasm.wasm.v1.QueryGrpc.newBlockingStub(channel)

    fun baseRequest(
        txBody: TxBody,
        signers: List<BaseReqSigner>,
        gasAdjustment: Double? = null,
    ): BaseReq =
        signers.map {
            BaseReqSigner(
                signer = it.signer,
                sequenceOffset = it.sequenceOffset,
                account = it.account ?: this.getBaseAccount(it.signer.address())
            )
        }.let {
            BaseReq(
                signers = it,
                body = txBody,
                chainId = chainId,
                gasAdjustment = gasAdjustment
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
            }.let {
                tx.toBuilder().addAllSignatures(it).build()
            }.let { txFinal ->
                val msgFee = msgFeeClient.calculateTxFees(
                    CalculateTxFeesRequest.newBuilder().setTxBytes(txFinal.toByteString()).setGasAdjustment(
                        if (baseReq.gasAdjustment != null) baseReq.gasAdjustment.toFloat()
                        else GasEstimate.DEFAULT_FEE_ADJUSTMENT.toFloat()
                    ).build()
                )
                GasEstimate(
                    msgFee.estimatedGas,
                    baseReq.gasAdjustment,
                    msgFee.totalFeesList
                )
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
    ): ServiceOuterClass.BroadcastTxResponse = baseRequest(
        txBody = txBody,
        signers = signers,
        gasAdjustment = gasAdjustment
    ).let { baseReq -> broadcastTx(baseReq, estimateTx(baseReq), mode) }

    fun getBaseAccount(bech32Address: String): Auth.BaseAccount =
        authClient.account(
            QueryOuterClass.QueryAccountRequest.newBuilder().setAddress(bech32Address).build()
        ).account.run {
            when {
                this.`is`(Auth.BaseAccount::class.java) -> unpack(Auth.BaseAccount::class.java)
                else -> throw IllegalArgumentException("Account type not handled:$typeUrl")
            }
        }

    fun getAcountBalance(bech32Address: String, denom: String): CoinOuterClass.Coin =
        bankClient.balance(
            cosmos.bank.v1beta1.QueryOuterClass.QueryBalanceRequest.newBuilder().setAddress(bech32Address).setDenom(denom).build()
        ).balance

    fun getAllMsgFees(): MutableList<MsgFee>? =
        msgFeeClient.queryAllMsgFees(QueryAllMsgFeesRequest.getDefaultInstance()).msgFeesList

    fun addMsgFeeProposal(walletSigner: WalletSigner, msgType: String): ServiceOuterClass.BroadcastTxResponse {
        val addGovProposal = io.provenance.msgfees.v1.AddMsgFeeProposal.newBuilder().setAdditionalFee(
            CoinOuterClass.Coin.newBuilder()
                .setDenom("gwei").setAmount(2000.toString()).build()
        ).setDescription("Msg fee for Create Marker.")
            .setMsgTypeUrl(msgType).setTitle("Vote for adding fee's to create marker")
            .build().toAny()
        val submitProposal = cosmos.gov.v1beta1.Tx.MsgSubmitProposal.newBuilder().setContent(addGovProposal)
            .setProposer(walletSigner.account.address).addInitialDeposit(
                CoinOuterClass.Coin.newBuilder()
                    .setAmount(15000000000.toString()).setDenom("nhash").build()
            ).build()
        val response = estimateAndBroadcastTx(submitProposal.toAny().toTxBody(), listOf(BaseReqSigner(walletSigner)), gasAdjustment = 1.5)
        return response
    }

    fun voteOnProposal(walletSigner: WalletSigner, proposalId: Long): ServiceOuterClass.BroadcastTxResponse {
        val vote = cosmos.gov.v1beta1.Tx.MsgVote.newBuilder().setProposalId(proposalId).setVoter(walletSigner.account.address)
            .setOption(Gov.VoteOption.VOTE_OPTION_YES).build()
        val response = estimateAndBroadcastTx(vote.toAny().toTxBody(), listOf(BaseReqSigner(walletSigner)), gasAdjustment = 1.5)
        return response
    }

    fun getAllProposalsAndFilter(): Gov.Proposal? {
        return govClient.proposals(cosmos.gov.v1beta1.QueryOuterClass.QueryProposalsRequest.getDefaultInstance())
            .proposalsList.firstOrNull { it.status == Gov.ProposalStatus.PROPOSAL_STATUS_VOTING_PERIOD }
    }

    fun storeWasm(walletSigner: WalletSigner,): ServiceOuterClass.BroadcastTxResponse {
        val wasmContract = ByteString.copyFrom(File("src/main/resources/ats_smart_contract.wasm").readBytes())
        return estimateAndBroadcastTx(
            cosmwasm.wasm.v1.Tx.MsgStoreCode.newBuilder()
                .setSender(walletSigner.address())
                .setWasmByteCode(wasmContract)
                .build().toAny().toTxBody(),
            listOf(BaseReqSigner(walletSigner)), gasAdjustment = 1.5, mode = ServiceOuterClass.BroadcastMode.BROADCAST_MODE_BLOCK
        )
    }
}

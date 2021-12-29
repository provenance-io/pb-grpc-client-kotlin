package io.provenance.client

import com.google.gson.Gson
import cosmos.auth.v1beta1.QueryOuterClass
import cosmos.bank.v1beta1.Tx
import cosmos.base.v1beta1.CoinOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.GasEstimate
import io.provenance.client.grpc.PbClient
import io.provenance.client.wallet.NetworkType
import io.provenance.client.wallet.WalletSigner
import io.provenance.client.wallet.toAny
import io.provenance.client.wallet.toTxBody
import org.junit.Before
import java.io.File
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PbClientTest {

    val pbClient = PbClient(
            chainId = "chain-local",
            channelUri = URI("http://localhost:9090"),
    )
    var mapOfNodeSigners = mutableMapOf<String, WalletSigner>()
    // sample mnemonic, can be anything
    val mnemonic = "tenant radar absurd ostrich music useless broom cup dragon depart annual charge lawsuit aware embark leader hour major venture private near inside daughter cabin" // todo use your own mnemonic

    @Before
    fun before() {
        mapOfNodeSigners = getAllVotingKeys()
        val listOfMsgFees = pbClient.getAllMsgFees()?.filter { it.msgTypeUrl == "/cosmos.bank.v1beta1.MsgSend" || it.msgTypeUrl == "/provenance.marker.v1.MsgAddMarkerRequest"}
        if(listOfMsgFees?.size!! !=2) {
            createGovProposalAndVote(walletSigners = mapOfNodeSigners, "/provenance.marker.v1.MsgAddMarkerRequest")
            createGovProposalAndVote(walletSigners = mapOfNodeSigners, "/cosmos.bank.v1beta1.MsgSend")
        }
    }


    @Test
    fun testClientQuery() {

        pbClient.authClient.accounts(
                QueryOuterClass.QueryAccountsRequest.getDefaultInstance()
        ).also { response ->
            println(response)
            assertTrue(
                    response.accountsCount > 0,
                    "Found zero accounts on blockchain or could not properly connect to localhost chain."
            )
        }
    }

    /**
     * Example of how to submit a transaction to chain using the new estimate method for Msg fees.
     */
    @Test
    fun testClientTxn() {
        val walletSignerToWallet = WalletSigner(NetworkType.TESTNET, mnemonic)
        val wallet = mapOfNodeSigners["node0"]!!

        val balanceHashOriginal = pbClient.getAcountBalance(wallet.address(), "nhash")
        val balanceGweiOriginal = pbClient.getAcountBalance(wallet.address(), "gwei")

        // transfer 10000nhash
        val amount = "10000"
        val txn: TxOuterClass.TxBody = Tx.MsgSend.newBuilder()
                .setFromAddress(wallet.address())
                .setToAddress(walletSignerToWallet.address())
                .addAmount(CoinOuterClass.Coin.newBuilder().setDenom("nhash").setAmount(amount))
                .build()
                .toAny()
                .toTxBody()// todo create your own txn

        val baseRequest = pbClient.baseRequest(
                txBody = txn,
                signers = listOf(BaseReqSigner(wallet)),
                1.5
        )
        val estimate: GasEstimate = pbClient.estimateTx(baseRequest)

        println("estimate is $estimate")
        val estimatedHash = estimate.feeCalculated.firstOrNull { it.denom == "nhash" }
        assertNotNull(estimatedHash, "estimated hash cannot be null")
        val estimatedGwei = estimate.feeCalculated.firstOrNull { it.denom == "gwei" }
        assertNotNull(estimatedGwei, "estimated gwei cannot be null")

        val res = pbClient.estimateAndBroadcastTx(txn, listOf(BaseReqSigner(wallet)), gasAdjustment = 1.5)
        assertTrue(
                res.txResponse.code == 0,
                "Did not succeed."
        )

        // let the block commit
        Thread.sleep(10000)

        val balanceHash = pbClient.getAcountBalance(wallet.account.address, "nhash")
        val balanceGwei = pbClient.getAcountBalance(wallet.account.address, "gwei")

        val gweiConsumed = balanceGweiOriginal.amount.toBigDecimal().subtract(balanceGwei.amount.toBigDecimal())
        val hashConsumed = balanceHashOriginal.amount.toBigDecimal().subtract(balanceHash.amount.toBigDecimal()).subtract(amount.toBigDecimal())
        assertEquals(estimatedHash.amount.toString(), hashConsumed.toString(), "estimate should match actual")
        assertEquals(estimatedGwei.amount.toString(), gweiConsumed.toString(), "estimate should match actual")


    }

    @Test
    fun testClientMultipleTxn() {
        // sample mnemonic
        val walletSignerToWallet = WalletSigner(NetworkType.TESTNET, mnemonic)
        val wallet = mapOfNodeSigners["node0"]!!

        val balanceHashOriginal = pbClient.getAcountBalance(wallet.address(), "nhash")
        val balanceGweiOriginal = pbClient.getAcountBalance(wallet.address(), "gwei")

        // transfer 10000nhash
        val amount = "10000"
        val txn = Tx.MsgSend.newBuilder()
                .setFromAddress(wallet.address())
                .setToAddress(walletSignerToWallet.address())
                .addAmount(CoinOuterClass.Coin.newBuilder().setDenom("nhash").setAmount(amount))
                .build()
                .toAny()

        val txn2 = Tx.MsgSend.newBuilder()
                .setFromAddress(wallet.address())
                .setToAddress(walletSignerToWallet.address())
                .addAmount(CoinOuterClass.Coin.newBuilder().setDenom("nhash").setAmount(amount))
                .build()
                .toAny()

        val baseRequest = pbClient.baseRequest(
                txBody = listOf(txn,txn2).toTxBody(),
                signers = listOf(BaseReqSigner(wallet)),
                1.5
        )
        val estimate: GasEstimate = pbClient.estimateTx(baseRequest)

        println("estimate is $estimate")
        val estimatedHash = estimate.feeCalculated.firstOrNull { it.denom == "nhash" }
        assertNotNull(estimatedHash, "estimated hash cannot be null")
        val estimatedGwei = estimate.feeCalculated.firstOrNull { it.denom == "gwei" }
        assertNotNull(estimatedGwei, "estimated gwei cannot be null")

        val res = pbClient.estimateAndBroadcastTx(listOf(txn,txn2).toTxBody(), listOf(BaseReqSigner(wallet)), gasAdjustment = 1.5)
        assertTrue(
                res.txResponse.code == 0,
                "Did not succeed."
        )

        // let the block commit
        Thread.sleep(10000)

        val balanceHash = pbClient.getAcountBalance(wallet.account.address, "nhash")
        val balanceGwei = pbClient.getAcountBalance(wallet.account.address, "gwei")

        val gweiConsumed = balanceGweiOriginal.amount.toBigDecimal().subtract(balanceGwei.amount.toBigDecimal())
        val hashConsumed = balanceHashOriginal.amount.toBigDecimal().subtract(balanceHash.amount.toBigDecimal()).subtract(amount.toBigDecimal()).subtract(amount.toBigDecimal())
        assertEquals(estimatedHash.amount.toString(), hashConsumed.toString(), "estimate should match actual")
        assertEquals(estimatedGwei.amount.toString(), gweiConsumed.toString(), "estimate should match actual")


    }

    fun getAllVotingKeys(): MutableMap<String, WalletSigner> {
        val mapOfSigners = mutableMapOf<String, WalletSigner>()
        for (i in 0 until 4) {
            val jsonString: String = File("../../provenance/build/node${i}/key_seed.json").readText(Charsets.UTF_8)
            val map = Gson().fromJson(jsonString, mutableMapOf<String, String>().javaClass)
            val walletSigner = WalletSigner(NetworkType.COSMOS_TESTNET, map["secret"]!!)
            println(walletSigner.address())
            mapOfSigners.put("node${i}", walletSigner)
        }
        return mapOfSigners
    }

    // propose governance and vote
    fun createGovProposalAndVote(walletSigners: Map<String, WalletSigner>, msgType:String) {
        assertTrue { pbClient.addMsgFeeProposal(walletSigners["node0"]!!,msgType).txResponse.code == 0 }

        // let the block be committed
        Thread.sleep(10000)

        // vote on proposal
        val govProp = pbClient.getAllProposalsAndFilter()!!
        assertTrue {  pbClient.voteOnProposal(walletSigners["node0"]!!, govProp.proposalId).txResponse.code == 0}
        assertTrue {  pbClient.voteOnProposal(walletSigners["node1"]!!, govProp.proposalId).txResponse.code == 0}
        assertTrue {  pbClient.voteOnProposal(walletSigners["node2"]!!, govProp.proposalId).txResponse.code == 0}
        assertTrue {  pbClient.voteOnProposal(walletSigners["node3"]!!, govProp.proposalId).txResponse.code == 0}
        Thread.sleep(10000)
    }
}


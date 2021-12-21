package io.provenance.client

import cosmos.auth.v1beta1.QueryOuterClass
import cosmos.bank.v1beta1.Tx
import cosmos.base.v1beta1.CoinOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.PbClient
//import cosmos.tx.v1beta1.txBody
import io.provenance.client.wallet.NetworkType
import io.provenance.client.wallet.WalletSigner
import io.provenance.client.wallet.toAny
import io.provenance.client.wallet.toTxBody
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue

class PbClientTest {

    val pbClient = PbClient(
            chainId = "chain-local",
            channelUri = URI("http://localhost:9090"),
    )

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
     * Example of how to submit a transaction to chain.
     *
     * Example only... real values are needed to run this example
     */
    @Test
    fun testClientTxn() {
        val mnemonic = "tenant radar absurd ostrich music useless broom cup dragon depart annual charge lawsuit aware embark leader hour major venture private near inside daughter cabin" // todo use your own mnemonic
        val walletSigner = WalletSigner(NetworkType.TESTNET, mnemonic)
        val txn: TxOuterClass.TxBody = Tx.MsgSend.newBuilder()
                .setFromAddress(walletSigner.account.address)
                .setToAddress("tp13w5897ksgxwzgttxzrenv48tktqyw7fh8d77p6")
                .addAmount(CoinOuterClass.Coin.newBuilder().setDenom("nhash").setAmount("10000"))
                .build()
                .toAny()
                .toTxBody()// todo create your own txn


        val res = pbClient.estimateAndBroadcastTx(txn, listOf(BaseReqSigner(walletSigner)), gasAdjustment = 1.5)
        assertTrue(
                res.txResponse.code == 0,
                "Did not succeed."
        )
    }
}


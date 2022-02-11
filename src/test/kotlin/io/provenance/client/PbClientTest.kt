package io.provenance.client

import cosmos.auth.v1beta1.QueryOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.wallet.NetworkType
import io.provenance.client.wallet.fromMnemonic
import java.net.URI
import kotlin.test.assertTrue

class PbClientTest {

    val pbClient = PbClient(
        chainId = "chain-local",
        channelUri = URI("http://localhost:9090"),
    )

    // @Test
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
    fun testClientTxn() {
        val mnemonic = "your 20 word phrase here" // todo use your own mnemonic
        val walletSigner = fromMnemonic(NetworkType.TESTNET, mnemonic)
        val txn: TxOuterClass.TxBody = TxOuterClass.TxBody.getDefaultInstance() // todo create your own txn

        pbClient.estimateAndBroadcastTx(txn, listOf(BaseReqSigner(walletSigner)))
    }
}

package io.provenance.client.coroutines

import cosmos.auth.v1beta1.QueryOuterClass
import cosmos.bank.v1beta1.Tx
import cosmos.base.v1beta1.coin
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import cosmos.tx.v1beta1.getTxRequest
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.protobuf.extensions.getTx
import io.provenance.client.protobuf.extensions.toAny
import io.provenance.client.wallet.WalletSigner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import tech.figure.hdwallet.bip39.MnemonicWords
import tech.figure.hdwallet.hrp.Hrp
import tech.figure.hdwallet.wallet.Wallet
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Ignore
class PbClientTest {
    val pbClient = PbCoroutinesClient(
        chainId = "chain-local",
        channelUri = URI("http://localhost:9090"),
        gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION // GasEstimationMethod.COSMOS_SIMULATION used only if pbc version is 1.7 or lower
    )

    @Test
    fun testClientQuery() = runTest {
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

    @Test
    fun `Simulated BROADCAST_MODE_BLOCK works`() = runTest {
        val signer = testWalletSigner()
        val result = pbClient.estimateAndBroadcastTx(
            TxOuterClass.TxBody.newBuilder().addMessages(
                Tx.MsgSend.newBuilder()
                .setFromAddress(signer.address())
                .setToAddress(signer.address())
                .addAmount(coin {
                    this.amount = "1"
                    this.denom = "nhash"
                }).build()
                .toAny()).build(),
            listOf(BaseReqSigner(signer)),
            mode = ServiceOuterClass.BroadcastMode.BROADCAST_MODE_BLOCK,
        )

        assert(result.txResponse.height > 0) { "Transaction response had no height" }
        assert(result.txResponse.code == 0) { "Transaction not successful" }
    }

    @Test
    fun `Simulated BROADCAST_MODE_SYNC works`() = runTest {
        val signer = testWalletSigner()
        var preHash: String? = null
        val result = pbClient.estimateAndBroadcastTx(
            TxOuterClass.TxBody.newBuilder().addMessages(
                Tx.MsgSend.newBuilder()
                    .setFromAddress(signer.address())
                    .setToAddress(signer.address())
                    .addAmount(coin {
                        this.amount = "1"
                        this.denom = "nhash"
                    }).build()
                    .toAny()).build(),
            listOf(BaseReqSigner(signer)),
            mode = ServiceOuterClass.BroadcastMode.BROADCAST_MODE_SYNC,
            txHashHandler = { preHash = it }
        )

        assert(preHash != null) { "preHash not received" }
        assert(result.txResponse.txhash == preHash) { "Transaction response had no txHash" }
        var tx: ServiceOuterClass.GetTxResponse? = null
        for (i in 1..6) {
            try {
                tx = pbClient.cosmosService.getTx(getTxRequest { hash = preHash!! })
            } catch (e: StatusException) {
                if (e.message?.contains("not found") == true) {
                    Thread.sleep(1000)
                    continue
                }
                throw e
            }
        }
        assert(tx != null) { "Transaction $preHash not fetched" }
        assert(tx!!.txResponse.height > 0) { "Transaction response had no height" }
        assert(tx.txResponse.code == 0) { "Transaction not successful" }
    }

    private fun testWalletSigner(): WalletSigner =
        MnemonicWords.generate().let {
            Wallet.fromMnemonic(
                hrp = Hrp.ProvenanceBlockchain.testnet,
                passphrase = "",
                mnemonicWords = it,
                testnet = true
            )
        }.let { WalletSigner(it["m/44'/1'/0'/0'/0'"]) }
}

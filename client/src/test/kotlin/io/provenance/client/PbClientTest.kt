package io.provenance.client

import cosmos.auth.v1beta1.Auth
import cosmos.auth.v1beta1.QueryOuterClass
import cosmos.bank.v1beta1.Tx.MsgSend
import cosmos.base.v1beta1.coin
import cosmos.bank.v1beta1.msgSend
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.ServiceOuterClass.GetTxResponse
import cosmos.tx.v1beta1.TxOuterClass
import io.grpc.StatusRuntimeException
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.GasEstimationMethod
import io.provenance.client.grpc.PbClient
import io.provenance.client.protobuf.extensions.getTx
import io.provenance.client.protobuf.extensions.toAny
import io.provenance.client.wallet.WalletSigner
import io.provenance.marker.v1.msgTransferRequest
import tech.figure.hdwallet.bip39.MnemonicWords
import tech.figure.hdwallet.hrp.Hrp
import tech.figure.hdwallet.wallet.Wallet
import java.net.URI
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// @OptIn(TestnetFeaturePreview::class)
class PbClientTest {
    private val pbClient = PbClient(
        chainId = "chain-local",
        channelUri = URI("http://localhost:9090"),
        gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION // GasEstimationMethod.COSMOS_SIMULATION used only if pbc version is 1.7 or lower
    )

    @Test
    @Ignore("requires grpc connection")
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
    @Test
    fun `Build BaseReq`() {
        val txBody = TxOuterClass.TxBody.getDefaultInstance()
        val account = Auth.BaseAccount.getDefaultInstance()
        val baseReqSigner = BaseReqSigner(
            signer = testWalletSigner(),
            account = account,
        )
        val gasAdjustment = 2.0
        val feeGranter = "granter"
        val feePayer = "payer"

        val result = pbClient.baseRequest(txBody, listOf(baseReqSigner), gasAdjustment, feeGranter, feePayer)
        assertEquals(txBody, result.body)
        assertEquals(gasAdjustment, result.gasAdjustment)
        assertEquals(feeGranter, result.feeGranter)
        assertEquals(feePayer, result.feePayer)
        assertEquals(pbClient.chainId, result.chainId)
        with(result.signers.single()) {
            assertEquals(baseReqSigner.signer, this.signer)
            assertEquals(baseReqSigner.sequenceOffset, 0)
            assertEquals(baseReqSigner.account, this.account)
        }
    }

    @Test
    @Ignore("requires grpc connection")
    fun `Simulated BROADCAST_MODE_BLOCK works`() {
        val signer = testWalletSigner()
        val result = pbClient.estimateAndBroadcastTx(
            TxOuterClass.TxBody.newBuilder().addMessages(MsgSend.newBuilder()
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
    @Ignore("requires grpc connection")
    fun `Simulated BROADCAST_MODE_SYNC works`() {
        val signer = testWalletSigner()
        var preHash: String? = null
        val result = pbClient.estimateAndBroadcastTx(
            TxOuterClass.TxBody.newBuilder().addMessages(MsgSend.newBuilder()
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
        var tx: GetTxResponse? = null
        for (i in 1..6) {
            try {
                tx = pbClient.cosmosService.getTx(preHash!!)
            } catch (e: StatusRuntimeException) {
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

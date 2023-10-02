package io.provenance.client

import cosmos.auth.v1beta1.Auth
import cosmos.auth.v1beta1.QueryOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.GasEstimationMethod
import io.provenance.client.grpc.PbClient
import io.provenance.client.wallet.WalletSigner
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

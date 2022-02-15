package io.provenance.client.grpc

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import cosmos.auth.v1beta1.Auth
import cosmos.crypto.secp256k1.Keys
import cosmos.tx.signing.v1beta1.Signing.SignMode
import cosmos.tx.v1beta1.TxOuterClass.AuthInfo
import cosmos.tx.v1beta1.TxOuterClass.Fee
import cosmos.tx.v1beta1.TxOuterClass.ModeInfo
import cosmos.tx.v1beta1.TxOuterClass.ModeInfo.Single
import cosmos.tx.v1beta1.TxOuterClass.SignDoc
import cosmos.tx.v1beta1.TxOuterClass.SignerInfo
import cosmos.tx.v1beta1.TxOuterClass.TxBody

interface Signer {
    fun address(): String
    fun pubKey(): Keys.PubKey
    fun sign(data: ByteArray): ByteArray
}

data class BaseReqSigner(
    val signer: Signer,
    val sequenceOffset: Int = 0,
    val account: Auth.BaseAccount? = null
)

data class BaseReq(
    val signers: List<BaseReqSigner>,
    val body: TxBody,
    val chainId: String,
    val gasAdjustment: Float? = null,
    val feeGranter: String? = null
) {

    fun buildAuthInfo(gasEstimate: GasEstimate = GasEstimate(0)): AuthInfo =
        AuthInfo.newBuilder()
            .setFee(
                Fee.newBuilder()
                    .addAllAmount(gasEstimate.feesCalculated)
                    .setGasLimit(gasEstimate.limit)
                    .also {
                        if (feeGranter != null) {
                            it.granter = feeGranter
                        }
                    }
            )
            .addAllSignerInfos(
                signers.map {
                    SignerInfo.newBuilder()
                        .setPublicKey(Any.pack(it.signer.pubKey(), ""))
                        .setModeInfo(
                            ModeInfo.newBuilder()
                                .setSingle(Single.newBuilder().setModeValue(SignMode.SIGN_MODE_DIRECT_VALUE))
                        )
                        .setSequence(it.account!!.sequence + it.sequenceOffset)
                        .build()
                }
            )
            .build()

    fun buildSignDocBytesList(authInfoBytes: ByteString, bodyBytes: ByteString): List<ByteArray> =
        signers.map {
            SignDoc.newBuilder()
                .setBodyBytes(bodyBytes)
                .setAuthInfoBytes(authInfoBytes)
                .setChainId(chainId)
                .setAccountNumber(it.account!!.accountNumber)
                .build()
                .toByteArray()
        }
}

package io.provenance.client.wallet

import com.google.protobuf.ByteString
import cosmos.crypto.secp256k1.Keys
import io.provenance.client.grpc.Signer
import io.provenance.hdwallet.bip39.MnemonicWords
import io.provenance.hdwallet.common.hashing.sha256
import io.provenance.hdwallet.hrp.Hrp
import io.provenance.hdwallet.signer.BCECSigner
import io.provenance.hdwallet.wallet.Account
import io.provenance.hdwallet.wallet.Wallet

enum class NetworkType(
    /**
     * The hrp (Human Readable Prefix) of the network address
     */
    val prefix: String,
    /**
     * The HD wallet path
     */
    val path: String
) {
    TESTNET(prefix = Hrp.ProvenanceBlockchain.testnet, path = "m/44'/1'/0'/0/0'"),
    MAINNET(prefix = Hrp.ProvenanceBlockchain.mainnet, path = "m/505'/1'/0'/0/0")
}

class WalletSigner(prefix: String, path: String, mnemonic: String, passphrase: String = "") : Signer {

    constructor(networkType: NetworkType, mnemonic: String, passphrase: String = "") :
        this(networkType.prefix, networkType.path, mnemonic, passphrase)

    val wallet = Wallet.fromMnemonic(prefix, passphrase.toCharArray(), MnemonicWords.of(mnemonic))

    val account: Account = wallet[path]

    override fun address(): String = account.address.value

    override fun pubKey(): Keys.PubKey =
        Keys.PubKey
            .newBuilder()
            .setKey(ByteString.copyFrom(account.keyPair.publicKey.compressed()))
            .build()

    override fun sign(data: ByteArray): ByteArray = BCECSigner()
        .sign(account.keyPair.privateKey, data.sha256())
        .encodeAsBTC()
        .toByteArray()
}

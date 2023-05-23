package io.provenance.client.wallet

import com.google.protobuf.ByteString
import cosmos.crypto.secp256k1.Keys
import io.provenance.client.grpc.Signer
import tech.figure.hdwallet.bip39.MnemonicWords
import tech.figure.hdwallet.common.hashing.sha256
import tech.figure.hdwallet.signer.BCECSigner
import tech.figure.hdwallet.wallet.Account
import tech.figure.hdwallet.wallet.Wallet

/**
 * Create a [WalletSigner] from a BIP-39 mnemonic.
 *
 * @param prefix The human-readable prefix to use when generating network addresses.
 * @param path The key derivation path to use.
 * @param mnemonic The mnemonic phrase list used to generate the key used by the wallet for signing.
 * @param passphrase An optional passphrase to use when generating the key used by the wallet for signing. Default is `""`.
 * @param isMainNet If true, mainnet keys will be generated, otherwise testnet keys will be generated. The default is false.
 * @return [WalletSigner]
 */
fun fromMnemonic(
    prefix: String,
    path: String,
    mnemonic: String,
    passphrase: String = "",
    isMainNet: Boolean = false,
): WalletSigner {
    val wallet: Wallet = Wallet.fromMnemonic(
        hrp = prefix,
        passphrase = passphrase,
        mnemonicWords = MnemonicWords.of(mnemonic),
        testnet = !isMainNet
    )
    return WalletSigner(wallet[path])
}

/**
 * Create a [WalletSigner] from a BIP-39 mnemonic.
 *
 * @param networkType Use a defined [NetworkType] to specify the path and prefix when generating the signing key.
 * @param mnemonic The mnemonic phrase list used to generate the key used by the wallet for signing.
 * @param passphrase An optional passphrase to use when generating the key used by the wallet for signing. Default is `""`.
 * @param isMainNet If true, mainnet keys will be generated, otherwise testnet keys will be generated. The default is false.
 * @return [WalletSigner]
 */
fun fromMnemonic(
    networkType: NetworkType,
    mnemonic: String,
    passphrase: String = "",
    isMainNet: Boolean = false,
): WalletSigner =
    fromMnemonic(
        prefix = networkType.prefix,
        path = networkType.path,
        mnemonic = mnemonic,
        passphrase = passphrase,
        isMainNet = isMainNet
    )

/**
 * Create a [WalletSigner] from an base58-encoded BIP-32 extended private key.
 *
 * @param prefix The human-readable prefix to use when generating network addresses.
 * @param key The extended private key bytes.
 * @return [WalletSigner]
 */
fun fromBase58EncodedKey(prefix: String, key: String) =
    WalletSigner(Account.fromBip32(hrp = prefix, bip32 = key))

/**
 * Create a [WalletSigner] from an base58-encoded BIP-32 extended private key.
 *
 * @param networkType Use a defined [NetworkType] to specify the path and prefix when generating the signing key.
 * @param encodedKey The extended private key bytes.
 * @return [WalletSigner]
 */
fun fromBase58EncodedKey(networkType: NetworkType, key: String) =
    WalletSigner(Account.fromBip32(hrp = networkType.prefix, bip32 = key))

/**
 * The signing wallet implementation
 *
 * @property account The account used for transacting on the blockhain.
 */
class WalletSigner(private val account: Account) : Signer {

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

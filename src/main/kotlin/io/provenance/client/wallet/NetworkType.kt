package io.provenance.client.wallet

import io.provenance.hdwallet.hrp.Hrp

enum class NetworkType(
    /**
     * The hrp (Human Readable Prefix) of the network address.
     */
    val prefix: String,
    /**
     * The HD wallet path.
     */
    val path: String,
) {
    TESTNET(prefix = Hrp.ProvenanceBlockchain.testnet, path = "m/44'/1'/0'/0/0'"),
    MAINNET(prefix = Hrp.ProvenanceBlockchain.mainnet, path = "m/505'/1'/0'/0/0")
}

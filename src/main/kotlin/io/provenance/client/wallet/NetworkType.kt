package io.provenance.client.wallet

import io.provenance.hdwallet.hrp.Hrp

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
    TESTNET(prefix = Hrp.ProvenanceBlockchain.testnet, "m/44'/1'/0'/0/0'"),
    COSMOS_TESTNET(prefix = Hrp.ProvenanceBlockchain.testnet, "m/44'/1'/0'/0/0"), // cause we are now creating validators on localnet with that path ffs
    MAINNET(prefix = Hrp.ProvenanceBlockchain.mainnet, "m/505'/1'/0'/0/0")
}

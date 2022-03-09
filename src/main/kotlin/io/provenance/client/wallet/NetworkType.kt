package io.provenance.client.wallet

open class NetworkType(
    /**
     * The hrp (Human Readable Prefix) of the network address
     */
    val prefix: String,
    /**
     * The HD wallet path
     */
    val path: String
)
package io.provenance.client.common.gas

import cosmos.base.v1beta1.CoinOuterClass

/**
 * The gas estimate implementation
 *
 * @param limit The estimated gas limit.
 * @param feesCalculated A list of [CoinOuterClass.Coin].
 */
data class GasEstimate(
    val limit: Long,
    val feesCalculated: List<CoinOuterClass.Coin> = emptyList(),
    val msgFees: List<CoinOuterClass.Coin> = emptyList()
) {
    companion object {
        const val DEFAULT_FEE_ADJUSTMENT = 1.25

        // TODO - Remove once mainnet.version > 1.8
        @Deprecated("do not use")
        const val DEFAULT_GAS_PRICE = 1905.00
    }
}

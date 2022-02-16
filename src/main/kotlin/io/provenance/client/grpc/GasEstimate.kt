package io.provenance.client.grpc

import cosmos.base.v1beta1.CoinOuterClass
import kotlin.math.ceil

/**
 * The gas estimate implementation
 *
 * @param limit The estimated gas limit.
 * @param feesCalculated A list of [CoinOuterClass.Coin].
 */
data class GasEstimate(val limit: Long, val feesCalculated: List<CoinOuterClass.Coin> = emptyList()) {

    companion object {
        const val DEFAULT_FEE_ADJUSTMENT = 1.25
        const val DEFAULT_GAS_PRICE = 1905.00
    }
}

/**
 * The gas estimate implementation
 *
 * @param estimate The estimated gas limit.
 * @param adjustment An adjustment to apply to the gas estimate.
 */
fun fromSimulation(estimate: Long, adjustment: Double): GasEstimate {
    val limit = ceil(estimate * adjustment).toLong()
    val feeAmount = ceil(limit * GasEstimate.DEFAULT_GAS_PRICE).toLong()
    return listOf(
        CoinOuterClass.Coin.newBuilder()
            .setAmount(feeAmount.toString())
            .setDenom("nhash")
            .build()
    ).let { fees -> GasEstimate(limit, fees) }
}

/**
 * A set of flags used to determine how gas should be estimated for a transaction
 *
 * - `COSMOS_SIMULATION` - A flag for cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
 * - `MSG_FEE_CALCULATION` - A flag for message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
 */
enum class GasEstimationMethod {
    /**
     * A flag for cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
     */
    COSMOS_SIMULATION,

    /**
     * A flag for message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
     */
    MSG_FEE_CALCULATION
}

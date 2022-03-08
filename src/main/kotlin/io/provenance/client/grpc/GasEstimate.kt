package io.provenance.client.grpc

import cosmos.base.v1beta1.CoinOuterClass
import cosmos.tx.v1beta1.TxOuterClass
// import io.provenance.client.TestnetFeaturePreview
import io.provenance.client.gas.estimators.MsgFeeCalculationGasEstimator
import io.provenance.client.gas.estimators.cosmosSimulationGasEstimator
import io.provenance.client.gas.estimators.floatingGasPriceGasEstimator
import io.provenance.client.gas.prices.GasPrices
import io.provenance.client.internal.extensions.toCoin

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
        internal const val DEFAULT_GAS_PRICE = 1905.00
    }
}

/**
 * [GasEstimator] is an alias to standardize how gas estimations are made.
 * @param tx The transaction to estimate for.
 * @param adj The gas adjustment being applied.
 * @return Gas estimates.
 */
typealias GasEstimator = (tx: TxOuterClass.Tx, adjustment: Double) -> GasEstimate

/**
 * Wrapper alias for estimation methods to allow scoping of pbClient GRPC methods into the estimation.
 */
typealias PbGasEstimator = PbClient.() -> GasEstimator

/**
 * A set of flags used to specify how gas should be estimated
 *
 * - `COSMOS_SIMULATION` - A flag to specify cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
 * - `MSG_FEE_CALCULATION` - A flag to specify message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
 */
object GasEstimationMethod {
    /**
     * A flag for cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
     */
    val COSMOS_SIMULATION: PbGasEstimator = cosmosSimulationGasEstimator { GasEstimate.DEFAULT_GAS_PRICE.toCoin("nhash") }

    /**
     * A flag for message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
     */
    // @TestnetFeaturePreview
    val MSG_FEE_CALCULATION: PbGasEstimator = MsgFeeCalculationGasEstimator
}

/**
 * Add a floating adjustment based on current market rates to estimations.
 *
 * Example:
 *
 * ```kotlin
 * val priceGetter = UrlGasPrices("https://oracle.my.domain/gas-price").cached()
 *
 * val estimator =
 *   if (provenance.network.version < 1.8) GasEstimationMethod.COSMOS_SIMULATION
 *   else GasEstimationMethod.MSG_FEE_CALCULATION
 *
 * val pbClient = PbClient(
 *     chainId = "pio-testnet-1",
 *     channelUri = URI("grpcs://grpc.test.provenance.io"),
 *     gasEstimationMethod = floatingGasPrices(estimator, priceGetter)
 * )
 * ```
 *
 * @param delegate The underlying estimation calculation methods to use.
 * @param gasPrices The current gas price supplier.
 * @return [PbGasEstimator]
 */
// @TestnetFeaturePreview
fun floatingGasPrices(delegate: PbGasEstimator, gasPrices: GasPrices): PbGasEstimator =
    floatingGasPriceGasEstimator(delegate, gasPrices)

package io.provenance.client.grpc

import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.gas.estimators.MsgFeeCalculationGasEstimator
import io.provenance.client.gas.estimators.cosmosSimulationGasEstimator
import io.provenance.client.gas.estimators.floatingGasPriceGasEstimator
import io.provenance.client.common.extensions.toCoin
import io.provenance.client.common.gas.prices.GasPrices

/**
 * [GasEstimator] is an alias to standardize how gas estimations are made.
 * @param tx The transaction to estimate for.
 * @param adj The gas adjustment being applied.
 * @return Gas estimates.
 */
typealias GasEstimator = AbstractPbClient<*>.(tx: TxOuterClass.Tx, adjustment: Double) -> GasEstimate

/**
 *
 */
typealias PbGasEstimator = AbstractPbClient<*>.() -> GasEstimator

/**
 *
 */
fun gasEstimator(block: AbstractPbClient<*>.(tx: TxOuterClass.Tx, adjustment: Double) -> GasEstimate): GasEstimator {
    return { tx: TxOuterClass.Tx, adjustment: Double -> block(tx, adjustment) }
}

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
    val COSMOS_SIMULATION: GasEstimator = cosmosSimulationGasEstimator { GasEstimate.DEFAULT_GAS_PRICE.toCoin("nhash") }

    /**
     * A flag for message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
     */
    // @TestnetFeaturePreview
    val MSG_FEE_CALCULATION: GasEstimator = MsgFeeCalculationGasEstimator
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
 * @return [GasEstimator]
 */
fun floatingGasPrices(delegate: GasEstimator, gasPrices: GasPrices): GasEstimator =
    floatingGasPriceGasEstimator(delegate, gasPrices)

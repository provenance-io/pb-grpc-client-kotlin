package io.provenance.client.coroutines.gas.estimators

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.client.common.extensions.toCoin
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.coroutines.gas.prices.GasPrices
import io.provenance.client.coroutines.gasEstimator
import kotlin.math.ceil

/**
 * Cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
 * TODO - Remove once mainnet.version > 1.8
 */
internal fun cosmosSimulationGasEstimator(gasPrices: GasPrices) = gasEstimator { tx, adjustment ->
    val price = gasPrices()
    val sim = cosmosService.simulate(
        ServiceOuterClass.SimulateRequest.newBuilder()
            .setTxBytes(tx.toByteString())
            .build()
    )
    val limit = ceil(sim.gasInfo.gasUsed * adjustment).toLong()
    val feeAmount = ceil(limit * price.amount.toDouble()).toLong()
    GasEstimate(limit, listOf(feeAmount.toCoin(price.denom)))
}

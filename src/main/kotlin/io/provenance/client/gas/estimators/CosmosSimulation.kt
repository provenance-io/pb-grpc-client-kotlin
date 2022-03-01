package io.provenance.client.gas.estimators

import cosmos.tx.v1beta1.ServiceOuterClass
import io.provenance.client.gas.prices.GasPrices
import io.provenance.client.grpc.GasEstimate
import io.provenance.client.grpc.PbGasEstimator
import io.provenance.client.internal.extensions.toCoin
import kotlin.math.ceil

/**
 * Cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
 * TODO - Remove once mainnet.version > 1.8
 */
internal fun cosmosSimulationGasEstimator(gasPrices: GasPrices): PbGasEstimator = {
    { tx, adjustment ->
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
}

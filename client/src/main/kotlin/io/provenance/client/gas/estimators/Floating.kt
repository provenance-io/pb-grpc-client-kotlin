package io.provenance.client.gas.estimators

import io.provenance.client.common.extensions.toCoin
import io.provenance.client.common.gas.prices.GasPrices
import io.provenance.client.grpc.GasEstimator
import io.provenance.client.grpc.gasEstimator

internal fun floatingGasPriceGasEstimator(delegate: GasEstimator, floatingGasPrice: GasPrices) = gasEstimator { tx, adjustment ->
    val (price, denom) = floatingGasPrice().let { it.amount.toDouble() to it.denom }
    require(denom == "nhash") { "only nhash is supported for fees" }

    // Original estimate
    val estimate = delegate(tx, adjustment)

    // Re-calculate price based on floating gas price instead of original.
    val newBaseFee = listOf((estimate.limit * price).toCoin(denom))

    // Add in any message based fees to new base fee.
    estimate.copy(feesCalculated = newBaseFee + estimate.msgFees)
}

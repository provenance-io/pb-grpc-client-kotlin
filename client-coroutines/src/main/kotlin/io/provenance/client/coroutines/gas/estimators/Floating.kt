package io.provenance.client.coroutines.gas.estimators

import io.provenance.client.common.extensions.toCoin
import io.provenance.client.coroutines.GasEstimator
import io.provenance.client.coroutines.gas.prices.GasPrices
import io.provenance.client.coroutines.gasEstimator

internal fun floatingGasPriceGasEstimator(delegate: GasEstimator, floatingGasPrice: GasPrices) = gasEstimator { tx, adjustment ->
    val (price, denom) = floatingGasPrice().let { it.amount.toBigDecimal() to it.denom }
    require(denom == "nhash") { "only nhash is supported for fees" }

    // Original estimate
    val estimate = delegate(tx, adjustment)

    // Re-calculate price based on floating gas price instead of original.
    val newBaseFee = listOf(price.times(estimate.limit.toBigDecimal()).toCoin(denom))

    // Add in any message based fees to new base fee.
    estimate.copy(feesCalculated = newBaseFee + estimate.msgFees)
}

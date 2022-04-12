package io.provenance.client.coroutines.gas.estimators

import io.provenance.client.coroutines.PbGasEstimator
import io.provenance.client.coroutines.gas.prices.GasPrices
import io.provenance.client.internal.extensions.toCoin

// @TestnetFeaturePreview
internal fun floatingGasPriceGasEstimator(delegate: PbGasEstimator, floatingGasPrice: GasPrices): PbGasEstimator = {
    { tx, adjustment ->
        val (price, denom) = floatingGasPrice().let { it.amount.toDouble() to it.denom }
        require(denom == "nhash") { "only nhash is supported for fees" }

        // Original estimate
        val estimate = delegate(this)(tx, adjustment)

        // Re-calculate price based on floating gas price instead of original.
        val newBaseFee = listOf((estimate.limit * price).toCoin(denom))

        // Add in any message based fees to new base fee.
        estimate.copy(feesCalculated = newBaseFee + estimate.msgFees)
    }
}

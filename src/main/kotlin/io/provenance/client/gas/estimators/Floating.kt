package io.provenance.client.gas.estimators

import io.provenance.client.TestnetFeaturePreview
import io.provenance.client.gas.prices.GasPrices
import io.provenance.client.grpc.PbGasEstimator
import io.provenance.client.internal.extensions.times

@TestnetFeaturePreview
internal fun floatingGasPriceGasEstimator(delegate: PbGasEstimator, floatingGasPrice: GasPrices): PbGasEstimator = {
	{ tx, adjustment ->
		val price = floatingGasPrice()
		require(price.denom == "nhash") { "only nhash is supported for fees" }

		// Original estimate
		val estimate = delegate(this)(tx, adjustment)
		// Adjust up or down based on floating factor.
		val factor = price.amount.toDouble() / nodeGasPrice.value
		// Updated values
		estimate.copy(feesCalculated = estimate.feesCalculated * factor)
	}
}

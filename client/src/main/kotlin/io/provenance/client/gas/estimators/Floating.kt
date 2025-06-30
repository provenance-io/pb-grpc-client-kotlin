package io.provenance.client.gas.estimators

import io.provenance.client.common.gas.prices.GasPrices
import io.provenance.client.grpc.GasEstimator
import io.provenance.client.grpc.gasEstimator

@Suppress("UNUSED_PARAMETER")
@Deprecated(message= "As of 2.6.x and higher pb-grpc-client defaults to Provenance 1.25 flat fees and this function ignores the GasEstimator delegate fallback.", replaceWith = ReplaceWith("MSG_FEE_CALCULATION"), level = DeprecationLevel.WARNING)
internal fun floatingGasPriceGasEstimator(delegate: GasEstimator, floatingGasPrice: GasPrices) = gasEstimator { tx, adjustment ->
    val (_, denom) = floatingGasPrice().let { it.amount.toBigDecimal() to it.denom }
    require(denom == "nhash") { "only nhash is supported for fees" }

    // As of 2.6.x and higher default to Provenance 1.25 flat fee and ignore delegate
    MsgFeeCalculationGasEstimator(tx, adjustment)
}

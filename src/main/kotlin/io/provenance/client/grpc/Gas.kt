package io.provenance.client.grpc

import cosmos.base.v1beta1.CoinOuterClass
import kotlin.math.ceil

private fun Double.roundUp(): Long = ceil(this).toLong()

data class GasEstimate(val estimate: Long, val feeAdjustment: Double? = DEFAULT_FEE_ADJUSTMENT, val feeCalculated: List<CoinOuterClass.Coin> = emptyList()) {

    companion object {
        const val DEFAULT_FEE_ADJUSTMENT = 1.25
    }

    val limit = estimate
}

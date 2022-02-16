package io.provenance.client.grpc

import cosmos.base.v1beta1.CoinOuterClass

data class GasEstimate(val limit: Long, val feesCalculated: List<CoinOuterClass.Coin> = emptyList()) {

    companion object {
        const val DEFAULT_FEE_ADJUSTMENT = 1.25
    }
}

package io.provenance.client.common.gas

import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.client.common.extensions.toCoin

/**
 *
 */
data class GasPrice(val gasPrice: Long, val gasPriceDenom: String) {
    fun toCoin(): CoinOuterClass.Coin = "$gasPrice$gasPriceDenom".toCoin()
}

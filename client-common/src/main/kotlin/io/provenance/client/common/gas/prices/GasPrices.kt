package io.provenance.client.common.gas.prices

import cosmos.base.v1beta1.CoinOuterClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

typealias GasPrices = () -> CoinOuterClass.Coin

fun gasPrices(block: () -> CoinOuterClass.Coin) = block

fun GasPrices.cached(ttl: Duration = 1.hours): GasPrices = cachedGasPrice(this, ttl)

fun GasPrices.withFallback(gasPrices: GasPrices): GasPrices {
    val parent = this
    return { runCatching { parent.invoke() }.getOrDefault(gasPrices()) }
}
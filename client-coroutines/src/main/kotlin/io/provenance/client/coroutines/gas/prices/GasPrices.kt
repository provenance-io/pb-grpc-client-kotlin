package io.provenance.client.coroutines.gas.prices

import cosmos.base.v1beta1.CoinOuterClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

typealias GasPrices = suspend () -> CoinOuterClass.Coin

fun GasPrices.cached(ttl: Duration = 1.hours): GasPrices = CachedGasPrice(this, ttl)

class CachedGasPrice(delegate: GasPrices, ttl: Duration) : GasPrices {
    override suspend fun invoke(): CoinOuterClass.Coin {
        TODO("Not yet implemented")
    }
}

fun GasPrices.withFallbackPrice(gasPrice: CoinOuterClass.Coin): GasPrices {
    val parent = this
    return { runCatching { parent.invoke() }.getOrDefault(gasPrice) }
}

fun gasPrices(block: suspend () -> CoinOuterClass.Coin): GasPrices = { block() }

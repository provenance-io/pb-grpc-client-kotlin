package io.provenance.client.coroutines.gas.prices

import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.client.coroutines.caching.cached
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

typealias GasPrices = () -> CoinOuterClass.Coin

fun GasPrices.cached(ttl: Duration = 1.hours): GasPrices = CachedGasPrice(this, ttl)

class CachedGasPrice(val delegate: GasPrices, val ttl: Duration) : GasPrices {
    override fun invoke(): CoinOuterClass.Coin {
        var price: GasPrices
        val cached = cached(ttl) { delegate() }
        price = gasPrices { cached.get() }
        return price.invoke()
    }
}

fun GasPrices.withFallbackPrice(gasPrice: CoinOuterClass.Coin): GasPrices {
    val parent = this
    return { runCatching { parent.invoke() }.getOrDefault(gasPrice) }
}

fun gasPrices(coroutineContext: CoroutineContext = EmptyCoroutineContext, block: suspend () -> CoinOuterClass.Coin): GasPrices = {
    runBlocking(coroutineContext) {
        block()
    }
}

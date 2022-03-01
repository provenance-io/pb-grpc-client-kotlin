package io.provenance.client.gas.prices

import cosmos.base.v1beta1.CoinOuterClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

typealias GasPrices = () -> CoinOuterClass.Coin

fun GasPrices.cached(ttl: Duration = 1.hours): GasPrices = CachedGasPrice(this, ttl)

fun GasPrices.withFallbackPrice(gasPrice: CoinOuterClass.Coin): GasPrices {
    val parent = this

    return object : GasPrices {
        override fun invoke(): CoinOuterClass.Coin {
            return try {
                parent.invoke()
            } catch (e: Throwable) {
                gasPrice
            }
        }
    }
}

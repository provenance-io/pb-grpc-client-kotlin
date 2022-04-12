package io.provenance.client.common.gas.prices

import io.provenance.caching.cached
import kotlin.time.Duration

/**
 * Cache the gas prices for a determined period of time
 */
fun cachedGasPrice(gasPrices: GasPrices, duration: Duration) : GasPrices {
    val cached = cached(duration) { gasPrices() }
    return gasPrices { cached.get() }
}

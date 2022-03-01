package io.provenance.client.gas.prices

import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.client.internal.extensions.toCoin
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

/**
 * Cache the gas prices for a determined period of time
 */
class CachedGasPrice(private val gasPrices: GasPrices, private val duration: Duration) : GasPrices {
	private val lastFetch = AtomicReference(OffsetDateTime.MIN)
	private val cachedValue = AtomicReference("0nhash".toCoin())

	override fun invoke(): CoinOuterClass.Coin {
		if (OffsetDateTime.now().isAfter(lastFetch.get().plus(duration.inWholeMilliseconds, ChronoUnit.MILLIS))) {
			cachedValue.set(gasPrices())
			lastFetch.set(OffsetDateTime.now())
		}
		return cachedValue.get()
	}
}
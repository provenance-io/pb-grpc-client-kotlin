package io.provenance.caching

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

fun <T> cached(ttl: Duration, block: () -> T): Cached<T> {
    return object : BaseCachedAsync<T>(ttl) {
        override fun fetch(): T = block()
    }
}

interface Cached<T> {
    fun fetch(): T
    fun get(): T
}

abstract class BaseCachedAsync<T>(private val ttl: Duration) : Cached<T> {
    private val lastFetch = AtomicReference(OffsetDateTime.MIN)
    private val cachedValue = AtomicReference<T>()

    override fun get(): T {
        if (OffsetDateTime.now().isAfter(lastFetch.get().plus(ttl.inWholeMilliseconds, ChronoUnit.MILLIS))) {
            cachedValue.set(fetch())
            lastFetch.set(OffsetDateTime.now())
        }
        return cachedValue.get()
    }
}

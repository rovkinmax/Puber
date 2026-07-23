package com.kino.puber.core.collections

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import java.util.LinkedHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface TypedTtlCache<K : Any, V : Any> {
    suspend fun getOrPut(key: K, defaultValue: suspend () -> V): V
    fun put(key: K, value: V, ttl: Duration? = null)
    fun remove(key: K)
    fun clear()

    companion object {
        val DefaultTtl: Duration = 20.seconds
    }
}

class TypedTtlCacheImpl<K : Any, V : Any>(
    private val defaultTtl: Duration = TypedTtlCache.DefaultTtl,
    private val maximumSize: Int = 256,
    maximumConcurrentLoads: Int = 16,
    private val nowNanos: () -> Long = System::nanoTime,
) : TypedTtlCache<K, V> {

    private data class Entry<V>(
        val value: V,
        val expiresAtNanos: Long,
    )

    private class Flight<V> {
        val outcome = CompletableDeferred<FlightOutcome<V>>()
    }

    private sealed interface FlightOutcome<out V> {
        data class Success<V>(val value: V) : FlightOutcome<V>
        data class Failure(val throwable: Throwable) : FlightOutcome<Nothing>
        data object LeaderCancelled : FlightOutcome<Nothing>
        data object Invalidated : FlightOutcome<Nothing>
    }

    private sealed interface Lookup<out V> {
        data class Value<V>(val value: V) : Lookup<V>
        data class Wait<V>(val flight: Flight<V>) : Lookup<V>
        data class Load<V>(val flight: Flight<V>) : Lookup<V>
    }

    private val lock = Any()
    private val completed = LinkedHashMap<K, Entry<V>>(
        INITIAL_CAPACITY,
        LOAD_FACTOR,
        true,
    )
    private val flights = HashMap<K, Flight<V>>()
    private val loadPermits = Semaphore(maximumConcurrentLoads)

    init {
        require(maximumSize > 0) { "maximumSize must be positive" }
        require(maximumConcurrentLoads > 0) { "maximumConcurrentLoads must be positive" }
    }

    override suspend fun getOrPut(key: K, defaultValue: suspend () -> V): V {
        while (true) {
            when (val lookup = lookupOrElect(key)) {
                is Lookup.Value -> return lookup.value
                is Lookup.Load -> load(key, lookup.flight, defaultValue)?.let { return it }
                is Lookup.Wait -> when (val outcome = lookup.flight.outcome.await()) {
                    is FlightOutcome.Success -> return outcome.value
                    is FlightOutcome.Failure -> throw outcome.throwable
                    FlightOutcome.LeaderCancelled,
                    FlightOutcome.Invalidated -> Unit
                }
            }
        }
    }

    override fun put(key: K, value: V, ttl: Duration?) {
        val invalidatedFlight = synchronized(lock) {
            val flight = flights.remove(key)
            putUnsafe(key, value, ttl ?: defaultTtl)
            flight
        }
        invalidatedFlight?.outcome?.complete(FlightOutcome.Invalidated)
    }

    override fun remove(key: K) {
        val invalidatedFlight = synchronized(lock) {
            completed.remove(key)
            flights.remove(key)
        }
        invalidatedFlight?.outcome?.complete(FlightOutcome.Invalidated)
    }

    override fun clear() {
        val invalidatedFlights = synchronized(lock) {
            completed.clear()
            val activeFlights = flights.values.toList()
            flights.clear()
            activeFlights
        }
        invalidatedFlights.forEach { flight ->
            flight.outcome.complete(FlightOutcome.Invalidated)
        }
    }

    private suspend fun lookupOrElect(key: K): Lookup<V> {
        synchronized(lock) {
            lookupUnsafe(key)?.let { return it }
        }

        loadPermits.acquire()
        val lookup = synchronized(lock) {
            lookupUnsafe(key) ?: Flight<V>().let { flight ->
                flights[key] = flight
                Lookup.Load(flight)
            }
        }
        if (lookup !is Lookup.Load) {
            loadPermits.release()
        }
        return lookup
    }

    private fun lookupUnsafe(key: K): Lookup<V>? {
        getValidValueUnsafe(key)?.let { value ->
            return Lookup.Value(value)
        }

        flights[key]?.let { flight ->
            return Lookup.Wait(flight)
        }
        return null
    }

    private suspend fun load(
        key: K,
        flight: Flight<V>,
        defaultValue: suspend () -> V,
    ): V? {
        try {
            val value = defaultValue()
            val accepted = synchronized(lock) {
                if (flights[key] === flight) {
                    flights.remove(key)
                    putUnsafe(key, value, defaultTtl)
                    true
                } else {
                    false
                }
            }
            return if (accepted) {
                flight.outcome.complete(FlightOutcome.Success(value))
                value
            } else {
                flight.outcome.complete(FlightOutcome.Invalidated)
                null
            }
        } catch (cancellation: CancellationException) {
            detachFlight(key, flight)
            flight.outcome.complete(FlightOutcome.LeaderCancelled)
            throw cancellation
        } catch (throwable: Throwable) {
            return if (detachFlight(key, flight)) {
                flight.outcome.complete(FlightOutcome.Failure(throwable))
                throw throwable
            } else {
                flight.outcome.complete(FlightOutcome.Invalidated)
                null
            }
        } finally {
            loadPermits.release()
        }
    }

    private fun detachFlight(key: K, flight: Flight<V>): Boolean {
        return synchronized(lock) {
            if (flights[key] === flight) {
                flights.remove(key)
                true
            } else {
                false
            }
        }
    }

    private fun getValidValueUnsafe(key: K): V? {
        val entry = completed[key] ?: return null
        if (!isExpired(entry)) {
            return entry.value
        }
        completed.remove(key)
        return null
    }

    private fun putUnsafe(key: K, value: V, ttl: Duration) {
        completed[key] = Entry(
            value = value,
            expiresAtNanos = expirationTime(ttl),
        )
        evictExpiredAndOverflowUnsafe()
    }

    private fun expirationTime(ttl: Duration): Long {
        if (ttl.isInfinite()) return Long.MAX_VALUE
        val now = nowNanos()
        val ttlNanos = ttl.inWholeNanoseconds.coerceAtLeast(0L)
        return if (ttlNanos >= Long.MAX_VALUE - now) Long.MAX_VALUE else now + ttlNanos
    }

    private fun isExpired(entry: Entry<V>): Boolean {
        return nowNanos() >= entry.expiresAtNanos
    }

    private fun evictExpiredAndOverflowUnsafe() {
        val iterator = completed.entries.iterator()
        while (iterator.hasNext()) {
            if (isExpired(iterator.next().value)) {
                iterator.remove()
            }
        }
        while (completed.size > maximumSize) {
            completed.entries.iterator().run {
                next()
                remove()
            }
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}

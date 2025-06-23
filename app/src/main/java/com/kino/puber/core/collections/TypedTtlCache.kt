package com.kino.puber.core.collections

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    cleanupInterval: Duration = 5.seconds
) : TypedTtlCache<K, V> {

    private val cache = ConcurrentHashMap<K, V>()
    private val expirationTimes = ConcurrentHashMap<K, Long>()
    private val mutexMap = ConcurrentHashMap<K, Mutex>()

    private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor()

    init {
        cleanupExecutor.scheduleWithFixedDelay(
            ::cleanupExpiredEntries,
            cleanupInterval.inWholeMilliseconds,
            cleanupInterval.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )
    }

    private fun getMutexForKey(key: K): Mutex {
        return mutexMap.computeIfAbsent(key) { Mutex() }
    }

    private fun isExpired(expirationTime: Long): Boolean {
        return System.currentTimeMillis() > expirationTime
    }

    private fun getValidValueUnsafe(key: K): V? {
        val value = cache[key]
        val expiration = expirationTimes[key]
        return when {
            value == null -> null
            expiration == null -> value
            !isExpired(expiration) -> value
            else -> {
                remove(key)
                null
            }
        }
    }

    override fun put(key: K, value: V, ttl: Duration?) {
        val localTtl = ttl ?: defaultTtl
        val expirationTime = System.currentTimeMillis() + localTtl.inWholeMilliseconds
        cache[key] = value
        expirationTimes[key] = expirationTime
    }

    override fun remove(key: K) {
        cache.remove(key)
        expirationTimes.remove(key)
        mutexMap.remove(key)
    }

    override fun clear() {
        cache.clear()
        expirationTimes.clear()
        mutexMap.clear()
    }

    override suspend fun getOrPut(key: K, defaultValue: suspend () -> V): V {
        getValidValueUnsafe(key)?.let { return it }

        val mutex = getMutexForKey(key)
        return mutex.withLock {
            getValidValueUnsafe(key)?.let { return it }
            val newValue = defaultValue()
            put(key, newValue)
            newValue
        }
    }

    private fun cleanupExpiredEntries() {
        expirationTimes.entries
            .filter { (_, expirationTime) -> isExpired(expirationTime) }
            .forEach { (key, _) -> remove(key) }
    }
}
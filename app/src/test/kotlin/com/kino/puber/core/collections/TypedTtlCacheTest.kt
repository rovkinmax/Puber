package com.kino.puber.core.collections

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.nanoseconds

class TypedTtlCacheTest {

    @Test
    fun concurrentCallers_shareSingleLoad() = runTest {
        val cache = TypedTtlCacheImpl<String, String>()
        val releaseLoad = CompletableDeferred<Unit>()
        var loadCount = 0

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                loadCount += 1
                releaseLoad.await()
                "value"
            }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                loadCount += 1
                "other"
            }
        }

        assertEquals(1, loadCount)
        releaseLoad.complete(Unit)
        assertEquals("value", first.await())
        assertEquals("value", second.await())
    }

    @Test
    fun cancelledLeader_rethrowsOnlyToLeaderAndWaitersRetrySingleFlight() = runTest {
        val cache = TypedTtlCacheImpl<String, String>(maximumConcurrentLoads = 1)
        val leaderStarted = CompletableDeferred<Unit>()
        var retryLoads = 0

        val leader = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                leaderStarted.complete(Unit)
                awaitCancellation()
            }
        }
        leaderStarted.await()
        val firstWaiter = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                retryLoads += 1
                "fresh"
            }
        }
        val secondWaiter = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                retryLoads += 1
                "other"
            }
        }

        leader.cancelAndJoin()
        runCurrent()

        assertTrue(leader.isCancelled)
        assertEquals("fresh", firstWaiter.await())
        assertEquals("fresh", secondWaiter.await())
        assertEquals(1, retryLoads)
        assertEquals("fresh", cache.getOrPut("key") { "unexpected" })
    }

    @Test
    fun cancelledWaiter_doesNotCancelSharedLoad() = runTest {
        val cache = TypedTtlCacheImpl<String, String>()
        val releaseLoad = CompletableDeferred<Unit>()
        val leader = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                releaseLoad.await()
                "value"
            }
        }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") { "unexpected" }
        }

        waiter.cancelAndJoin()
        releaseLoad.complete(Unit)

        assertTrue(waiter.isCancelled)
        assertEquals("value", leader.await())
        assertEquals("value", cache.getOrPut("key") { "unexpected" })
    }

    @Test
    fun removeDuringLoad_detachesOldFlightAndReloadsImmediately() = runTest {
        val cache = TypedTtlCacheImpl<String, String>()
        val releaseStale = CompletableDeferred<Unit>()
        var freshLoads = 0
        val staleLeader = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                releaseStale.await()
                "stale"
            }
        }
        val staleWaiter = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                freshLoads += 1
                "fresh"
            }
        }

        cache.remove("key")
        runCurrent()

        assertEquals("fresh", staleWaiter.await())
        assertFalse(staleLeader.isCompleted)
        assertEquals("fresh", cache.getOrPut("key") { "unexpected" })
        releaseStale.complete(Unit)
        assertEquals("fresh", staleLeader.await())
        assertEquals(1, freshLoads)
    }

    @Test
    fun clearDuringLoad_detachesOldFlightsAndReloadsImmediately() = runTest {
        val cache = TypedTtlCacheImpl<String, String>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val staleFirst = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("first") {
                releaseFirst.await()
                "stale-first"
            }
        }
        val staleSecond = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("second") {
                releaseSecond.await()
                "stale-second"
            }
        }

        cache.clear()
        val freshFirst = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("first") { "fresh-first" }
        }
        val freshSecond = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("second") { "fresh-second" }
        }
        runCurrent()

        assertEquals("fresh-first", freshFirst.await())
        assertEquals("fresh-second", freshSecond.await())
        assertFalse(staleFirst.isCompleted)
        assertFalse(staleSecond.isCompleted)
        releaseFirst.complete(Unit)
        releaseSecond.complete(Unit)
        assertEquals("fresh-first", staleFirst.await())
        assertEquals("fresh-second", staleSecond.await())
        assertEquals("fresh-first", cache.getOrPut("first") { "unexpected" })
        assertEquals("fresh-second", cache.getOrPut("second") { "unexpected" })
    }

    @Test
    fun explicitPutDuringLoad_detachesOldFlightAndIsVisibleImmediately() = runTest {
        val cache = TypedTtlCacheImpl<String, String>()
        val releaseLoad = CompletableDeferred<Unit>()
        val staleLeader = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                releaseLoad.await()
                "stale"
            }
        }
        val staleWaiter = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") { "unexpected" }
        }

        cache.put("key", "manual")

        assertEquals("manual", cache.getOrPut("key") { "unexpected" })
        assertEquals("manual", staleWaiter.await())
        assertFalse(staleLeader.isCompleted)
        releaseLoad.complete(Unit)
        assertEquals("manual", staleLeader.await())
        assertEquals("manual", cache.getOrPut("key") { "unexpected" })
    }

    @Test
    fun expiredPutDuringLoad_detachesOldFlightAndReloadsImmediately() = runTest {
        val cache = TypedTtlCacheImpl<String, String>()
        val releaseStale = CompletableDeferred<Unit>()
        val stale = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") {
                releaseStale.await()
                "stale"
            }
        }

        cache.put("key", "expired", ttl = ZERO)
        val fresh = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("key") { "fresh" }
        }
        runCurrent()

        assertEquals("fresh", fresh.await())
        assertFalse(stale.isCompleted)
        releaseStale.complete(Unit)
        assertEquals("fresh", stale.await())
        assertEquals("fresh", cache.getOrPut("key") { "unexpected" })
    }

    @Test
    fun leastRecentlyUsedEntry_isEvictedAtMaximumSize() = runTest {
        val cache = TypedTtlCacheImpl<String, String>(maximumSize = 2)
        cache.put("first", "one")
        cache.put("second", "two")

        assertEquals("one", cache.getOrPut("first") { "unexpected" })
        cache.put("third", "three")

        assertEquals("one", cache.getOrPut("first") { "unexpected" })
        assertEquals("three", cache.getOrPut("third") { "unexpected" })
        assertEquals("reloaded", cache.getOrPut("second") { "reloaded" })
    }

    @Test
    fun expiredEntry_isReloaded() = runTest {
        var now = 0L
        val cache = TypedTtlCacheImpl<String, String>(nowNanos = { now })
        cache.put("key", "old", ttl = 10.nanoseconds)

        now = 9L
        assertEquals("old", cache.getOrPut("key") { "unexpected" })
        now = 10L
        assertEquals("fresh", cache.getOrPut("key") { "fresh" })
    }

    @Test
    fun concurrentLoads_areBoundedBySemaphore() = runTest {
        val cache = TypedTtlCacheImpl<String, String>(maximumConcurrentLoads = 1)
        val releaseFirst = CompletableDeferred<Unit>()
        var secondStarted = false
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("first") {
                releaseFirst.await()
                "one"
            }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            cache.getOrPut("second") {
                secondStarted = true
                "two"
            }
        }

        runCurrent()
        assertFalse(secondStarted)
        releaseFirst.complete(Unit)

        assertEquals("one", first.await())
        assertEquals("two", second.await())
        assertTrue(secondStarted)
    }

    @Test
    fun failedLoad_releasesSemaphorePermit() = runTest {
        val cache = TypedTtlCacheImpl<String, String>(maximumConcurrentLoads = 1)
        val failure = IllegalStateException("failed")
        val failed = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                cache.getOrPut("failed") { throw failure }
            }
        }

        assertEquals(failure, failed.await().exceptionOrNull())
        assertEquals("value", cache.getOrPut("next") { "value" })
    }
}

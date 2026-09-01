package com.kino.puber.ui.feature.player.vm

import android.media.MediaCodecList
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.playertestfixtures.FixtureId
import com.kino.puber.playertestfixtures.PlayerTestFixtures
import com.kino.puber.playertestfixtures.network.LoopbackNetworkJournal
import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.ResponsePlan
import com.kino.puber.profile.PlayerTestControl
import com.kino.puber.ui.feature.player.PlayerInstrumentationTestCase
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.BufferPreset
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
internal class PlaybackControllerResourcesTest : PlayerInstrumentationTestCase() {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var server: PlayerTestControl
    private lateinit var cacheDirectory: File
    private lateinit var cache: SimpleCache
    private lateinit var controller: PlaybackController
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var playerView: PlayerView
    private var responseGate: CountDownLatch? = null

    @Before
    fun setUp() {
        LoopbackNetworkJournal(context).clear()
        server = PlayerTestControl()
        server.start()

        cacheDirectory = context.cacheDir.resolve("player-resources-${UUID.randomUUID()}")
        cache = SimpleCache(
            cacheDirectory,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
        controller = PlaybackController(
            context = context,
            okHttpClient = okhttp3.OkHttpClient(),
            mediaCache = cache,
            playerPreferencesRepository = PlayerPreferencesRepository(context),
        )
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        attachPlayerView()
    }

    @After
    fun tearDown() {
        responseGate?.countDown()
        if (::scenario.isInitialized) {
            scenario.onActivity {
                playerView.player = null
                controller.release()
            }
            scenario.close()
        }
        if (::cache.isInitialized) {
            cache.release()
        }
        if (::server.isInitialized) {
            try {
                server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
                server.assertNoUnknownRequests(MAX_DIAGNOSTIC_REQUESTS)
            } finally {
                server.close()
            }
        }
        assertTrue(
            "Owned cache directory was not cleaned: $cacheDirectory",
            !cacheDirectory.exists() || cacheDirectory.deleteRecursively(),
        )
        assertTrue(
            "Unexpected network egress: ${LoopbackNetworkJournal(context).snapshot()}",
            LoopbackNetworkJournal(context).snapshot().isEmpty(),
        )
    }

    @Test
    fun progressiveReplay_readsCommittedCacheAfterUpstreamRouteIsRemoved() = run {
        lateinit var url: String
        lateinit var first: PlayerProbe
        lateinit var replay: PlayerProbe

        step("Play the progressive fixture through to committed cache") {
            server.reset(progressiveRoutes())
            url = loopbackUrl("/media/progressive.mp4")
            first = prepare(url)
            awaitReady(first)
            await(first.ended, "initial playback ended", first)
            val cachedBytes = cache.getCachedBytes(url, 0L, C.LENGTH_UNSET.toLong())
            assertTrue("Expected committed media bytes, got $cachedBytes", cachedBytes > 0L)
            releaseController(first)
        }

        step("Replay from cache after removing the upstream route") {
            server.reset(emptyList())
            replay = prepare(url)
            awaitReady(replay)
            runOnActivity {
                controller.pause()
                controller.seekTo(CACHED_REPLAY_SEEK_POSITION_MS)
            }
            awaitCondition("cached replay seek crosses the position fence", replay) {
                playbackState() == Player.STATE_READY &&
                    !isPlaying() &&
                    currentPosition() in CACHED_REPLAY_SEEK_POSITION_MS..
                    CACHED_REPLAY_SEEK_FENCE_END_MS
            }
        }

        step("Verify the cached seek is error-free and makes no upstream request") {
            assertTrue(
                "Cached replay seek should not request a removed upstream route: " +
                    server.requestJournal.entries,
                server.requestJournal.entries.isEmpty(),
            )
            assertNull("Cached replay seek reported a playback error", replay.error.get())
            releaseController(replay)
            assertResourcesQuiescent("cached replay seek", replay)
        }
    }

    @Test
    fun emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario() = run {
        val url = loopbackUrl("/media/progressive.mp4")
        lateinit var corrupt: PlayerProbe
        lateinit var clean: PlayerProbe

        step("Load an empty cache from the loopback upstream") {
            server.reset(progressiveRoutes())
            val miss = prepare(url)
            awaitReady(miss)
            assertTrue(
                "Empty cache should use loopback upstream",
                server.requestJournal.entries.any { it.path == "/media/progressive.mp4" },
            )
            releaseController(miss)
        }

        step("Handle a corrupt owned cache entry with a controlled outcome") {
            cache.removeResource(url)
            writeCorruptCacheEntry(url)
            server.reset(progressiveRoutes())
            corrupt = prepare(url)
            awaitReadyOrError(corrupt)
            assertTrue(
                "Corrupt cache must produce a controlled recovery or error: ${diagnostics(corrupt)}",
                corrupt.ready.count == 0L || corrupt.error.get() != null,
            )
            releaseController(corrupt)
        }

        step("Clean the corrupt entry and prove the next playback is not poisoned") {
            cache.removeResource(url)
            assertTrue(cache.getCachedSpans(url).isEmpty())
            server.reset(progressiveRoutes())
            clean = prepare(url)
            awaitReady(clean)
            assertNull(clean.error.get())
            assertTrue(
                "Clean scenario should load after corrupt entry cleanup",
                server.requestJournal.entries.any { it.path == "/media/progressive.mp4" },
            )
            releaseController(clean)
            assertResourcesQuiescent("clean post-corruption scenario", clean)
        }
    }

    @Test
    fun lifecycle_detachReattach_backgroundForegroundAndRecreation_keepPlayerUsable() = run {
        lateinit var probe: PlayerProbe

        step("Prepare progressive playback for lifecycle transitions") {
            server.reset(progressiveRoutes())
            probe = prepare(loopbackUrl("/media/progressive.mp4"))
            awaitReady(probe)
        }

        step("Detach and reattach the PlayerView without losing READY") {
            runOnActivity { playerView.player = null }
            assertNull(playerRead { playerView.player })
            runOnActivity { playerView.player = controller.player }
            assertNotNull(playerRead { playerView.player })
            awaitCondition("reattached player remains ready", probe) {
                playbackState() == Player.STATE_READY
            }
        }

        step("Background and foreground the host while retaining the player") {
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            attachPlayerView()
            assertNotNull(playerRead { playerView.player })
            awaitCondition("foreground player remains ready", probe) {
                playbackState() == Player.STATE_READY
            }
        }

        step("Recreate the activity and restore the PlayerView attachment") {
            scenario.recreate()
            attachPlayerView()
            assertNotNull(playerRead { playerView.player })
            awaitCondition("recreated activity receives the player", probe) {
                playbackState() == Player.STATE_READY
            }
            assertTrue("Player diagnostics were unexpectedly empty", diagnostics(probe).isNotBlank())
        }

        step("Release recreated playback with no retained resources") {
            releaseController(probe)
            assertResourcesQuiescent("activity recreation", probe)
        }
    }

    @Test
    fun progressiveDecode_reportsMandatoryCapabilitiesDecodersCountersAndAnalytics() = run {
        lateinit var probe: PlayerProbe

        step("Require H.264 and AAC decoder capabilities on the emulator") {
            val capabilities = CodecCapabilityReport.capture()
            assertTrue(
                "H.264 decoder is mandatory; ${capabilities.boundedSummary()}",
                capabilities.h264Decoders.isNotEmpty(),
            )
            assertTrue(
                "AAC decoder is mandatory; ${capabilities.boundedSummary()}",
                capabilities.aacDecoders.isNotEmpty(),
            )
        }

        step("Decode the progressive fixture and collect real decoder activity") {
            server.reset(progressiveRoutes())
            probe = prepare(loopbackUrl("/media/progressive.mp4"))
            awaitReady(probe)
            await(probe.firstFrame, "first frame", probe)
            awaitCondition("H.264/AAC decoder diagnostics", probe) {
                val snapshot = decoderSnapshot()
                snapshot.videoMimeType == MimeTypes.VIDEO_H264 &&
                    snapshot.audioMimeType == MimeTypes.AUDIO_AAC &&
                    snapshot.videoDecoderInitCount > 0 &&
                    snapshot.audioDecoderInitCount > 0 &&
                    snapshot.videoQueuedInputBuffers > 0 &&
                    snapshot.audioQueuedInputBuffers > 0 &&
                    snapshot.renderedVideoBuffers > 0 &&
                    probe.analytics.videoDecoderName.get() != null &&
                    probe.analytics.audioDecoderName.get() != null
            }
        }

        step("Verify formats, decoder counters, and bounded analytics") {
            val snapshot = decoderSnapshot()
            assertEquals(MimeTypes.VIDEO_H264, snapshot.videoMimeType)
            assertEquals(MimeTypes.AUDIO_AAC, snapshot.audioMimeType)
            assertTrue(
                "Video decoder counters were empty: $snapshot",
                snapshot.videoQueuedInputBuffers > 0,
            )
            assertTrue(
                "Audio decoder counters were empty: $snapshot",
                snapshot.audioQueuedInputBuffers > 0,
            )
            assertTrue("No video frame was rendered: $snapshot", snapshot.renderedVideoBuffers > 0)
            assertTrue(
                "Unexpected analytics load errors: ${probe.analytics.boundedEvents()}",
                probe.analytics.loadErrors.isEmpty(),
            )
            assertTrue(
                "Unexpected analytics player errors: ${probe.analytics.boundedEvents()}",
                probe.analytics.playerErrors.isEmpty(),
            )
        }

        step("Release decoded playback with no resource ownership retained") {
            releaseController(probe)
            assertResourcesQuiescent("mandatory codec diagnostics", probe)
        }
    }

    @Test
    fun releaseDuringBlockedLoad_closesRequestsAndDetachesReleasedView() = run {
        val gate = CountDownLatch(1)
        responseGate = gate
        val url = loopbackUrl("/media/blocked.mp4")
        lateinit var probe: PlayerProbe

        step("Start a progressive request blocked by the fixture gate") {
            server.reset(
                listOf(
                    server.route(
                        id = "blocked",
                        path = "/media/blocked.mp4",
                        response = HermeticTestServer.delayed(
                            gate = gate,
                            body = PlayerTestFixtures.readBytes(FixtureId.ProgressiveMp4, context),
                            contentType = "video/mp4",
                        ),
                    ),
                ),
            )
            probe = prepare(url)
            awaitCondition("blocked request starts", probe) {
                server.activeRequestCount > 0
            }
        }

        step("Release during the blocked load and detach the PlayerView") {
            releaseController(probe, awaitRequests = false)
            gate.countDown()
        }

        step("Drain the request without stale callbacks or retained resources") {
            assertResourcesQuiescent("release during blocked load", probe)
            assertTrue(
                "Stale callback reported an error: ${probe.error.get()}",
                probe.error.get() == null,
            )
        }
    }

    @Test
    fun boundedPreparePlaySeekReleaseLoop_leavesNoActiveRequestsOrOpenSpans() = run {
        val url = loopbackUrl("/media/progressive.mp4")
        repeat(STRESS_ITERATIONS) { iteration ->
            lateinit var probe: PlayerProbe

            step("Prepare stress iteration ${iteration + 1}") {
                server.reset(progressiveRoutes())
                probe = prepare(url)
                awaitReady(probe)
            }

            step("Play and seek stress iteration ${iteration + 1}") {
                runOnActivity {
                    controller.pause()
                    controller.seekTo(350L)
                    controller.play()
                }
                awaitCondition("stress iteration $iteration advances", probe) {
                    currentPosition() >= 300L
                }
                assertNull(probe.error.get())
            }

            step("Release stress iteration ${iteration + 1} without leaked resources") {
                releaseController(probe)
                assertResourcesQuiescent("stress iteration $iteration", probe)
            }
        }
    }

    private fun attachPlayerView() {
        scenario.onActivity { activity ->
            playerView = PlayerView(activity).apply {
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                player = controller.player
            }
            activity.setContentView(playerView)
        }
    }

    private fun prepare(url: String): PlayerProbe {
        val probe = PlayerProbe()
        scenario.onActivity {
            controller.prepare(
                streamUrl = url,
                subtitles = null,
                startPosition = 0L,
                bufferPreset = BufferPreset.SMALL,
                fastDns = false,
            )
            val player = checkNotNull(controller.player)
            player.addListener(probe)
            player.addAnalyticsListener(probe.analytics)
            controller.setCallback(probe.callback)
            playerView.player = player
        }
        return probe
    }

    private fun releaseController(
        probe: PlayerProbe,
        awaitRequests: Boolean = true,
    ) {
        runOnActivity {
            playerView.player = null
            controller.release()
            probe.callback.markReleased()
        }
        assertNull(playerRead { controller.player })
        assertNull(playerRead { playerView.player })
        assertFalse(playerRead { playerView.keepScreenOn })
        if (awaitRequests) {
            server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
            drainPlayerThread()
            probe.callback.assertNoCallbacksAfterRelease()
        }
    }

    private fun writeCorruptCacheEntry(url: String) {
        val corruptBytes = "not-a-media-container".toByteArray()
        val hole = cache.startReadWrite(url, 0L, corruptBytes.size.toLong())
        try {
            val file = cache.startFile(url, 0L, corruptBytes.size.toLong())
            file.outputStream().use { it.write(corruptBytes) }
            cache.commitFile(file, corruptBytes.size.toLong())
        } finally {
            cache.releaseHoleSpan(hole)
        }
        assertTrue(cache.getCachedBytes(url, 0L, corruptBytes.size.toLong()) > 0L)
    }

    private fun progressiveRoutes(): List<HermeticRoute> =
        listOf(
            server.route(
                id = "progressive",
                path = "/media/progressive.mp4",
                response = ResponsePlan.Range(
                    body = PlayerTestFixtures.readBytes(FixtureId.ProgressiveMp4, context),
                    contentType = "video/mp4",
                ),
            ),
        )

    private fun awaitReady(probe: PlayerProbe) {
        await(probe.ready, "READY", probe)
        assertNull("Unexpected playback error: ${diagnostics(probe)}", probe.error.get())
    }

    private fun awaitReadyOrError(probe: PlayerProbe) {
        await(probe.outcome, "corrupt-cache outcome", probe)
    }

    private fun await(
        latch: CountDownLatch,
        label: String,
        probe: PlayerProbe,
    ) {
        assertTrue(
            "$label timed out; ${diagnostics(probe)}",
            latch.await(PLAYER_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
    }

    private fun awaitCondition(
        label: String,
        probe: PlayerProbe,
        condition: () -> Boolean,
    ) {
        val reached = CountDownLatch(1)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PLAYER_TIMEOUT_SECONDS)
        lateinit var check: Runnable
        check = Runnable {
            if (condition()) {
                reached.countDown()
            } else if (System.nanoTime() < deadline) {
                handler.postDelayed(check, CONDITION_POLL_MS)
            } else {
                reached.countDown()
            }
        }
        handler.post(check)
        assertTrue(
            "$label timed out; ${diagnostics(probe)}",
            reached.await(PLAYER_TIMEOUT_SECONDS + 1L, TimeUnit.SECONDS),
        )
        handler.removeCallbacks(check)
        assertTrue("$label was not reached; ${diagnostics(probe)}", condition())
    }

    private fun assertResourcesQuiescent(
        label: String,
        probe: PlayerProbe,
    ) {
        server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
        drainPlayerThread()
        probe.callback.assertNoCallbacksAfterRelease()
        assertEquals("$label left active requests", 0, server.activeRequestCount)
        assertCacheOwnershipReleased(label)
        drainPlayerThread()
        probe.callback.assertNoCallbacksAfterRelease()
        assertNull("$label retained a controller player", playerRead { controller.player })
        assertNull("$label retained a PlayerView player", playerRead { playerView.player })
        assertFalse("$label retained keep-screen-on ownership", playerRead { playerView.keepScreenOn })
        assertEquals(
            "$label recorded unexpected egress",
            emptyList<String>(),
            LoopbackNetworkJournal(context).snapshot(),
        )
    }

    private fun assertCacheOwnershipReleased(label: String) {
        cache.getKeys().toList().forEach { key ->
            val lockProbe = awaitCacheWriteLockRelease(key)
            assertNotNull("$label retained a cache write lock for $key", lockProbe)
            checkNotNull(lockProbe).let { span ->
                assertTrue("$label returned a non-hole lock probe for $key: $span", span.isHoleSpan)
                cache.releaseHoleSpan(span)
            }
            cache.removeResource(key)
            assertTrue("$label recreated cache spans for $key", cache.getCachedSpans(key).isEmpty())
        }
    }

    private fun awaitCacheWriteLockRelease(key: String): androidx.media3.datasource.cache.CacheSpan? {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CACHE_CLEANUP_TIMEOUT_SECONDS)
        do {
            cache.removeResource(key)
            if (cache.getCachedSpans(key).isEmpty()) {
                cache.startReadWriteNonBlocking(key, 0L, C.LENGTH_UNSET.toLong())?.let { return it }
            }
        } while (
            System.nanoTime() < deadline &&
            CountDownLatch(1).await(CACHE_CLEANUP_POLL_MS, TimeUnit.MILLISECONDS).not()
        )
        return null
    }

    private fun drainPlayerThread() {
        runOnActivity { /* ActivityScenario is a synchronous main-thread fence. */ }
    }

    private fun diagnostics(probe: PlayerProbe): String {
        val value = AtomicReference<String>()
        scenario.onActivity {
            val player = controller.player
            val debug = controller.getDebugInfo()
            val requestTail = server.requestJournal.entries.takeLast(MAX_DIAGNOSTIC_REQUESTS)
                .map { "${it.method} ${it.path} ${it.range.orEmpty()} ${it.outcome}" }
            val decoder = decoderSnapshot(player)
            value.set(
                "state=${player?.playbackState},isPlaying=${player?.isPlaying}," +
                    "position=${player?.currentPosition},formats=" +
                    "${debug?.videoResolution}/${debug?.videoCodec}/${debug?.audioCodec}," +
                    "decoders=${probe.analytics.decoderSummary()}," +
                    "decoderCounters=$decoder," +
                    "buffered=${debug?.bufferedDuration},error=${probe.error.get()}," +
                    "analytics=${probe.analytics.boundedEvents()}," +
                    "capabilities=${CodecCapabilityReport.capture().boundedSummary()}," +
                    "requests=$requestTail",
            )
        }
        return checkNotNull(value.get())
    }

    private fun decoderSnapshot(): DecoderSnapshot = playerRead {
        decoderSnapshot(controller.player)
    }

    private fun decoderSnapshot(player: androidx.media3.exoplayer.ExoPlayer?): DecoderSnapshot {
        val videoCounters = player?.videoDecoderCounters?.apply { ensureUpdated() }
        val audioCounters = player?.audioDecoderCounters?.apply { ensureUpdated() }
        return DecoderSnapshot(
            videoMimeType = player?.videoFormat?.sampleMimeType,
            audioMimeType = player?.audioFormat?.sampleMimeType,
            videoDecoderInitCount = videoCounters?.decoderInitCount ?: 0,
            audioDecoderInitCount = audioCounters?.decoderInitCount ?: 0,
            videoQueuedInputBuffers = videoCounters?.queuedInputBufferCount ?: 0,
            audioQueuedInputBuffers = audioCounters?.queuedInputBufferCount ?: 0,
            renderedVideoBuffers = videoCounters?.renderedOutputBufferCount ?: 0,
            droppedVideoBuffers = videoCounters?.droppedBufferCount ?: 0,
        )
    }

    private fun loopbackUrl(path: String): String =
        server.url(path).replace("http://localhost:", "http://127.0.0.1:")

    private fun runOnActivity(block: () -> Unit) {
        scenario.onActivity { block() }
    }

    private fun <T> playerRead(read: () -> T): T {
        val value = AtomicReference<Any?>()
        scenario.onActivity { value.set(read()) }
        @Suppress("UNCHECKED_CAST")
        return value.get() as T
    }

    private fun currentPosition(): Long = playerRead { controller.currentPosition }

    private fun isPlaying(): Boolean = playerRead { controller.player?.isPlaying == true }

    private fun playbackState(): Int =
        playerRead { controller.player?.playbackState ?: Player.STATE_IDLE }

    private class PlayerProbe : Player.Listener {
        val ready = CountDownLatch(1)
        val firstFrame = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val outcome = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        val stateEvents = CopyOnWriteArrayList<Int>()
        val callback = CallbackProbe()
        val analytics = AnalyticsProbe()

        override fun onPlaybackStateChanged(playbackState: Int) {
            stateEvents += playbackState
            when (playbackState) {
                Player.STATE_READY -> {
                    ready.countDown()
                    outcome.countDown()
                }
                Player.STATE_ENDED -> ended.countDown()
            }
        }

        override fun onRenderedFirstFrame() {
            firstFrame.countDown()
        }

        override fun onPlayerError(error: PlaybackException) {
            this.error.set(error)
            outcome.countDown()
        }
    }

    private class CallbackProbe : PlaybackControl.Callback {
        private val released = AtomicBoolean(false)
        private val eventCount = AtomicInteger()
        private val countAtRelease = AtomicInteger(-1)
        private val staleEvents = CopyOnWriteArrayList<String>()

        override fun onPlaybackStateChanged(snapshot: PlaybackSnapshot) {
            record("state:${snapshot.intent}:${snapshot.isBuffering}:${snapshot.isPlaying}")
        }

        override fun onPlaybackEnded() {
            record("ended")
        }

        override fun onTracksUpdated(audioTracks: List<AudioTrackUIState>, selectedIndex: Int) {
            record("tracks:${audioTracks.size}:$selectedIndex")
        }

        override fun onError(message: String) {
            record("error")
        }

        fun markReleased() {
            countAtRelease.set(eventCount.get())
            released.set(true)
        }

        fun assertNoCallbacksAfterRelease() {
            assertEquals("Callbacks advanced after release: $staleEvents", countAtRelease.get(), eventCount.get())
            assertTrue("Stale callbacks after release: $staleEvents", staleEvents.isEmpty())
        }

        private fun record(event: String) {
            eventCount.incrementAndGet()
            if (released.get()) {
                staleEvents += event
            }
        }
    }

    private class AnalyticsProbe : AnalyticsListener {
        val videoDecoderName = AtomicReference<String?>()
        val audioDecoderName = AtomicReference<String?>()
        val loadErrors = CopyOnWriteArrayList<String>()
        val playerErrors = CopyOnWriteArrayList<String>()
        private val events = CopyOnWriteArrayList<String>()

        override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
            addEvent("state:$state")
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoDecoderName.set(decoderName)
            addEvent("video-decoder:${decoderName.take(MAX_DECODER_NAME_LENGTH)}")
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            audioDecoderName.set(decoderName)
            addEvent("audio-decoder:${decoderName.take(MAX_DECODER_NAME_LENGTH)}")
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            addEvent("dropped:$droppedFrames")
        }

        override fun onLoadError(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean,
        ) {
            val value = "${error.javaClass.simpleName}:canceled=$wasCanceled"
            loadErrors += value
            addEvent("load-error:$value")
        }

        override fun onPlayerError(
            eventTime: AnalyticsListener.EventTime,
            error: PlaybackException,
        ) {
            playerErrors += error.errorCodeName
            addEvent("player-error:${error.errorCodeName}")
        }

        fun decoderSummary(): String =
            "video=${videoDecoderName.get()?.take(MAX_DECODER_NAME_LENGTH)}," +
                "audio=${audioDecoderName.get()?.take(MAX_DECODER_NAME_LENGTH)}"

        fun boundedEvents(): List<String> = events.takeLast(MAX_ANALYTICS_EVENTS)

        private fun addEvent(event: String) {
            events += event
            while (events.size > MAX_ANALYTICS_EVENTS) {
                events.removeAt(0)
            }
        }
    }

    private data class DecoderSnapshot(
        val videoMimeType: String?,
        val audioMimeType: String?,
        val videoDecoderInitCount: Int,
        val audioDecoderInitCount: Int,
        val videoQueuedInputBuffers: Int,
        val audioQueuedInputBuffers: Int,
        val renderedVideoBuffers: Int,
        val droppedVideoBuffers: Int,
    )

    private data class CodecCapabilityReport(
        val h264Decoders: List<String>,
        val aacDecoders: List<String>,
    ) {
        fun boundedSummary(): String =
            "H264=${h264Decoders.take(MAX_CAPABILITY_DECODERS)}," +
                "AAC=${aacDecoders.take(MAX_CAPABILITY_DECODERS)}"

        companion object {
            fun capture(): CodecCapabilityReport {
                val decoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                    .filterNot { it.isEncoder }
                return CodecCapabilityReport(
                    h264Decoders = decoders.supporting(MimeTypes.VIDEO_H264),
                    aacDecoders = decoders.supporting(MimeTypes.AUDIO_AAC),
                )
            }

            private fun List<android.media.MediaCodecInfo>.supporting(mimeType: String): List<String> =
                filter { codec ->
                    codec.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
                }.map { it.name }.sorted()
        }
    }

    private companion object {
        const val MAX_CACHE_BYTES = 32L * 1024L * 1024L
        const val PLAYER_TIMEOUT_SECONDS = 20L
        const val CONDITION_POLL_MS = 25L
        const val CACHE_CLEANUP_TIMEOUT_SECONDS = 10L
        const val CACHE_CLEANUP_POLL_MS = 25L
        const val MAX_DIAGNOSTIC_REQUESTS = 8
        const val MAX_ANALYTICS_EVENTS = 12
        const val MAX_CAPABILITY_DECODERS = 4
        const val MAX_DECODER_NAME_LENGTH = 80
        const val STRESS_ITERATIONS = 3
        const val CACHED_REPLAY_SEEK_POSITION_MS = 2_000L
        const val CACHED_REPLAY_SEEK_FENCE_END_MS = 2_500L
    }
}

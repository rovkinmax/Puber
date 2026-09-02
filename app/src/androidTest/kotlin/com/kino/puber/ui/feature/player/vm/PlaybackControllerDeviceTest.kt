package com.kino.puber.ui.feature.player.vm

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.player.StreamSource
import com.kino.puber.playertestfixtures.FixtureId
import com.kino.puber.playertestfixtures.PlayerTestFixtures
import com.kino.puber.playertestfixtures.network.LoopbackNetworkJournal
import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.ResponseOutcome
import com.kino.puber.playertestfixtures.server.ResponsePlan
import com.kino.puber.profile.PlayerTestControl
import com.kino.puber.ui.feature.player.PlayerInstrumentationTestCase
import com.kino.puber.ui.feature.player.model.BufferPreset
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
internal class PlaybackControllerDeviceTest : PlayerInstrumentationTestCase() {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var server: PlayerTestControl
    private lateinit var cacheDirectory: File
    private lateinit var cache: SimpleCache
    private lateinit var controller: PlaybackController
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var playerView: PlayerView

    @Before
    fun setUp() {
        LoopbackNetworkJournal(context).clear()
        server = PlayerTestControl()
        server.start(mediaRoutes())

        cacheDirectory = context.cacheDir.resolve("player-device-${UUID.randomUUID()}")
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
        scenario.onActivity { activity ->
            playerView = PlayerView(activity).apply {
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            activity.setContentView(playerView)
        }
    }

    @After
    fun tearDown() {
        var cleanupFailure: Throwable? = null
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
                try {
                    server.assertRequiredRoutesRequested()
                } catch (error: IllegalStateException) {
                    cleanupFailure = error
                }
                try {
                    server.assertNoUnknownRequests(MAX_DIAGNOSTIC_REQUESTS)
                } catch (error: AssertionError) {
                    cleanupFailure?.addSuppressed(error) ?: run {
                        cleanupFailure = error
                    }
                }
            } catch (error: Throwable) {
                cleanupFailure = error
            } finally {
                server.close()
            }
        }
        val networkJournal = LoopbackNetworkJournal(context)
        check(networkJournal.snapshot().isEmpty()) {
            "Unexpected network egress: ${networkJournal.snapshot()}"
        }
        if (::cacheDirectory.isInitialized) {
            check(!cacheDirectory.exists() || cacheDirectory.deleteRecursively()) {
                "Failed to clean player cache: $cacheDirectory"
            }
        }
        cleanupFailure?.let { throw it }
    }

    @Test
    fun progressiveMp4_reachesReadyRendersFrameAdvancesAndEnds() = run {
        lateinit var probe: PlayerProbe

        step("Prepare the progressive fixture with the production player") {
            probe = prepare(FixtureId.ProgressiveMp4, startPositionMs = 0L)
        }

        step("Reach READY and render the first progressive frame") {
            await(probe.ready, "progressive READY")
            await(probe.firstFrame, "progressive first rendered frame")
        }

        step("Advance progressive playback and reach ENDED") {
            awaitCondition("progressive position advance") {
                controller.currentPosition > 0L
            }
            await(probe.ended, "progressive ENDED")
        }

        step("Verify progressive playback and its hermetic request journal") {
            assertNoPlaybackError(probe)
            assertTrue(
                "Expected a progressive MP4 request; journal=${server.requestJournal.entries}",
                server.requestJournal.entries.any {
                    it.path == "/media/progressive.mp4" &&
                        it.outcome is ResponseOutcome.Completed
                },
            )
            assertEquals(0, server.requestJournal.unknownRequests.size)
        }
    }

    @Test
    fun hlsVod_reachesReadyRendersFrameAdvancesAndEnds() = run {
        lateinit var probe: PlayerProbe

        step("Prepare the HLS fixture with the production player") {
            probe = prepare(FixtureId.HlsMaster, startPositionMs = 0L)
        }

        step("Reach READY and render the first HLS frame") {
            await(probe.ready, "HLS READY")
            await(probe.firstFrame, "HLS first rendered frame")
        }

        step("Advance HLS playback and reach ENDED") {
            awaitCondition("HLS position advance") {
                controller.currentPosition > 0L
            }
            await(probe.ended, "HLS ENDED")
        }

        step("Verify HLS playback and its manifest and segment requests") {
            assertNoPlaybackError(probe)
            val requests = server.requestJournal.entries
            assertTrue(
                "Expected HLS master and media playlist requests, journal=$requests",
                requests.any { it.path == "/media/hls/master.m3u8" } &&
                    requests.any { it.path == "/media/hls/video_360.m3u8" },
            )
            assertTrue(
                "Expected HLS segment request, journal=$requests",
                requests.any { it.path.startsWith("/media/hls/video_") && it.path.endsWith(".ts") },
            )
            assertEquals(0, server.requestJournal.unknownRequests.size)
        }
    }

    @Test
    fun progressivePlayback_controlsAndStartPositionUseRealPlayerEvents() = run {
        lateinit var probe: PlayerProbe

        step("Start progressive playback from the requested position") {
            probe = prepare(FixtureId.ProgressiveMp4, startPositionMs = 1_000L)
            await(probe.ready, "progressive READY")
            await(probe.firstFrame, "progressive first rendered frame")
            awaitCondition("non-zero start position") {
                controller.currentPosition >= 900L
            }
        }

        step("Pause playback through the production controller") {
            runOnPlayer { controller.pause() }
            awaitCondition("pause") { !controller.isPlaying }
        }

        step("Change speed and seek forward") {
            runOnPlayer {
                controller.setSpeed(2f)
                controller.seekTo(2_000L)
            }
            awaitCondition("forward seek") {
                controller.currentPosition in 1_800L..2_400L
            }
        }

        step("Resume playback after the forward seek") {
            runOnPlayer { controller.play() }
            awaitCondition("play after seek") { controller.isPlaying }
        }

        step("Seek backward while playback remains active") {
            runOnPlayer { controller.seekTo(250L) }
            awaitCondition("backward seek") {
                controller.currentPosition in 0L..700L
            }
        }

        step("Seek near the end and reach ENDED without playback errors") {
            runOnPlayer { controller.seekTo(3_700L) }
            await(probe.ended, "near-end seek ENDED")
            assertNoPlaybackError(probe)
        }

        step("Replay from ENDED and preserve the selected speed") {
            runOnPlayer { controller.play() }
            awaitCondition("replay after ended") {
                controller.currentPosition < 1_000L && controller.isPlaying
            }
            assertEquals(2f, playerRead { checkNotNull(player).getPlaybackParameters().speed }, 0f)
        }
    }

    @Test
    fun awaitCondition_failsClosedWhenConditionRemainsFalse() = run {
        lateinit var error: AssertionError

        step("Wait on a condition that deliberately remains false") {
            error = assertThrows(AssertionError::class.java) {
                awaitCondition(
                    label = "deliberately false condition",
                    timeoutMs = FAIL_CLOSED_TIMEOUT_MS,
                ) {
                    false
                }
            }
        }

        step("Report the failed condition with its diagnostic label") {
            assertTrue(error.message.orEmpty().contains("deliberately false condition"))
        }
    }

    @Test
    fun unexpectedLoopbackRequest_failsClosedWithBoundedDiagnostics() = run {
        lateinit var error: AssertionError

        try {
            step("Send a loopback request that has no admitted route") {
                server.reset(emptyList())
                okhttp3.OkHttpClient().newCall(
                    okhttp3.Request.Builder()
                        .url(loopbackUrl("/unexpected-player-request"))
                        .build(),
                ).execute().close()
                server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
            }

            step("Reject the unknown request with bounded diagnostics") {
                error = assertThrows(AssertionError::class.java) {
                    server.assertNoUnknownRequests(MAX_DIAGNOSTIC_REQUESTS)
                }
                assertTrue(error.message.orEmpty().contains("/unexpected-player-request"))
            }
        } finally {
            server.reset(emptyList())
        }
    }

    private fun prepare(
        fixture: FixtureId,
        startPositionMs: Long? = null,
    ): PlayerProbe {
        val probe = PlayerProbe()
        val stream = when (fixture) {
            FixtureId.ProgressiveMp4 -> StreamSource(
                url = loopbackUrl("/media/progressive.mp4"),
                isHls = false,
            )
            FixtureId.HlsMaster -> StreamSource(
                url = loopbackUrl("/media/hls/master.m3u8"),
                isHls = true,
            )
            else -> error("Unsupported device fixture: $fixture")
        }
        scenario.onActivity {
            controller.prepare(
                stream = stream,
                subtitles = null,
                startPosition = startPositionMs,
                bufferPreset = BufferPreset.SMALL,
                fastDns = false,
            )
            val player = checkNotNull(controller.player)
            player.addListener(probe)
            playerView.player = player
        }
        return probe
    }

    private fun loopbackUrl(path: String): String =
        server.url(path).replace("http://localhost:", "http://127.0.0.1:")

    private fun mediaRoutes(): List<HermeticRoute> {
        val routes = mutableListOf(
            server.route(
                id = "progressive",
                path = "/media/progressive.mp4",
                response = ResponsePlan.Range(
                    body = PlayerTestFixtures.readBytes(FixtureId.ProgressiveMp4, context),
                    contentType = "video/mp4",
                ),
            ),
            server.route(
                id = "master",
                path = "/media/hls/master.m3u8",
                response = HermeticTestServer.text(
                    body = PlayerTestFixtures.readBytes(FixtureId.HlsMaster, context)
                        .toString(Charsets.UTF_8),
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
        )
        FixtureId.entries
            .filter { it.path.startsWith("hls/") && it != FixtureId.HlsMaster }
            .forEach { fixture ->
                routes += server.route(
                    id = fixture.path,
                    path = "/media/${fixture.path}",
                    response = HermeticTestServer.text(
                        body = PlayerTestFixtures.readBytes(fixture, context)
                            .toString(Charsets.UTF_8),
                        contentType = HLS_CONTENT_TYPE,
                    ),
                )
            }
        listOf(
            "video_360_000.ts",
            "video_360_001.ts",
            "video_720_000.ts",
            "video_720_001.ts",
            "audio_english_000.ts",
            "audio_english_001.ts",
            "audio_english_002.ts",
            "audio_spanish_000.ts",
            "audio_spanish_001.ts",
            "audio_spanish_002.ts",
        ).forEach { filename ->
            routes += server.route(
                id = filename,
                path = "/media/hls/$filename",
                response = HermeticTestServer.bytes(
                    body = PlayerTestFixtures.openPath("hls/$filename", context)
                        .use { it.readBytes() },
                    contentType = "video/mp2t",
                ),
            )
        }
        return routes
    }

    private fun runOnPlayer(block: () -> Unit) {
        scenario.onActivity { block() }
    }

    private fun await(latch: CountDownLatch, label: String) {
        assertTrue(
            "$label timed out; diagnostics=${diagnostics()}",
            latch.await(PLAYER_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
    }

    private fun awaitCondition(
        label: String,
        timeoutMs: Long = TimeUnit.SECONDS.toMillis(PLAYER_TIMEOUT_SECONDS),
        condition: () -> Boolean,
    ) {
        val reached = CountDownLatch(1)
        val conditionSatisfied = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        lateinit var check: Runnable
        check = Runnable {
            if (condition()) {
                conditionSatisfied.set(true)
                reached.countDown()
            } else if (System.nanoTime() < deadline) {
                handler.postDelayed(check, CONDITION_POLL_MS)
            } else {
                reached.countDown()
            }
        }
        handler.post(check)
        val pollingCompleted = reached.await(
            timeoutMs + CONDITION_TIMEOUT_GRACE_MS,
            TimeUnit.MILLISECONDS,
        )
        handler.removeCallbacks(check)
        assertTrue(
            "$label polling timed out; diagnostics=${diagnostics()}",
            pollingCompleted,
        )
        assertTrue(
            "$label condition remained false; diagnostics=${diagnostics()}",
            conditionSatisfied.get(),
        )
    }

    private fun assertNoPlaybackError(probe: PlayerProbe) {
        assertTrue(
            "Playback error=${probe.error.get()?.errorCodeName}; diagnostics=${diagnostics()}",
            probe.error.get() == null,
        )
    }

    private fun diagnostics(): String {
        val playerState = playerRead {
            val player = controller.player
            val debug = controller.getDebugInfo()
            "state=${player?.playbackState},isPlaying=${player?.isPlaying}," +
                "position=${player?.currentPosition},duration=${player?.duration}," +
                "formats=${debug?.videoResolution}/${debug?.videoCodec}/" +
                "${debug?.audioCodec}"
        }
        return "$playerState,requests=${server.requestJournal.entries.takeLast(12)}"
    }

    private fun <T> playerRead(read: PlaybackController.() -> T): T {
        val value = AtomicReference<T>()
        scenario.onActivity { value.set(read(controller)) }
        return checkNotNull(value.get())
    }

    private class PlayerProbe : Player.Listener {
        val ready = CountDownLatch(1)
        val firstFrame = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        val stateEvents = CopyOnWriteArrayList<Int>()

        override fun onPlaybackStateChanged(playbackState: Int) {
            stateEvents += playbackState
            when (playbackState) {
                Player.STATE_READY -> ready.countDown()
                Player.STATE_ENDED -> ended.countDown()
            }
        }

        override fun onRenderedFirstFrame() {
            firstFrame.countDown()
        }

        override fun onPlayerError(error: PlaybackException) {
            this.error.set(error)
            ended.countDown()
        }
    }

    private companion object {
        const val HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl"
        const val MAX_CACHE_BYTES = 32L * 1024L * 1024L
        const val PLAYER_TIMEOUT_SECONDS = 20L
        const val CONDITION_POLL_MS = 25L
        const val CONDITION_TIMEOUT_GRACE_MS = 1_000L
        const val FAIL_CLOSED_TIMEOUT_MS = 100L
        const val MAX_DIAGNOSTIC_REQUESTS = 8
    }
}

package com.kino.puber.ui.feature.player.vm

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.playertestfixtures.FixtureId
import com.kino.puber.playertestfixtures.PlayerTestFixtures
import com.kino.puber.playertestfixtures.network.LoopbackNetworkJournal
import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.QueryMatchMode
import com.kino.puber.playertestfixtures.server.ResponseOutcome
import com.kino.puber.playertestfixtures.server.ResponsePlan
import com.kino.puber.profile.PlayerTestControl
import com.kino.puber.ui.feature.player.PlayerInstrumentationTestCase
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.BufferPreset
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
internal class PlaybackControllerNetworkTracksTest : PlayerInstrumentationTestCase() {

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
        server.start()

        cacheDirectory = context.cacheDir.resolve("player-network-${UUID.randomUUID()}")
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
        if (::scenario.isInitialized) {
            scenario.onActivity {
                playerView.player = null
                controller.release()
            }
            scenario.close()
        }
        if (::cache.isInitialized) cache.release()
        if (::server.isInitialized) {
            try {
                server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
                server.assertNoUnknownRequests(MAX_DIAGNOSTIC_REQUESTS)
            } finally {
                server.close()
            }
        }
        check(LoopbackNetworkJournal(context).snapshot().isEmpty()) {
            "Unexpected network egress: ${LoopbackNetworkJournal(context).snapshot()}"
        }
        if (::cacheDirectory.isInitialized) {
            check(!cacheDirectory.exists() || cacheDirectory.deleteRecursively()) {
                "Failed to clean player cache: $cacheDirectory"
            }
        }
    }

    @Test
    fun hlsTracks_selectsAudioAndSubtitle_observesCueAndDisablesText() {
        server.reset(commonHlsRoutes())
        val subtitleUrl = loopbackUrl("/media/subtitle.vtt?signature=test-signature")
        val probe = prepare(
            path = "/media/hls/master.m3u8",
            subtitles = listOf(
                SubtitleLink(
                    lang = "en",
                    url = subtitleUrl,
                ),
            ),
        )

        awaitReady(probe)
        runOnPlayer { controller.pause() }
        awaitCondition("both AAC renditions are exposed") {
            audioTracks().map(AudioTrackUIState::language).toSet() == setOf("en", "es")
        }
        assertEquals(setOf("en", "es"), audioTracks().map(AudioTrackUIState::language).toSet())

        runOnPlayer { controller.selectAudioTrack(1) }
        awaitCondition("Spanish AAC rendition selected") {
            selectedAudioLanguage() == "es"
        }

        val subtitle = SubtitleTrackUIState(
            index = 1,
            label = "English",
            language = "en",
            url = subtitleUrl,
        )
        runOnPlayer { controller.selectSubtitle(subtitle) }
        awaitCondition("side-loaded WebVTT track enabled on HLS") {
            !textTracksDisabled() && selectedTextTrack()
        }
        assertEquals("es", selectedAudioLanguage())
        runOnPlayer {
            controller.seekTo(900L)
            controller.play()
        }
        awaitCondition("fixture cue is rendered") {
            probe.cueTexts.any { it.contains("Synthetic player fixture cue") } ||
                currentCueTexts().any { it.contains("Synthetic player fixture cue") }
        }
        assertTrue(server.requestJournal.entries.any { it.path == "/media/subtitle.vtt" })

        runOnPlayer { controller.selectSubtitle(null) }
        awaitCondition("WebVTT track disabled") { textTracksDisabled() }
        assertEquals("es", selectedAudioLanguage())
        assertEquals(0, server.requestJournal.unknownRequests.size)
    }

    @Test
    fun hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes() {
        val gate = CountDownLatch(1)
        server.reset(
            commonHlsRoutes(
                extraRoutes = listOf(
                    server.route(
                        id = "chunkless-video-gate",
                        path = "/media/hls/video_720_000.ts",
                        response = HermeticTestServer.delayed(
                            gate = gate,
                            body = fixtureBytes("hls/video_720_000.ts"),
                            contentType = "video/mp2t",
                        ),
                    ),
                    server.route(
                        id = "chunkless-audio-gate",
                        path = "/media/hls/audio_english_000.ts",
                        response = HermeticTestServer.delayed(
                            gate = gate,
                            body = fixtureBytes("hls/audio_english_000.ts"),
                            contentType = "video/mp2t",
                        ),
                    ),
                ),
            ),
        )
        val probe = prepare("/media/hls/master.m3u8")

        try {
            awaitCondition("first HLS media chunks are blocked") {
                server.activeRequestCount > 0
            }
            awaitCondition("chunkless HLS preparation exposes CODECS tracks") {
                server.activeRequestCount > 0 && playerRead {
                    val trackTypes = player?.currentTracks?.groups
                        ?.map { it.type }
                        .orEmpty()
                    C.TRACK_TYPE_VIDEO in trackTypes && C.TRACK_TYPE_AUDIO in trackTypes
                }
            }
        } finally {
            gate.countDown()
        }

        awaitReady(probe)
        assertTrue(probe.error.get() == null)
        assertEquals(0, server.requestJournal.unknownRequests.size)
    }

    @Test
    fun hlsSwitch_preservesPositionIntentTracks_andLastStreamWins() {
        server.reset(commonHlsRoutes())
        val lowUrl = loopbackUrl("/media/hls/quality-low.m3u8")
        val highUrl = loopbackUrl("/media/hls/quality-high.m3u8")
        val subtitleUrl = loopbackUrl("/media/subtitle.vtt?scenario=quality-switch")
        val subtitle = SubtitleLink(
            lang = "en",
            url = subtitleUrl,
        )
        val subtitleTrack = SubtitleTrackUIState(1, "English", "en", subtitleUrl)
        val probe = prepare("/media/hls/quality-low.m3u8", listOf(subtitle))

        awaitReady(probe)
        awaitCondition("audio tracks before switch") { audioTracks().size == 2 }
        runOnPlayer {
            controller.selectAudioTrack(1)
            controller.selectSubtitle(subtitleTrack)
            controller.seekTo(1_500L)
        }
        awaitCondition("position before switch") {
            currentPosition() in 1_000L..2_100L
        }
        awaitCondition("Spanish selection before switch") { selectedAudioLanguage() == "es" }
        awaitCondition("WebVTT selection before switch") {
            !textTracksDisabled() && selectedTextTrack()
        }
        awaitCondition("WebVTT request before switch") {
            server.requestJournal.entries.any { it.path == "/media/subtitle.vtt" }
        }
        runOnPlayer { controller.pause() }
        awaitCondition("paused switch source") { !isPlaying() }
        val pausedPosition = currentPosition()

        runOnPlayer { controller.switchStream(highUrl, listOf(subtitle)) }
        awaitCondition("high quality source prepared") {
            currentMediaPath() == "/media/hls/quality-high.m3u8" &&
                playbackState() == Player.STATE_READY
        }
        assertFalse(textTracksDisabled())
        assertTrue(selectedTextTrack())
        assertFalse(isPlaying())
        assertEquals(PlaybackIntent.Paused, playbackIntent())
        assertTrue(currentPosition() in (pausedPosition - 800L)..(pausedPosition + 800L))
        awaitCondition("audio selection restored after switch") { selectedAudioLanguage() == "es" }
        runOnPlayer { controller.play() }
        awaitCondition("play intent restored") {
            isPlaying() && playbackIntent() == PlaybackIntent.PlayRequested
        }
        runOnPlayer {
            controller.switchStream(lowUrl, listOf(subtitle))
            controller.switchStream(highUrl, listOf(subtitle))
            controller.switchStream(lowUrl, listOf(subtitle))
        }
        awaitCondition("last rapid switch wins") {
            currentMediaPath() == "/media/hls/quality-low.m3u8" &&
                playbackIntent() == PlaybackIntent.PlayRequested
        }
        assertTrue(currentPosition() >= pausedPosition - 1_000L)
        val masterPaths = server.requestJournal.entries
            .filter { it.path.startsWith("/media/hls/quality-") }
            .map { it.path }
        assertTrue(masterPaths.contains("/media/hls/quality-low.m3u8"))
        assertTrue(masterPaths.contains("/media/hls/quality-high.m3u8"))
        assertEquals(0, server.requestJournal.unknownRequests.size)
        assertTrue(probe.error.get() == null)
    }

    @Test
    fun progressiveSwitch_restoresSelectedSubtitleAndPausedIntent() {
        server.reset(commonHlsRoutes())
        val subtitleUrl = loopbackUrl("/media/subtitle.vtt")
        val subtitle = SubtitleTrackUIState(1, "English", "en", subtitleUrl)
        val first = prepare(
            path = "/media/progressive-a.mp4",
            subtitles = listOf(SubtitleLink(lang = "en", url = subtitleUrl)),
        )

        awaitReady(first)
        runOnPlayer { controller.pause() }
        runOnPlayer { controller.selectSubtitle(subtitle) }
        awaitCondition("subtitle selected before progressive switch") {
            !textTracksDisabled() && selectedTextTrack()
        }
        val positionBeforeSwitch = currentPosition()

        val secondUrl = loopbackUrl("/media/progressive-b.mp4")
        runOnPlayer {
            controller.switchStream(
                streamUrl = secondUrl,
                subtitles = listOf(SubtitleLink(lang = "en", url = subtitleUrl)),
            )
        }
        awaitCondition("progressive switch completes") {
            currentMediaPath() == "/media/progressive-b.mp4" &&
                playbackState() == Player.STATE_READY
        }
        assertEquals(PlaybackIntent.Paused, playbackIntent())
        assertFalse(isPlaying())
        assertTrue(currentPosition() in (positionBeforeSwitch - 800L)..(positionBeforeSwitch + 800L))
        awaitCondition("subtitle selection restored after progressive switch") {
            !textTracksDisabled() && selectedTextTrack()
        }
        runOnPlayer { controller.selectSubtitle(null) }
        awaitCondition("subtitle disabled after progressive switch") { textTracksDisabled() }
    }

    @Test
    fun hls404AndMalformedPlaylist_failWithoutUnboundedRetry() {
        runPlaylistFailure(
            path = "/media/fault-404.m3u8",
            response = HermeticTestServer.text(
                body = "not found",
                status = 404,
                contentType = HLS_CONTENT_TYPE,
            ),
        )
        runPlaylistFailure(
            path = "/media/fault-malformed.m3u8",
            response = HermeticTestServer.text(
                body = "this is not an HLS playlist",
                contentType = HLS_CONTENT_TYPE,
            ),
        )
    }

    @Test
    fun hlsVariant400_fallsBackToTheHealthyVariant() {
        assertVariantHttpFailureFallsBack(status = 400)
    }

    @Test
    fun hlsVariant502_fallsBackToTheHealthyVariant() {
        assertVariantHttpFailureFallsBack(status = 502)
    }

    private fun assertVariantHttpFailureFallsBack(status: Int) {
        val validLowPlaylist = playlist(FixtureId.HlsVideo360Playlist)
        server.reset(
            commonHlsRoutes(
                lowPlaylist = HermeticTestServer.sequence(
                    HermeticTestServer.text(
                        body = "variant unavailable",
                        status = status,
                        contentType = HLS_CONTENT_TYPE,
                    ),
                    HermeticTestServer.text(
                        body = validLowPlaylist,
                        contentType = HLS_CONTENT_TYPE,
                    ),
                ),
            ),
        )
        val probe = prepare("/media/hls/master.m3u8")

        awaitReady(probe)
        awaitCondition("healthy HLS variant after $status") {
            playbackState() == Player.STATE_READY &&
                server.requestJournal.entries.any { it.path == "/media/hls/video_720.m3u8" }
        }
        val failedVariant = server.requestJournal.entries.filter {
            it.path == "/media/hls/video_360.m3u8"
        }
        assertEquals(1, failedVariant.size)
        assertEquals(
            status,
            (failedVariant.single().outcome as ResponseOutcome.Completed).status,
        )
        assertTrue(server.requestJournal.entries.any { it.path == "/media/hls/video_720.m3u8" })
        assertTrue(probe.error.get() == null)
        assertEquals(0, server.requestJournal.unknownRequests.size)
    }

    @Test
    fun hlsDelayedAndInterruptedSegment_enterBufferingThenRecover() {
        val gate = CountDownLatch(1)
        server.reset(
            commonHlsRoutes(
                qualityLowMasterBody = singleVariantMaster("video_360.m3u8"),
                video360Playlist = HermeticTestServer.text(
                    body = playlist(FixtureId.HlsVideo360Playlist),
                    contentType = HLS_CONTENT_TYPE,
                ),
                extraRoutes = listOf(
                    server.route(
                        id = "delayed-segment",
                        path = "/media/hls/video_360_000.ts",
                        response = HermeticTestServer.delayed(
                            gate = gate,
                            body = fixtureBytes("hls/video_360_000.ts"),
                            contentType = "video/mp2t",
                        ),
                    ),
                ),
            ),
        )
        val delayedProbe = prepare("/media/hls/quality-low.m3u8")
        awaitCondition("delayed segment enters BUFFERING") {
            delayedProbe.stateEvents.contains(Player.STATE_BUFFERING) ||
                playbackState() == Player.STATE_BUFFERING
        }
        gate.countDown()
        awaitReady(delayedProbe)
        assertTrue(delayedProbe.error.get() == null)

        releasePlayerAndReset()
        val segment = fixtureBytes("hls/video_360_000.ts")
        server.reset(
            commonHlsRoutes(
                qualityLowMasterBody = singleVariantMaster("video_360_recovery.m3u8"),
                extraRoutes = listOf(
                    server.route(
                        id = "recovery-master",
                        path = "/media/hls/recovery.m3u8",
                        response = HermeticTestServer.text(
                            body = singleVariantMaster("video_360_recovery.m3u8"),
                            contentType = HLS_CONTENT_TYPE,
                        ),
                    ),
                    server.route(
                        id = "recovering-playlist",
                        path = "/media/hls/video_360_recovery.m3u8",
                        response = HermeticTestServer.text(
                            body = playlist(FixtureId.HlsVideo360Playlist)
                                .replace("video_360_", "video_360_recovery_"),
                            contentType = HLS_CONTENT_TYPE,
                        ),
                    ),
                    server.route(
                        id = "recovering-segment",
                        path = "/media/hls/video_360_recovery_000.ts",
                        response = HermeticTestServer.sequence(
                            HermeticTestServer.truncated(
                                body = segment,
                                bytesToWrite = segment.size / 4,
                                contentType = "video/mp2t",
                            ),
                            HermeticTestServer.bytes(segment, contentType = "video/mp2t"),
                        ),
                    ),
                    server.route(
                        id = "recovering-segment-1",
                        path = "/media/hls/video_360_recovery_001.ts",
                        response = HermeticTestServer.bytes(
                            fixtureBytes("hls/video_360_001.ts"),
                            contentType = "video/mp2t",
                        ),
                    ),
                ),
            ),
        )
        val recoveryProbe = prepare("/media/hls/recovery.m3u8")
        awaitReady(recoveryProbe)
        val recoveryEntries = server.requestJournal.entries.filter {
            it.routeId == "recovering-segment"
        }
        assertTrue(
            "Expected interrupted segment recovery; entries=$recoveryEntries",
            recoveryEntries.size >= 2,
        )
        assertEquals(200, (recoveryEntries[0].outcome as ResponseOutcome.Completed).status)
        assertEquals(200, (recoveryEntries[1].outcome as ResponseOutcome.Completed).status)
        assertTrue(
            "Expected a ranged recovery request; entries=$recoveryEntries",
            recoveryEntries[1].range != null,
        )
        assertTrue(recoveryProbe.error.get() == null)
        assertEquals(0, server.requestJournal.unknownRequests.size)
    }

    @Test
    fun playerMediaNetworkIsolation_rejectsExternalRedirectAndSanitizesViolation() {
        server.reset(
            listOf(
                server.route(
                    id = "external-redirect",
                    path = "/media/redirect.m3u8",
                    response = HermeticTestServer.redirect(
                        "https://redirect-user:redirect-password@example.com/blocked" +
                            "?token=must-not-be-recorded",
                    ),
                ),
            ),
        )
        val probe = prepare("/media/redirect.m3u8")

        awaitCondition("external redirect fails locally") { probe.error.get() != null }
        awaitCondition("one external violation is journaled") {
            LoopbackNetworkJournal(context).snapshot().size == 1
        }
        try {
            val violations = LoopbackNetworkJournal(context).snapshot()
            assertEquals(1, violations.size)
            assertTrue(violations.single().startsWith("https://example.com:443"))
            assertFalse(violations.single().contains("redirect-user"))
            assertFalse(violations.single().contains("redirect-password"))
            assertFalse(violations.single().contains("token"))
            assertFalse(violations.single().contains("?"))
            assertEquals(0, server.requestJournal.unknownRequests.size)
        } finally {
            LoopbackNetworkJournal(context).clear()
        }
    }

    private fun runPlaylistFailure(path: String, response: ResponsePlan) {
        releasePlayerAndReset()
        server.reset(
            listOf(
                server.route(id = "fault", path = path, response = response),
            ),
        )
        val probe = prepare(path)
        awaitCondition("playlist failure $path") { probe.error.get() != null }
        val requests = server.requestJournal.entries.filter { it.path == path }
        assertTrue(
            "Expected bounded retry count for $path, got ${requests.size}",
            requests.size in 1..6,
        )
        assertTrue(probe.error.get() is PlaybackException)
        assertEquals(0, server.requestJournal.unknownRequests.size)
    }

    private fun prepare(
        path: String,
        subtitles: List<SubtitleLink>? = null,
    ): PlayerProbe {
        val probe = PlayerProbe()
        val streamUrl = loopbackUrl(path)
        scenario.onActivity {
            controller.prepare(
                streamUrl = streamUrl,
                subtitles = subtitles,
                startPosition = 0L,
                bufferPreset = BufferPreset.SMALL,
                fastDns = false,
            )
            val player = checkNotNull(controller.player)
            player.addListener(probe)
            playerView.player = player
        }
        return probe
    }

    private fun releasePlayerAndReset() {
        scenario.onActivity {
            playerView.player = null
            controller.release()
        }
        server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
    }

    private fun awaitReady(probe: PlayerProbe) {
        awaitCondition("READY") {
            playbackState() == Player.STATE_READY
        }
        assertTrue(probe.error.get() == null)
    }

    private fun currentPosition(): Long = playerRead { currentPosition }

    private fun isPlaying(): Boolean = playerRead { isPlaying }

    private fun playbackIntent(): PlaybackIntent = playerRead { playbackIntent }

    private fun playbackState(): Int = playerRead { player?.playbackState ?: Player.STATE_IDLE }

    private fun audioTracks(): List<AudioTrackUIState> {
        val value = AtomicReference<List<AudioTrackUIState>>()
        scenario.onActivity {
            value.set(
                controller.player?.currentTracks?.groups
                    ?.filter { it.type == C.TRACK_TYPE_AUDIO }
                    ?.mapIndexed { index, group ->
                        val format = group.getTrackFormat(0)
                        AudioTrackUIState(
                            index = index,
                            label = format.label.orEmpty(),
                            language = format.language.orEmpty(),
                        )
                    }
                    .orEmpty(),
            )
        }
        return value.get().orEmpty()
    }

    private fun selectedAudioLanguage(): String? = playerRead {
        player?.currentTracks?.groups
            ?.filter { it.type == C.TRACK_TYPE_AUDIO }
            ?.firstOrNull { it.isSelected }
            ?.getTrackFormat(0)
            ?.language
    }

    private fun selectedTextTrack(): Boolean = playerRead {
        player?.currentTracks?.groups
            ?.filter { it.type == C.TRACK_TYPE_TEXT }
            ?.any { it.isSelected } == true
    }

    private fun textTracksDisabled(): Boolean = playerRead {
        player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true
    }

    private fun currentMediaPath(): String? = playerRead {
        player?.currentMediaItem?.localConfiguration?.uri?.encodedPath
    }

    private fun currentCueTexts(): List<String> = playerRead {
        player?.currentCues?.cues?.mapNotNull { it.text?.toString() }.orEmpty()
    }

    private fun <T> playerRead(read: PlaybackController.() -> T): T {
        val value = AtomicReference<T>()
        scenario.onActivity { value.set(read(controller)) }
        return value.get()
    }

    private fun runOnPlayer(block: () -> Unit) {
        scenario.onActivity { block() }
    }

    private fun awaitCondition(label: String, condition: () -> Boolean) {
        val reached = CountDownLatch(1)
        val result = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PLAYER_TIMEOUT_SECONDS)
        lateinit var check: Runnable
        check = Runnable {
            if (condition()) {
                result.set(true)
                reached.countDown()
            } else if (System.nanoTime() < deadline) {
                handler.postDelayed(check, CONDITION_POLL_MS)
            } else {
                reached.countDown()
            }
        }
        handler.post(check)
        assertTrue("$label timed out; diagnostics=${diagnostics()}", reached.await(
            PLAYER_TIMEOUT_SECONDS + 1,
            TimeUnit.SECONDS,
        ))
        handler.removeCallbacks(check)
        assertTrue("$label was not reached; diagnostics=${diagnostics()}", result.get())
    }

    private fun diagnostics(): String {
        val playerState = playerRead {
            val player = controller.player
            val debug = controller.getDebugInfo()
            "state=${player?.playbackState},isPlaying=${player?.isPlaying}," +
                "position=${player?.currentPosition},duration=${player?.duration}," +
                "formats=${debug?.videoResolution}/${debug?.videoCodec}/${debug?.audioCodec}"
        }
        return "$playerState,requests=${server.requestJournal.entries.takeLast(12)}"
    }

    private fun loopbackUrl(path: String): String =
        server.url(path).replace("http://localhost:", "http://127.0.0.1:")

    private fun commonHlsRoutes(
        masterBody: String = fixtureText(FixtureId.HlsMaster),
        qualityLowMasterBody: String = singleVariantMaster("video_360.m3u8"),
        lowPlaylist: ResponsePlan = HermeticTestServer.text(
            body = playlist(FixtureId.HlsVideo360Playlist),
            contentType = HLS_CONTENT_TYPE,
        ),
        highPlaylist: ResponsePlan = HermeticTestServer.text(
            body = playlist(FixtureId.HlsVideo720Playlist),
            contentType = HLS_CONTENT_TYPE,
        ),
        video360Playlist: ResponsePlan? = null,
        extraRoutes: List<HermeticRoute> = emptyList(),
    ): List<HermeticRoute> {
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
                id = "progressive-a",
                path = "/media/progressive-a.mp4",
                response = ResponsePlan.Range(
                    body = PlayerTestFixtures.readBytes(FixtureId.ProgressiveMp4, context),
                    contentType = "video/mp4",
                ),
            ),
            server.route(
                id = "progressive-b",
                path = "/media/progressive-b.mp4",
                response = ResponsePlan.Range(
                    body = PlayerTestFixtures.readBytes(FixtureId.ProgressiveMp4, context),
                    contentType = "video/mp4",
                ),
            ),
            server.route(
                id = "master",
                path = "/media/hls/master.m3u8",
                response = HermeticTestServer.text(
                    body = masterBody,
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
            server.route(
                id = "quality-low",
                path = "/media/hls/quality-low.m3u8",
                response = HermeticTestServer.text(
                    body = qualityLowMasterBody,
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
            server.route(
                id = "quality-high",
                path = "/media/hls/quality-high.m3u8",
                response = HermeticTestServer.text(
                    body = singleVariantMaster("video_720.m3u8"),
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
            server.route(
                id = "video-360-playlist",
                path = "/media/hls/video_360.m3u8",
                response = video360Playlist ?: lowPlaylist,
            ),
            server.route(
                id = "video-720-playlist",
                path = "/media/hls/video_720.m3u8",
                response = highPlaylist,
            ),
            server.route(
                id = "audio-english-playlist",
                path = "/media/hls/audio_english.m3u8",
                response = HermeticTestServer.text(
                    body = playlist(FixtureId.HlsAudioEnglishPlaylist),
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
            server.route(
                id = "audio-spanish-playlist",
                path = "/media/hls/audio_spanish.m3u8",
                response = HermeticTestServer.text(
                    body = playlist(FixtureId.HlsAudioSpanishPlaylist),
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
            server.route(
                id = "subtitle",
                path = "/media/subtitle.vtt",
                queryMode = QueryMatchMode.Contains,
                response = HermeticTestServer.text(
                    body = fixtureText(FixtureId.SubtitleWebVtt),
                    contentType = "text/vtt",
                ),
            ),
        )
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
                    body = PlayerTestFixtures.openPath("hls/$filename", context).use { it.readBytes() },
                    contentType = "video/mp2t",
                ),
            )
        }
        return extraRoutes + routes
    }

    private fun fixtureText(fixture: FixtureId): String =
        PlayerTestFixtures.readBytes(fixture, context).toString(Charsets.UTF_8)

    private fun playlist(fixture: FixtureId): String = fixtureText(fixture)

    private fun fixtureBytes(path: String): ByteArray =
        PlayerTestFixtures.openPath(path, context).use { it.readBytes() }

    private fun singleVariantMaster(videoPlaylist: String): String =
        """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-INDEPENDENT-SEGMENTS
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="audio_english.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Español",LANGUAGE="es",DEFAULT=NO,AUTOSELECT=YES,URI="audio_spanish.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42c01e,mp4a.40.2",AUDIO="audio"
        $videoPlaylist
        """.trimIndent()

    private class PlayerProbe : Player.Listener {
        val stateEvents = CopyOnWriteArrayList<Int>()
        val cueTexts = CopyOnWriteArrayList<String>()
        val error = AtomicReference<PlaybackException?>()

        override fun onPlaybackStateChanged(playbackState: Int) {
            stateEvents += playbackState
        }

        override fun onTracksChanged(tracks: Tracks) = Unit

        override fun onCues(cueGroup: CueGroup) {
            cueTexts += cueGroup.cues.mapNotNull { it.text?.toString() }
        }

        override fun onCues(cues: List<Cue>) {
            cueTexts += cues.mapNotNull { it.text?.toString() }
        }

        override fun onPlayerError(error: PlaybackException) {
            this.error.set(error)
        }
    }

    private companion object {
        const val HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl"
        const val MAX_CACHE_BYTES = 32L * 1024L * 1024L
        const val PLAYER_TIMEOUT_SECONDS = 20L
        const val CONDITION_POLL_MS = 25L
        const val MAX_DIAGNOSTIC_REQUESTS = 8
    }
}

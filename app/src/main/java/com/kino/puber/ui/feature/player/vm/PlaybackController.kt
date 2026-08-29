package com.kino.puber.ui.feature.player.vm

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsManifest
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.extractor.DefaultExtractorsFactory
import com.kino.puber.BuildConfig
import com.kino.puber.R
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.BufferPreset
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff
import java.net.URI
import java.util.Locale
import okhttp3.OkHttpClient

internal interface PlaybackControl {
    interface Callback : PlaybackEventSink {
        fun onTracksUpdated(
            audioTracks: List<AudioTrackUIState>,
            selectedIndex: Int,
            subtitleTracks: List<SubtitleTrackUIState> = emptyList(),
        )
        fun onError(message: String)
    }

    val currentPosition: Long
    val duration: Long
    val isPlaying: Boolean
    val playbackIntent: PlaybackIntent
    val shouldKeepScreenOn: Boolean
    val bufferedPosition: Long

    fun setCallback(callback: Callback)
    fun prepare(
        streamUrl: String,
        subtitles: List<SubtitleLink>?,
        startPosition: Long?,
        bufferPreset: BufferPreset = BufferPreset.AUTO,
        fastDns: Boolean = true,
    )

    fun switchStream(streamUrl: String, subtitles: List<SubtitleLink>?)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun selectAudioTrack(groupIndex: Int)
    fun selectSubtitle(track: SubtitleTrackUIState?)
    fun release()
}

internal class PlaybackController(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val mediaCache: androidx.media3.datasource.cache.Cache,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
) : PlaybackControl {

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var playerListener: Player.Listener? = null
    private var callbackSession: PlaybackCallbackGate.Session? = null
    private var useFastDns = true
    private var pendingSubtitleTrack: SubtitleTrackUIState? = null
    private val ac3FallbackPolicy = Ac3FallbackPolicy()
    private val callbackGate = PlaybackCallbackGate()
    private val mediaItemFactory = PlaybackMediaItemFactory()
    private val subtitleTrackSelector = SubtitleTrackSelector()

    @OptIn(UnstableApi::class)
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
    private var dataSourceFactory: DataSource.Factory? = null

    val player: ExoPlayer? get() = exoPlayer
    override val currentPosition: Long get() = exoPlayer?.currentPosition ?: 0L
    override val duration: Long get() = exoPlayer?.duration?.coerceAtLeast(0) ?: 0L
    override val isPlaying: Boolean get() = exoPlayer?.isPlaying == true
    override val playbackIntent: PlaybackIntent
        get() = playbackSnapshot().intent
    override val shouldKeepScreenOn: Boolean
        get() = playbackSnapshot().shouldKeepScreenOn
    override val bufferedPosition: Long get() = exoPlayer?.bufferedPosition ?: 0L
    
    private fun createPlayerListener(session: PlaybackCallbackGate.Session) =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                notifyPlaybackState(session)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                callbackGate.dispatch(session) { callback ->
                    exoPlayer?.let { player ->
                        PlaybackTransitions.dispatchPlaybackState(
                            engine = ExoPlayerPlaybackEngine(player),
                            playbackState = playbackState,
                            sink = callback,
                        )
                    }
                    if (playbackState == Player.STATE_READY) {
                        notifyTracksUpdated(callback)
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                notifyPlaybackState(session)
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                notifyPlaybackState(session)
            }

            override fun onTracksChanged(tracks: Tracks) {
                callbackGate.dispatch(session) { callback ->
                    notifyTracksUpdated(callback)
                    applyPendingSubtitleSelection()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                callbackGate.dispatch(session) { callback ->
                    val cause = error.cause
                    when {
                        cause is BehindLiveWindowException -> recoverBehindLiveWindow()
                        cause.isAc3DecoderInitializationException() -> when (
                            val decision = ac3FallbackPolicy.onDecoderInitializationFailure(
                                exoPlayer?.currentPosition ?: 0L,
                            )
                        ) {
                            is Ac3FallbackPolicy.Decision.Retry ->
                                disableAc3AndRetry(decision.positionMs)
                            Ac3FallbackPolicy.Decision.Terminal -> callback?.onError(
                                context.getString(R.string.player_error_playback)
                            )
                        }
                        else -> callback?.onError(
                            error.localizedMessage ?: context.getString(R.string.player_error_playback)
                        )
                    }
                }
            }
        }

    private fun recoverBehindLiveWindow() {
        exoPlayer?.let { player ->
            player.seekToDefaultPosition()
            player.prepare()
        }
    }

    private fun Throwable?.isAc3DecoderInitializationException(): Boolean {
        return this is MediaCodecRenderer.DecoderInitializationException && mimeType == MimeTypes.AUDIO_AC3
    }

    override fun setCallback(callback: PlaybackControl.Callback) {
        callbackGate.setCallback(callback)
    }

    @OptIn(UnstableApi::class)
    override fun prepare(
        streamUrl: String,
        subtitles: List<SubtitleLink>?,
        startPosition: Long?,
        bufferPreset: BufferPreset,
        fastDns: Boolean,
    ) {
        release()
        ac3FallbackPolicy.reset()
        useFastDns = fastDns

        val bufferParams = DeviceBufferConfig.resolve(context, bufferPreset)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferParams.minBufferMs,
                bufferParams.maxBufferMs,
                bufferParams.bufferForPlaybackMs,
                bufferParams.bufferForPlaybackAfterRebufferMs,
            )
            .setBackBuffer(
                bufferParams.backBufferDurationMs,
                /* retainBackBufferFromKeyframe = */ false,
            )
            .setTargetBufferBytes(bufferParams.targetBufferBytes)
            .setPrioritizeTimeOverSizeThresholds(bufferParams.prioritizeTimeOverSize)
            .build()

        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            /* maxDurationForQualityDecreaseMs = */ MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            /* minDurationToRetainAfterDiscardMs = */ MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
            /* bandwidthFraction = */ BANDWIDTH_FRACTION,
        )
        val trackSelector = DefaultTrackSelector(context, adaptiveTrackSelectionFactory).apply {
            parameters = buildUponParameters()
                .setExceedVideoConstraintsIfNecessary(false)
                .setExceedRendererCapabilitiesIfNecessary(false)
                .build()
        }
        this.trackSelector = trackSelector

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        dataSourceFactory = createDataSourceFactory()

        val mediaSourceFactory = createMediaSourceFactory(dataSourceFactory!!)

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .build()
        exoPlayer = player
        bindCallbackSession(player)

        val mediaItem = buildMediaItem(streamUrl, subtitles)
        setMediaSource(player, mediaItem, streamUrl)

        player.prepare()
        if (startPosition != null) {
            if (startPosition > 0) {
                player.seekTo(startPosition)
            }
            player.playWhenReady = true
        }
        notifyPlaybackState()
    }

    @OptIn(UnstableApi::class)
    private fun createMediaSourceFactory(
        dataSourceFactory: DataSource.Factory,
    ): DefaultMediaSourceFactory {
        return DefaultMediaSourceFactory(
            dataSourceFactory,
            DefaultExtractorsFactory().setDisableArtworkMetadata(
                playerPreferencesRepository.discardEmbeddedArtworkMetadata,
            ),
        ).setExperimentalEnableHagcPlayback(playerPreferencesRepository.hagcPlaybackEnabled)
    }

    override fun switchStream(streamUrl: String, subtitles: List<SubtitleLink>?) {
        val player = exoPlayer ?: return
        bindCallbackSession(player)
        val engine = ExoPlayerPlaybackEngine(player)
        PlaybackTransitions.switchStream(
            engine = engine,
            streamUrl = streamUrl,
            subtitles = subtitles,
        )
        notifyPlaybackState()
    }

    private fun bindCallbackSession(player: ExoPlayer) {
        val session = callbackGate.beginSession()
        val listener = createPlayerListener(session)
        playerListener?.let(player::removeListener)
        player.addListener(listener)
        callbackSession = session
        playerListener = listener
    }

    override fun play() {
        exoPlayer?.let { player ->
            PlaybackTransitions.play(ExoPlayerPlaybackEngine(player))
            notifyPlaybackState()
        }
    }

    override fun pause() {
        exoPlayer?.let { player ->
            player.pause()
            notifyPlaybackState()
        }
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    override fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    override fun selectAudioTrack(groupIndex: Int) {
        val player = exoPlayer ?: return
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val targetGroup = audioGroups.getOrNull(groupIndex) ?: return

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(targetGroup.mediaTrackGroup, 0)
            )
            .build()
    }

    override fun selectSubtitle(track: SubtitleTrackUIState?) {
        val player = exoPlayer ?: return
        if (track == null || track.isOff) {
            pendingSubtitleTrack = null
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }

        pendingSubtitleTrack = track
        applySubtitleTrackSelection(track)
    }

    override fun release() {
        callbackGate.invalidate()
        callbackSession = null
        playerListener?.let { exoPlayer?.removeListener(it) }
        playerListener = null
        exoPlayer?.release()
        exoPlayer = null
        trackSelector = null
        dataSourceFactory = null
        pendingSubtitleTrack = null
    }

    @OptIn(UnstableApi::class)
    private fun disableAc3AndRetry(positionMs: Long) {
        val player = exoPlayer ?: return
        val selector = trackSelector ?: return

        player.stop()

        selector.parameters = selector.parameters.buildUpon()
            .setExceedRendererCapabilitiesIfNecessary(false)
            .setExceedAudioConstraintsIfNecessary(false)
            .build()

        player.seekTo(positionMs)
        player.prepare()
        player.playWhenReady = true
    }

    private fun buildMediaItem(streamUrl: String, subtitles: List<SubtitleLink>?): MediaItem {
        val builder = MediaItem.Builder().setUri(streamUrl)
        if (streamUrl.isHlsStreamUrl()) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        // Every external subtitle is attached, including ones an HLS manifest may also
        // publish as a rendition: the manifest contents are unknown until the source is
        // prepared, and a rendition that turns out to duplicate an API entry is dropped
        // from the picker afterwards by SubtitleTrackMerger.
        val subtitleConfigs = subtitles.orEmpty().mapNotNull { sub ->
            if (!sub.shouldSideLoad) return@mapNotNull null
            val subtitleUrl = sub.url
            val stableKey = subtitleUrl.stableSubtitleKey()
            MediaItem.SubtitleConfiguration.Builder(subtitleUrl.toUri())
                .setMimeType(subtitleMimeType(subtitleUrl))
                .setLanguage(sub.lang)
                .setLabel(stableKey)
                .setId(stableKey)
                .build()
        }
        if (subtitleConfigs.isNotEmpty()) {
            builder.setSubtitleConfigurations(subtitleConfigs)
        }
        return builder.build()
    }

    private fun subtitleMimeType(url: String): String {
        val normalizedUrl = url
            .substringBefore('?')
            .substringBefore('#')
            .lowercase(Locale.ROOT)
        return when {
            normalizedUrl.endsWith(".vtt") || normalizedUrl.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
            normalizedUrl.endsWith(".ass") || normalizedUrl.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            normalizedUrl.endsWith(".ttml") || normalizedUrl.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    @OptIn(UnstableApi::class)
    private fun createDataSourceFactory(): DataSource.Factory {
        val builder = okHttpClient.newBuilder()
            .connectTimeout(PLAYER_NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(PLAYER_NETWORK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        if (useFastDns) {
            builder.dns(okhttp3.Dns.SYSTEM)
        }
        val playerClient = builder.build()
        val httpFactory = OkHttpDataSource.Factory(playerClient)
            .setUserAgent("Puber/${BuildConfig.VERSION_NAME} (Android)")
        return CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @OptIn(UnstableApi::class)
    private fun setMediaSource(player: ExoPlayer, mediaItem: MediaItem, streamUrl: String) {
        val dsFactory = dataSourceFactory ?: return
        if (!streamUrl.isHlsStreamUrl()) {
            // DefaultMediaSourceFactory side-loads the subtitle configurations itself.
            player.setMediaItem(mediaItem)
            return
        }
        val hlsSource = HlsMediaSource.Factory(dsFactory)
            .setAllowChunklessPreparation(true)
            .setLoadErrorHandlingPolicy(HlsErrorPolicy())
            .createMediaSource(mediaItem)
        player.setMediaSource(withSideLoadedSubtitles(hlsSource, mediaItem, dsFactory))
    }

    // HlsMediaSource.Factory ignores MediaItem subtitle configurations, so an API subtitle
    // missing from the manifest would otherwise be unreachable on an HLS stream.
    @OptIn(UnstableApi::class)
    private fun withSideLoadedSubtitles(
        source: MediaSource,
        mediaItem: MediaItem,
        dsFactory: DataSource.Factory,
    ): MediaSource {
        val subtitleConfigs = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()
        if (subtitleConfigs.isEmpty()) return source
        val subtitleSources = subtitleConfigs.map { config ->
            SingleSampleMediaSource.Factory(dsFactory)
                .setLoadErrorHandlingPolicy(HlsErrorPolicy())
                .createMediaSource(config, C.TIME_UNSET)
        }
        return MergingMediaSource(source, *subtitleSources.toTypedArray())
    }

    data class DebugInfo(
        val videoResolution: String,
        val videoCodec: String,
        val videoBitrate: String,
        val audioCodec: String,
        val audioChannels: String,
        val droppedFrames: String,
        val bufferedDuration: String,
    )

    @OptIn(UnstableApi::class)
    fun getDebugInfo(): DebugInfo? {
        val player = exoPlayer ?: return null
        val videoFormat = player.videoFormat
        val audioFormat = player.audioFormat

        val decoderCounters = player.videoDecoderCounters
        val dropped = decoderCounters?.droppedBufferCount ?: 0

        val bufferedMs = player.bufferedPosition - player.currentPosition
        val bufferedSec = (bufferedMs / 1000.0).coerceAtLeast(0.0)

        return DebugInfo(
            videoResolution = videoFormat?.let { "${it.width}x${it.height}" } ?: "—",
            videoCodec = videoFormat?.codecs ?: videoFormat?.sampleMimeType?.substringAfter("/") ?: "—",
            videoBitrate = if (videoFormat?.bitrate != null && videoFormat.bitrate > 0) {
                "%.1f Mbps".format(videoFormat.bitrate / BITS_PER_MEGABIT)
            } else {
                "—"
            },
            audioCodec = audioFormat?.codecs ?: audioFormat?.sampleMimeType?.substringAfter("/") ?: "—",
            audioChannels = when (audioFormat?.channelCount) {
                1 -> "mono"
                2 -> "stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> audioFormat?.channelCount?.toString() ?: "—"
            },
            droppedFrames = dropped.toString(),
            bufferedDuration = "%.1fs".format(bufferedSec),
        )
    }

    private fun playbackSnapshot(): PlaybackSnapshot {
        val player = exoPlayer ?: return PlaybackStatePolicy.derive(
            isPlaying = false,
            playWhenReady = false,
            playbackState = Player.STATE_IDLE,
            suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        )
        return playbackSnapshot(player)
    }

    private fun playbackSnapshot(player: ExoPlayer): PlaybackSnapshot {
        return PlaybackStatePolicy.derive(
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            suppressionReason = player.playbackSuppressionReason,
            position = player.currentPosition,
            duration = player.duration.coerceAtLeast(0),
            buffered = player.bufferedPosition,
        )
    }

    private fun notifyPlaybackState() {
        callbackSession?.let(::notifyPlaybackState)
    }

    private fun notifyPlaybackState(session: PlaybackCallbackGate.Session) {
        callbackGate.dispatch(session) { callback ->
            val player = exoPlayer ?: return@dispatch
            PlaybackTransitions.dispatchPlaybackSnapshot(
                engine = ExoPlayerPlaybackEngine(player),
                sink = callback,
            )
        }
    }

    private inner class ExoPlayerPlaybackEngine(
        private val player: ExoPlayer,
    ) : PlaybackEnginePort {
        private var pendingTrackSelectionParameters: TrackSelectionParameters? = null

        override val isPlaying: Boolean
            get() = player.isPlaying
        override val playWhenReady: Boolean
            get() = player.playWhenReady
        override val playbackState: Int
            get() = player.playbackState
        override val playbackSuppressionReason: Int
            get() = player.playbackSuppressionReason
        override val currentPosition: Long
            get() = player.currentPosition
        override val duration: Long
            get() = player.duration.coerceAtLeast(0)
        override val bufferedPosition: Long
            get() = player.bufferedPosition
        override var trackSelectionParameters: Any
            get() = player.trackSelectionParameters
            set(value) {
                pendingTrackSelectionParameters = value as? TrackSelectionParameters
            }

        override fun stop() {
            player.stop()
        }

        override fun setMediaSource(streamUrl: String, subtitles: List<SubtitleLink>?) {
            setMediaSource(player, buildMediaItem(streamUrl, subtitles), streamUrl)
        }

        override fun restoreTrackSelection() {
            pendingTrackSelectionParameters?.let { player.trackSelectionParameters = it }
            pendingTrackSelectionParameters = null
        }

        override fun prepare() {
            player.prepare()
        }

        override fun seekTo(positionMs: Long) {
            player.seekTo(positionMs)
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            player.playWhenReady = playWhenReady
        }

        override fun seekToDefaultPosition() {
            player.seekToDefaultPosition()
        }

        override fun play() {
            player.play()
        }
    }

    private fun notifyTracksUpdated(callback: PlaybackControl.Callback?) {
        val player = exoPlayer ?: return
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (audioGroups.isEmpty() && textGroups.isEmpty()) return
        val audioTracks = audioGroups.mapIndexed { index, group ->
            val format = group.getTrackFormat(0)
            val label = format.label ?: format.language ?: "Track ${index + 1}"
            AudioTrackUIState(
                index = index,
                label = label,
                language = format.language ?: "",
            )
        }
        val selectedIndex = audioGroups.indexOfFirst { it.isSelected }.coerceAtLeast(0)
        val hlsSubtitles = (player.currentManifest as? HlsManifest)
            ?.multivariantPlaylist
            ?.subtitles
            .orEmpty()
        callback?.onTracksUpdated(
            audioTracks,
            selectedIndex,
            buildSubtitleTracks(textGroups, hlsSubtitles),
        )
    }

    private fun buildSubtitleTracks(
        textGroups: List<Tracks.Group>,
        hlsSubtitles: List<HlsMultivariantPlaylist.Rendition>,
    ): List<SubtitleTrackUIState> {
        var subtitleIndex = 0
        val textTrackCount = textGroups.sumOf { it.length }
        return textGroups.flatMapIndexed { groupIndex, group ->
            (0 until group.length).map { trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                subtitleIndex += 1
                buildSubtitleTrack(
                    index = subtitleIndex,
                    format = format,
                    hlsRendition = findHlsRendition(
                        format = format,
                        renditionIndex = subtitleIndex - 1,
                        textTrackCount = textTrackCount,
                        hlsSubtitles = hlsSubtitles,
                    ),
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    groupId = group.mediaTrackGroup.id,
                )
            }
        }
    }

    private fun findHlsRendition(
        format: Format,
        renditionIndex: Int,
        textTrackCount: Int,
        hlsSubtitles: List<HlsMultivariantPlaylist.Rendition>,
    ): HlsMultivariantPlaylist.Rendition? {
        return hlsSubtitles.filter { rendition ->
            format.id != null && rendition.format.id == format.id
        }.singleOrNull() ?: hlsSubtitles.filter { rendition ->
            rendition.format.label == format.label &&
                sameSubtitleLanguage(
                    rendition.format.language.orEmpty(),
                    format.language.orEmpty(),
                )
        }.singleOrNull() ?: hlsSubtitles.getOrNull(renditionIndex)
            .takeIf { hlsSubtitles.size == textTrackCount }
    }

    private fun buildSubtitleTrack(
        index: Int,
        format: Format,
        hlsRendition: HlsMultivariantPlaylist.Rendition?,
        groupIndex: Int,
        trackIndex: Int,
        groupId: String,
    ): SubtitleTrackUIState {
        val identityFormat = hlsRendition?.format
        val language = format.language ?: identityFormat?.language.orEmpty()
        // SubtitleLabeler builds every visible label once the full track list is known.
        return SubtitleTrackUIState(
            index = index,
            label = "",
            language = language,
            url = "",
            descriptiveLabel = format.label ?: identityFormat?.label,
            isForced = (format.selectionFlags or (identityFormat?.selectionFlags ?: 0)) and
                C.SELECTION_FLAG_FORCED != 0,
            playerTrackId = format.id ?: identityFormat?.id,
            playerTrackGroupId = groupId,
            playerTrackUri = hlsRendition?.url?.toString(),
            playerGroupIndex = groupIndex,
            playerTrackIndex = trackIndex,
        )
    }

    private fun applyPendingSubtitleSelection() {
        pendingSubtitleTrack?.let(::applySubtitleTrackSelection)
    }

    private fun applySubtitleTrackSelection(track: SubtitleTrackUIState) {
        val player = exoPlayer ?: return
        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val target = subtitleTrackSelector.select(track, textGroups.toPlayerTextTracks())
            ?: return
        val targetGroup = textGroups.getOrNull(target.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                TrackSelectionOverride(targetGroup.mediaTrackGroup, target.trackIndex),
            )
            .build()
    }

    private fun List<Tracks.Group>.toPlayerTextTracks(): List<PlayerTextTrack> =
        flatMapIndexed { groupIndex, group ->
            (0 until group.length).map { trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                PlayerTextTrack(
                    groupId = group.mediaTrackGroup.id,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    formatId = format.id,
                    formatLabel = format.label,
                    language = format.language,
                )
            }
        }

    private companion object {
        const val MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10_000
        const val MAX_DURATION_FOR_QUALITY_DECREASE_MS = 15_000
        const val MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 25_000
        const val BANDWIDTH_FRACTION = 0.75f
        const val PLAYER_NETWORK_TIMEOUT_SECONDS = 20L
        const val BITS_PER_MEGABIT = 1_000_000.0
    }
}

// Matches only the URL path: a host such as "hls.cdn.example" or a query
// parameter must not turn a progressive stream into an HLS one, because the
// answer also decides whether API subtitles are side-loaded.
internal fun String.isHlsStreamUrl(): Boolean {
    val path = runCatching { URI(this).path }.getOrNull()
        ?: substringBefore('?').substringBefore('#').substringAfter("://").substringAfter('/', "")
    if (path.isEmpty()) return false
    if (path.endsWith(M3U8_EXTENSION, ignoreCase = true)) return true
    return path.split('/').any { segment -> HLS_PATH_SEGMENT.matches(segment) }
}

private const val M3U8_EXTENSION = ".m3u8"
private val HLS_PATH_SEGMENT = Regex("""hls\d*""", RegexOption.IGNORE_CASE)

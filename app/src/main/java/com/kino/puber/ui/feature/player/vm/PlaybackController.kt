package com.kino.puber.ui.feature.player.vm

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.BehindLiveWindowException
import okhttp3.OkHttpClient
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kino.puber.BuildConfig
import com.kino.puber.R
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.BufferPreset
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import java.util.Locale

internal interface PlaybackControl {
    interface Callback {
        fun onPlaybackStateChanged(
            isPlaying: Boolean,
            isBuffering: Boolean,
            position: Long,
            duration: Long,
            buffered: Long,
        )

        fun onTracksUpdated(audioTracks: List<AudioTrackUIState>, selectedIndex: Int)
        fun onPlaybackEnded()
        fun onError(message: String)
    }

    val currentPosition: Long
    val duration: Long
    val isPlaying: Boolean
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
) : PlaybackControl {

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var callback: PlaybackControl.Callback? = null
    private var ac3FallbackApplied = false
    private var useFastDns = true
    private var pendingSubtitleTrack: SubtitleTrackUIState? = null

    @OptIn(UnstableApi::class)
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
    private var dataSourceFactory: DataSource.Factory? = null

    val player: ExoPlayer? get() = exoPlayer
    override val currentPosition: Long get() = exoPlayer?.currentPosition ?: 0L
    override val duration: Long get() = exoPlayer?.duration?.coerceAtLeast(0) ?: 0L
    override val isPlaying: Boolean get() = exoPlayer?.isPlaying == true
    override val bufferedPosition: Long get() = exoPlayer?.bufferedPosition ?: 0L
    
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            notifyPlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> callback?.onPlaybackEnded()
                Player.STATE_READY -> {
                    notifyPlaybackState()
                    notifyTracksUpdated()
                }
                Player.STATE_BUFFERING -> notifyPlaybackState()
                else -> {}
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            notifyTracksUpdated()
            applyPendingSubtitleSelection()
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            when {
                cause is BehindLiveWindowException -> recoverBehindLiveWindow()
                cause.isAc3DecoderInitializationException() -> disableAc3AndRetry()
                else -> callback?.onError(
                    error.localizedMessage ?: context.getString(R.string.player_error_playback)
                )
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
        this.callback = callback
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
        ac3FallbackApplied = false
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

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory!!))
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .build()
            .apply { addListener(playerListener) }
        exoPlayer = player

        val mediaItem = buildMediaItem(streamUrl, subtitles)
        setMediaSource(player, mediaItem, streamUrl)

        player.prepare()
        if (startPosition != null) {
            if (startPosition > 0) {
                player.seekTo(startPosition)
            }
            player.playWhenReady = true
        }
    }

    override fun switchStream(streamUrl: String, subtitles: List<SubtitleLink>?) {
        val player = exoPlayer ?: return
        val savedPosition = player.currentPosition
        val wasPlaying = player.isPlaying
        val savedTrackParams = player.trackSelectionParameters

        player.stop()

        val mediaItem = buildMediaItem(streamUrl, subtitles)
        setMediaSource(player, mediaItem, streamUrl)

        player.trackSelectionParameters = savedTrackParams
        player.prepare()
        player.seekTo(savedPosition)
        player.playWhenReady = wasPlaying
    }

    override fun play() {
        exoPlayer?.play()
    }

    override fun pause() {
        exoPlayer?.pause()
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
        if (track == null || track.url.isEmpty()) {
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
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        trackSelector = null
        dataSourceFactory = null
    }

    @OptIn(UnstableApi::class)
    private fun disableAc3AndRetry() {
        if (ac3FallbackApplied) {
            callback?.onError(context.getString(R.string.player_error_playback))
            return
        }
        ac3FallbackApplied = true

        val player = exoPlayer ?: return
        val selector = trackSelector ?: return
        val position = player.currentPosition

        player.stop()

        selector.parameters = selector.parameters.buildUpon()
            .setExceedRendererCapabilitiesIfNecessary(false)
            .setExceedAudioConstraintsIfNecessary(false)
            .build()

        player.seekTo(position)
        player.prepare()
        player.playWhenReady = true
    }

    private fun buildMediaItem(streamUrl: String, subtitles: List<SubtitleLink>?): MediaItem {
        val builder = MediaItem.Builder().setUri(streamUrl)
        if (!subtitles.isNullOrEmpty()) {
            val subtitleConfigs = subtitles.map { sub ->
                val stableKey = sub.url.stableSubtitleKey()
                MediaItem.SubtitleConfiguration.Builder(sub.url.toUri())
                    .setMimeType(subtitleMimeType(sub.url))
                    .setLanguage(sub.lang)
                    .setLabel(stableKey)
                    .setId(stableKey)
                    .build()
            }
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
        if (streamUrl.contains(".m3u8") || streamUrl.contains("hls")) {
            val hlsSource = HlsMediaSource.Factory(dsFactory)
                .setAllowChunklessPreparation(true)
                .setLoadErrorHandlingPolicy(HlsErrorPolicy())
                .createMediaSource(mediaItem)
            player.setMediaSource(hlsSource)
        } else {
            player.setMediaItem(mediaItem)
        }
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

    private fun notifyPlaybackState() {
        val player = exoPlayer ?: return
        callback?.onPlaybackStateChanged(
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            position = player.currentPosition,
            duration = player.duration.coerceAtLeast(0),
            buffered = player.bufferedPosition,
        )
    }

    private fun notifyTracksUpdated() {
        val player = exoPlayer ?: return
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isEmpty()) return

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
        callback?.onTracksUpdated(audioTracks, selectedIndex)
    }

    private fun applyPendingSubtitleSelection() {
        pendingSubtitleTrack?.let(::applySubtitleTrackSelection)
    }

    private fun applySubtitleTrackSelection(track: SubtitleTrackUIState) {
        val player = exoPlayer ?: return
        val stableKey = track.url.stableSubtitleKey()
        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val target = findTextTrack(track, stableKey, textGroups)
        val builder = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(track.language)
        if (target != null) {
            builder.setOverrideForType(
                TrackSelectionOverride(target.group.mediaTrackGroup, target.trackIndex),
            )
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun findTextTrack(
        track: SubtitleTrackUIState,
        stableKey: String,
        textGroups: List<Tracks.Group>,
    ): TextTrackSelection? {
        return findTextTrackBy(textGroups) { format ->
            format.id == track.url
        } ?: findTextTrackBy(textGroups) { format ->
            format.id == stableKey || format.label == stableKey
        } ?: findTextTrackBySubtitleIndex(textGroups, track.index)
        ?: findUnambiguousTextTrackByLanguage(textGroups, track.language)
    }

    // Media3 may not expose SubtitleConfiguration id/label for every source type.
    // The current track list still preserves the subtitle configuration order.
    private fun findTextTrackBySubtitleIndex(
        textGroups: List<Tracks.Group>,
        subtitleIndex: Int,
    ): TextTrackSelection? {
        val targetIndex = subtitleIndex - 1
        if (targetIndex < 0) return null
        return textGroups
            .flatMap { group ->
                (0 until group.length).map { trackIndex ->
                    TextTrackSelection(group = group, trackIndex = trackIndex)
                }
            }
            .getOrNull(targetIndex)
    }

    private fun findUnambiguousTextTrackByLanguage(
        textGroups: List<Tracks.Group>,
        language: String,
    ): TextTrackSelection? {
        if (language.isEmpty()) return null
        val matches = textGroups.flatMap { group ->
            (0 until group.length).mapNotNull { trackIndex ->
                group.getTrackFormat(trackIndex).takeIf { format ->
                    format.language == language
                }?.let {
                    TextTrackSelection(group = group, trackIndex = trackIndex)
                }
            }
        }
        return matches.singleOrNull()
    }

    private fun findTextTrackBy(
        textGroups: List<Tracks.Group>,
        predicate: (Format) -> Boolean,
    ): TextTrackSelection? {
        return textGroups.firstNotNullOfOrNull { group ->
            (0 until group.length).firstNotNullOfOrNull { trackIndex ->
                group.getTrackFormat(trackIndex).takeIf(predicate)?.let {
                    TextTrackSelection(group = group, trackIndex = trackIndex)
                }
            }
        }
    }

    private data class TextTrackSelection(
        val group: Tracks.Group,
        val trackIndex: Int,
    )

    private companion object {
        const val MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10_000
        const val MAX_DURATION_FOR_QUALITY_DECREASE_MS = 15_000
        const val MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 25_000
        const val BANDWIDTH_FRACTION = 0.75f
        const val PLAYER_NETWORK_TIMEOUT_SECONDS = 20L
        const val BITS_PER_MEGABIT = 1_000_000.0
    }
}

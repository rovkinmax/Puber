package com.kino.puber.ui.feature.player.vm

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.kino.puber.R
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.ui.feature.player.model.AudioTrackUIState

internal interface PlaybackControl {
    interface Callback {
        fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean, position: Long, duration: Long, buffered: Long)
        fun onTracksUpdated(audioTracks: List<AudioTrackUIState>, selectedIndex: Int)
        fun onPlaybackEnded()
        fun onError(message: String)
    }

    val currentPosition: Long
    val duration: Long
    val isPlaying: Boolean
    val bufferedPosition: Long

    fun setCallback(callback: Callback)
    fun prepare(streamUrl: String, subtitles: List<SubtitleLink>?, startPosition: Long?)
    fun switchStream(streamUrl: String, subtitles: List<SubtitleLink>?)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun selectAudioTrack(groupIndex: Int)
    fun selectSubtitle(index: Int)
    fun release()
}

internal class PlaybackController(private val context: Context) : PlaybackControl {

    private var exoPlayer: ExoPlayer? = null
    private var callback: PlaybackControl.Callback? = null

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

        override fun onPlayerError(error: PlaybackException) {
            callback?.onError(error.localizedMessage ?: context.getString(R.string.player_error_playback))
        }
    }

    override fun setCallback(callback: PlaybackControl.Callback) {
        this.callback = callback
    }

    @OptIn(UnstableApi::class)
    override fun prepare(streamUrl: String, subtitles: List<SubtitleLink>?, startPosition: Long?) {
        release()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 5_000,
                /* bufferForPlaybackAfterRebufferMs = */ 10_000,
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 30_000,
                /* retainBackBufferFromKeyframe = */ true,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(1_500_000L)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setExceedVideoConstraintsIfNecessary(true)
                .setExceedRendererCapabilitiesIfNecessary(true)
                .build()
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setTrackSelector(trackSelector)
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

    override fun selectSubtitle(index: Int) {
        val player = exoPlayer ?: return
        if (index == 0) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            val targetGroup = textGroups.getOrNull(index - 1)

            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .apply {
                    if (targetGroup != null) {
                        setOverrideForType(
                            TrackSelectionOverride(targetGroup.mediaTrackGroup, 0)
                        )
                    }
                }
                .build()
        }
    }

    override fun release() {
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun buildMediaItem(streamUrl: String, subtitles: List<SubtitleLink>?): MediaItem {
        val builder = MediaItem.Builder().setUri(streamUrl)
        if (!subtitles.isNullOrEmpty()) {
            val subtitleConfigs = subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(sub.url.toUri())
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(sub.lang)
                    .setLabel(sub.lang)
                    .build()
            }
            builder.setSubtitleConfigurations(subtitleConfigs)
        }
        return builder.build()
    }

    @OptIn(UnstableApi::class)
    private fun setMediaSource(player: ExoPlayer, mediaItem: MediaItem, streamUrl: String) {
        if (streamUrl.contains(".m3u8") || streamUrl.contains("hls")) {
            val dataSourceFactory = DefaultDataSource.Factory(context)
            val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
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
            videoResolution = if (videoFormat != null) "${videoFormat.width}x${videoFormat.height}" else "—",
            videoCodec = videoFormat?.codecs ?: videoFormat?.sampleMimeType?.substringAfter("/") ?: "—",
            videoBitrate = if (videoFormat?.bitrate != null && videoFormat.bitrate > 0) {
                "%.1f Mbps".format(videoFormat.bitrate / 1_000_000.0)
            } else "—",
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
}
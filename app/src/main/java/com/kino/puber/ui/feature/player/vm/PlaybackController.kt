package com.kino.puber.ui.feature.player.vm

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.kino.puber.R
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.ui.feature.player.model.AudioTrackUIState

internal class PlaybackController(private val context: Context) {

    interface Callback {
        fun onPlaybackStateChanged(isPlaying: Boolean, position: Long, duration: Long, buffered: Long)
        fun onTracksUpdated(audioTracks: List<AudioTrackUIState>, selectedIndex: Int)
        fun onPlaybackEnded()
        fun onError(message: String)
    }

    private var exoPlayer: ExoPlayer? = null
    private var callback: Callback? = null

    val player: ExoPlayer? get() = exoPlayer
    val currentPosition: Long get() = exoPlayer?.currentPosition ?: 0L
    val duration: Long get() = exoPlayer?.duration?.coerceAtLeast(0) ?: 0L
    val isPlaying: Boolean get() = exoPlayer?.isPlaying == true
    val bufferedPosition: Long get() = exoPlayer?.bufferedPosition ?: 0L
    
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

    fun setCallback(callback: Callback) {
        this.callback = callback
    }

    fun prepare(streamUrl: String, subtitles: List<SubtitleLink>?, startPosition: Long?) {
        release()

        val player = ExoPlayer.Builder(context).build().apply {
            addListener(playerListener)
        }
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

    fun switchStream(streamUrl: String, subtitles: List<SubtitleLink>?) {
        val player = exoPlayer ?: return
        val savedPosition = player.currentPosition
        val wasPlaying = player.isPlaying

        player.stop()

        val mediaItem = buildMediaItem(streamUrl, subtitles)
        setMediaSource(player, mediaItem, streamUrl)

        player.prepare()
        player.seekTo(savedPosition)
        player.playWhenReady = wasPlaying
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun selectAudioTrack(groupIndex: Int) {
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

    fun selectSubtitle(index: Int) {
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

    fun release() {
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        callback = null
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
                .createMediaSource(mediaItem)
            player.setMediaSource(hlsSource)
        } else {
            player.setMediaItem(mediaItem)
        }
    }

    private fun notifyPlaybackState() {
        val player = exoPlayer ?: return
        callback?.onPlaybackStateChanged(
            isPlaying = player.isPlaying,
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
package teikengstudio.xmpro.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import java.util.IdentityHashMap

/** long: 单集播放器通过业务队列切集，向通知、锁屏和耳机提供同一组标准播放命令。 */
internal class EpisodeQueuePlayer(player: Player) : ForwardingPlayer(player) {
    private val listeners = IdentityHashMap<Player.Listener, Player.Listener>()

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().withEpisodeCommands()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun addListener(listener: Player.Listener) {
        super.addListener(listeners.getOrPut(listener) { EpisodeListener(listener) })
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)?.let { super.removeListener(it) }
    }

    // long: 标准跳曲动作转给完整剧集队列，避免单集 ExoPlayer 把切集命令当成无操作。
    override fun seekToPrevious() = PlaybackQueueBridge.request(PlaybackQueueBridge.Command.Previous)

    override fun seekToPreviousMediaItem() = seekToPrevious()

    override fun seekToNext() = PlaybackQueueBridge.request(PlaybackQueueBridge.Command.Next)

    override fun seekToNextMediaItem() = seekToNext()

    private fun Player.Commands.withEpisodeCommands(): Player.Commands = buildUpon()
        // long: 队列边界由 MainViewModel 处理；始终提供标准槽位，确保锁屏固定显示三个按钮。
        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_NEXT)
        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        .build()

    // long: Java 默认回调不会由 Kotlin 的 by 自动转发；保留全部事件，确保通知、进度及自动续播同步更新。
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private inner class EpisodeListener(private val listener: Player.Listener) : Player.Listener {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
            // long: 换集时也报告同一组标准能力，防止控制器因单集播放器的原始命令更新而禁用切集。
            listener.onAvailableCommandsChanged(availableCommands.withEpisodeCommands())

        override fun onEvents(player: Player, events: Player.Events) = listener.onEvents(player, events)
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = listener.onTimelineChanged(timeline, reason)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = listener.onMediaItemTransition(mediaItem, reason)
        override fun onTracksChanged(tracks: Tracks) = listener.onTracksChanged(tracks)
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = listener.onMediaMetadataChanged(mediaMetadata)
        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) = listener.onPlaylistMetadataChanged(mediaMetadata)
        override fun onIsLoadingChanged(isLoading: Boolean) = listener.onIsLoadingChanged(isLoading)
        override fun onLoadingChanged(isLoading: Boolean) = listener.onLoadingChanged(isLoading)
        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = listener.onTrackSelectionParametersChanged(parameters)
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) = listener.onPlayerStateChanged(playWhenReady, playbackState)
        override fun onPlaybackStateChanged(playbackState: Int) = listener.onPlaybackStateChanged(playbackState)
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = listener.onPlayWhenReadyChanged(playWhenReady, reason)
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = listener.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)
        override fun onRepeatModeChanged(repeatMode: Int) = listener.onRepeatModeChanged(repeatMode)
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = listener.onShuffleModeEnabledChanged(shuffleModeEnabled)
        override fun onPlayerError(error: PlaybackException) = listener.onPlayerError(error)
        override fun onPlayerErrorChanged(error: PlaybackException?) = listener.onPlayerErrorChanged(error)
        override fun onPositionDiscontinuity(reason: Int) = listener.onPositionDiscontinuity(reason)
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) =
            listener.onPositionDiscontinuity(oldPosition, newPosition, reason)
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = listener.onPlaybackParametersChanged(playbackParameters)
        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) = listener.onSeekBackIncrementChanged(seekBackIncrementMs)
        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) = listener.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) = listener.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        override fun onAudioSessionIdChanged(audioSessionId: Int) = listener.onAudioSessionIdChanged(audioSessionId)
        override fun onAudioAttributesChanged(audioAttributes: AudioAttributes) = listener.onAudioAttributesChanged(audioAttributes)
        override fun onVolumeChanged(volume: Float) = listener.onVolumeChanged(volume)
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = listener.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) = listener.onDeviceInfoChanged(deviceInfo)
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = listener.onDeviceVolumeChanged(volume, muted)
        override fun onVideoSizeChanged(videoSize: VideoSize) = listener.onVideoSizeChanged(videoSize)
        override fun onSurfaceSizeChanged(width: Int, height: Int) = listener.onSurfaceSizeChanged(width, height)
        override fun onRenderedFirstFrame() = listener.onRenderedFirstFrame()
        override fun onCues(cues: List<Cue>) = listener.onCues(cues)
        override fun onCues(cueGroup: CueGroup) = listener.onCues(cueGroup)
        override fun onMetadata(metadata: Metadata) = listener.onMetadata(metadata)
    }
}

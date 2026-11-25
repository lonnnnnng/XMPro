package teikengstudio.xmpro.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.analytics.AnalyticsListener
import teikengstudio.xmpro.MainActivity

/**
 * Hosts the single ExoPlayer instance for the process so playback keeps running
 * in the background and keeps a MediaSession for notifications and lockscreen.
 */
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    private val effects = AudioEffects()
    private val effectScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var audioSessionId = 0

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setRenderersFactory(SmartSkip.renderersFactory(this))
            .build()
            .apply {
                // Without a declared focus request the app played over every
                // other sound and never took part in the system's focus rules:
                // a call or a voice message left the stream in an undefined
                // state instead of pausing and resuming it.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                // Long listens happen with the screen off; without this the
                // network radio is allowed to sleep mid-episode and the stream
                // simply stops where the buffer ran out.
                setWakeMode(C.WAKE_MODE_NETWORK)
                setHandleAudioBecomingNoisy(true)
            }
        var lastSnapshot: String? = null
        exo.addListener(object : Player.Listener {
            private fun recordSnapshot() {
                val snapshot = listOf(
                    exo.playbackState,
                    exo.playWhenReady,
                    exo.isPlaying,
                    exo.playbackSuppressionReason,
                ).joinToString(":")
                if (snapshot == lastSnapshot) return
                lastSnapshot = snapshot
                PlaybackDiagnostics.record(
                    this@PlaybackService,
                    "service_player_state",
                    "state" to PlaybackDiagnostics.playerState(exo.playbackState),
                    "play_when_ready" to exo.playWhenReady,
                    "is_playing" to exo.isPlaying,
                    "suppression" to exo.playbackSuppressionReason,
                    "position_ms" to exo.currentPosition.coerceAtLeast(0),
                    "buffered_ms" to exo.bufferedPosition.coerceAtLeast(0),
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) = recordSnapshot()

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                PlaybackDiagnostics.record(
                    this@PlaybackService,
                    "service_play_when_ready_changed",
                    "play_when_ready" to playWhenReady,
                    "reason" to reason,
                )
                recordSnapshot()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = recordSnapshot()

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = recordSnapshot()

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                PlaybackDiagnostics.record(
                    this@PlaybackService,
                    "service_media_item_transition",
                    "has_media_item" to (mediaItem != null),
                    "reason" to reason,
                )
                recordSnapshot()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                PlaybackDiagnostics.record(
                    this@PlaybackService,
                    "service_player_error",
                    "error_code" to error.errorCode,
                    "error_name" to error.errorCodeName,
                    "cause_types" to PlaybackDiagnostics.causeTypes(error),
                    "http_status" to PlaybackDiagnostics.httpStatus(error),
                )
            }
        })
        PlaybackDiagnostics.record(this, "service_created")
        // The session id only exists once an audio renderer is attached, and it
        // changes when the output device does; both cases re-apply the preset.
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                this@PlaybackService.audioSessionId = audioSessionId
                effects.apply(audioSessionId, EffectBus.state.value)
            }
        })
        effectScope.launch {
            EffectBus.state.collect { state -> effects.apply(audioSessionId, state) }
        }
        player = exo
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // long: 向系统提供标准切集能力，由系统统一生成上一集、播放/暂停、下一集三个按钮。
        session = MediaSession.Builder(this, EpisodeQueuePlayer(exo))
            // long: 系统媒体卡片需要明确的 Activity 入口，点击卡片才能返回正在播放的应用。
            .setSessionActivity(sessionActivity)
            // The default loader already fetches https artwork, which is what the
            // notification and lock screen need.
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val exo = player ?: return
        PlaybackDiagnostics.record(
            this,
            "service_task_removed",
            "play_when_ready" to exo.playWhenReady,
            "item_count" to exo.mediaItemCount,
        )
        if (!exo.playWhenReady || exo.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        val exo = player
        PlaybackDiagnostics.record(
            this,
            "service_destroyed",
            "state" to exo?.let { PlaybackDiagnostics.playerState(it.playbackState) },
            "play_when_ready" to exo?.playWhenReady,
            "is_playing" to exo?.isPlaying,
            "position_ms" to exo?.currentPosition?.coerceAtLeast(0),
        )
        effectScope.cancel()
        effects.release()
        session?.run {
            player.clearMediaItems()
            release()
        }
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }
}

/**
 * Builds the playing item, including the metadata the notification and lock
 * screen display. Without it only the stream URL was known and both surfaces
 * showed an empty title.
 */
internal fun mediaItemFor(
    url: String,
    mediaId: String,
    title: String,
    artist: String,
    artworkUrl: String?,
    durationMs: Long = 0L,
): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setUri(url)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist.ifBlank { null })
            // The catalogue length is known before playback starts; without it
            // the media card has no total to show until the player is prepared,
            // and on some launchers no time at all.
            .setDurationMs(durationMs.takeIf { it > 0 })
            .setArtworkUri(
                artworkUrl
                    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?.let { android.net.Uri.parse(it) },
            )
            .build(),
    )
    .build()

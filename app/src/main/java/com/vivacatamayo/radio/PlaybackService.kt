package com.vivacatamayo.radio

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var userWantsPlayback = false

    private val reconnectRunnable = Runnable {
        if (!userWantsPlayback) return@Runnable
        player.prepare()
        player.play()
    }

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            userWantsPlayback = playWhenReady
            if (!playWhenReady) {
                reconnectAttempt = 0
                handler.removeCallbacks(reconnectRunnable)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) reconnectAttempt = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            if (!userWantsPlayback) return
            reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
            val delayMs = (2500L * reconnectAttempt).coerceAtMost(15000L)
            handler.removeCallbacks(reconnectRunnable)
            handler.postDelayed(reconnectRunnable, delayMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_NETWORK)
            val metadata = MediaMetadata.Builder()
                .setTitle(RadioConfig.STATION_NAME)
                .setArtist(RadioConfig.SLOGAN)
                .setAlbumTitle(RadioConfig.LOCATION)
                .setIsPlayable(true)
                .build()
            setMediaItem(MediaItem.Builder().setUri(RadioConfig.STREAM_URL).setMediaMetadata(metadata).build())
            addListener(listener)
            prepare()
        }
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        mediaSession?.release()
        player.release()
        mediaSession = null
        super.onDestroy()
    }
}

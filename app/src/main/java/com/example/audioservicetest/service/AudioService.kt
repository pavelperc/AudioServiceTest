package com.example.audioservicetest.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import com.example.audioservicetest.R
import com.example.audioservicetest.notification.MyNotificationManager


class AudioService : MediaBrowserServiceCompat() {
    companion object {
        const val TAG = "AudioService"
        const val MY_MEDIA_ROOT_ID = "root_id"
        const val MY_MEDIA_ID = "123"
    }

    private val context get() = applicationContext
    private val audioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val player by lazy { MediaPlayer.create(context, R.raw.sample) }

    private lateinit var mediaSession: MediaSessionCompat

    private lateinit var notificationManager: MyNotificationManager

    private val metadata = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Title from Metadata")
        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Subtitle from Metadata")
        .build()

    private var timer: CountDownTimer? = null
        set(value) {
            field?.cancel()
            field = value
        }

    override fun onCreate() {
        super.onCreate()
        player.isLooping = true
        mediaSession = MediaSessionCompat(context, TAG)

        // setup available actions for media buttons.
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY
                        or PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
        mediaSession.setPlaybackState(stateBuilder.build())

        sessionToken = mediaSession.sessionToken
        mediaSession.setCallback(mediaSessionCallback)

        notificationManager = MyNotificationManager(
            context, mediaSession,
            onStop = { stopPlayer() }
        )
        mediaSession.setMetadata(metadata)

        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.registerReceiver(myNoisyAudioStreamReceiver, intentFilter)

        // active session enables media controls on the lock screen
        mediaSession.isActive = true
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = startPlayer()
        override fun onStop() = stopPlayer()
        override fun onPause() = pausePlayer()

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            // https://developer.android.com/guide/topics/media-apps/mediabuttons#customizing-mediabuttons
            val keyEvent = mediaButtonEvent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            if (keyEvent?.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY && keyEvent.action == KeyEvent.ACTION_DOWN) {
                startPlayer()
                return true
            } else if (keyEvent?.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && keyEvent.action == KeyEvent.ACTION_DOWN) {
                if (player.isPlaying) pausePlayer() else startPlayer()
                return true
            }
            return super.onMediaButtonEvent(mediaButtonEvent)
        }
    }


    private fun startPlayer() {
        // fails if another app is playing music this time
        val result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return
        }
        // expects startForeground call with notification in 5 seconds.
        ContextCompat.startForegroundService(context, Intent(context, AudioService::class.java))

        player.start()
        timer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }

            override fun onFinish() {
            }
        }.start()

        val playbackState = updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)

        val notification = notificationManager.showNotification(playbackState, metadata)

        val filter = IntentFilter()
        filter.addAction(MyNotificationManager.ACTION_PLAY)
        filter.addAction(MyNotificationManager.ACTION_PAUSE)
        filter.addAction(MyNotificationManager.ACTION_CLOSE)
        registerReceiver(notificationManager.broadcastReceiver, filter) // what is it???

        startForeground(MyNotificationManager.NOTIFICATION_ID, notification)
    }

    private fun stopPlayer() {
        Toast.makeText(this, "Stop player", Toast.LENGTH_SHORT).show()
        timer = null
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
        stopSelf()
        player.stop()
        player.prepare()
        player.seekTo(0)
        unregisterReceiver(notificationManager.broadcastReceiver)
        stopForeground(true)
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
    }

    private fun pausePlayer() {
        player.pause()
        timer = null
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
        val playbackState = updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        notificationManager.showNotification(playbackState, metadata)
        stopForeground(false)
    }

    private fun updatePlaybackState(state: Int): PlaybackStateCompat {
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, player.currentPosition.toLong(), 1f, System.currentTimeMillis())
            .build()
        mediaSession.setPlaybackState(playbackState)
        return playbackState
    }

    // when headphones are disconnected
    private val myNoisyAudioStreamReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Toast.makeText(context, "Headphones are disconnected", Toast.LENGTH_SHORT).show()
            pausePlayer()
        }
    }

    // when another app starts playing music, this callback happens
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> pausePlayer()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pausePlayer()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.setVolume(0.5f, 0.5f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!player.isPlaying) startPlayer()
                player.setVolume(1.0f, 1.0f)
            }
        }
    }

    private val audioFocusRequest =
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(afChangeListener)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentMediaId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
    }

    override fun onDestroy() {
        mediaSession.isActive = false
        try {
            context.unregisterReceiver(myNoisyAudioStreamReceiver)
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
        } catch (e: Exception) {
            Toast.makeText(context, "Destroy error:\n${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "onDestroy()", e)
        }
        player.release()
        timer = null
        Toast.makeText(context, "Service Destroyed!", Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }
}


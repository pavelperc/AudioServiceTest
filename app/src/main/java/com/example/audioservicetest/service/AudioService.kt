package com.example.audioservicetest.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.service.media.MediaBrowserService
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.MediaBrowserServiceCompat
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
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Some Title")
        .putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, "Some Author")
        .build()

    private var timer: CountDownTimer? = null
        set(value) {
            field?.cancel()
            field = value
        }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(context, TAG).apply {
//             Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                )
            setPlaybackState(stateBuilder.build())
        }

        sessionToken = mediaSession.sessionToken
        mediaSession.setCallback(mediaSessionCallback)

        notificationManager = MyNotificationManager(
            context, mediaSession,
            onStop = { stopPlayer() }
        )
        mediaSession.setMetadata(metadata)

        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.registerReceiver(myNoisyAudioStreamReceiver, intentFilter)
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = startPlayer()
        override fun onStop() = stopPlayer()
        override fun onPause() = pausePlayer()
    }


    private fun startPlayer() {
        // fails if another app is playing music this time
        val result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return
        }
        // todo WHERE IN OUR APPLICATION?
        startService(Intent(context, MediaBrowserService::class.java))

        mediaSession.isActive = true // todo WHY

        player.start()
        timer = object : CountDownTimer(
            (player.duration - player.currentPosition + 100).toLong(), 1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            }

            override fun onFinish() {
                stopPlayer()
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
        timer = null
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
        stopSelf()
        mediaSession.isActive = false // todo WHY DO WE NEED THIS???
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
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
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
    ): BrowserRoot? {
        // todo understand, check why null in our application.
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentMediaId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        // Assume for example that the music catalog is already loaded/cached.
        // check MY_MEDIA_ROOT_ID

        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(MY_MEDIA_ID)
            .setTitle("Sample") // todo what is metadata then???
            .setSubtitle("Subtitile")
            .build()
        val mediaItem =
            MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
        result.sendResult(listOf(mediaItem))
    }

    override fun onDestroy() {
        try {
            context.unregisterReceiver(myNoisyAudioStreamReceiver)
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


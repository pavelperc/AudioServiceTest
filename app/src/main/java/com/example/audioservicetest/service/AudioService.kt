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

    override fun onCreate() {
        super.onCreate()
        // Create a MediaSessionCompat
        mediaSession = MediaSessionCompat(context, TAG).apply {

//             Set an initial PlaybackState with ACTION_PLAY, so media buttons can start the player
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
//                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
//                            or PlaybackStateCompat.ACTION_STOP
                )
            setPlaybackState(stateBuilder.build())
        }

        sessionToken = mediaSession.sessionToken
        mediaSession.setCallback(mediaSessionCallback)

        notificationManager = MyNotificationManager(context, mediaSession)
        mediaSession.setMetadata(metadata)
    }


    private var timer: CountDownTimer? = null
        set(value) {
            field?.cancel()
            field = value
        }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            // Request audio focus for playback, this registers the afChangeListener
            val result = audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            // WHAT IS IT????????????????

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                // Start the service
                context.startService(Intent(context, MediaBrowserService::class.java))

                // Set the session active  (and update metadata and state)
                mediaSession.isActive = true
                // start the player (custom call)
                player.start()
                val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                // Register BECOME_NOISY BroadcastReceiver
                context.registerReceiver(myNoisyAudioStreamReceiver, intentFilter)

                timer = object : CountDownTimer(
                    (player.duration - player.currentPosition + 100).toLong(), 1000
                ) {
                    override fun onTick(millisUntilFinished: Long) {
                        mediaSession.setPlaybackState(
                            PlaybackStateCompat.Builder().setState(
                                PlaybackStateCompat.STATE_PLAYING,
                                player.currentPosition.toLong(), 1f, System.currentTimeMillis()
                            ).build()
                        )
                    }

                    override fun onFinish() {
                        updatePlayerState()
                    }
                }.start()
            }
        }

        override fun onStop() {
            timer = null
            // Abandon audio focus
//            audioManager.abandonAudioFocus(focusChangeListener)
            try {
                context.unregisterReceiver(myNoisyAudioStreamReceiver)
            } catch (e: Exception) {
            }
            // Stop the service
            stopSelf()
            // Set the session inactive  (and update metadata and state)
//            mediaSession.isActive = false
            // stop the player (custom call)
            player.stop()
            player.prepare()
            player.seekTo(0)
            // Take the service out of the foreground
//            stopForeground(false)
            updatePlayerState(stop = true)
        }

        override fun onPause() {
            // Update metadata and state
            // pause the player (custom call)
            player.pause()
            // unregister BECOME_NOISY BroadcastReceiver
            context.unregisterReceiver(myNoisyAudioStreamReceiver) /// aaaaaaa crash!!!!!
            // Take the service out of the foreground, retain the notification
//            stopForeground(false)
            timer = null

            updatePlayerState()
        }

        private val myNoisyAudioStreamReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                timer = null
                player.stop()
                player.prepare()
                player.seekTo(0)
                updatePlayerState(stop = true)
            }
        }

        private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> player.pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player.pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.setVolume(0.5f, 0.5f)
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (!player.isPlaying) player.start()
                    player.setVolume(1.0f, 1.0f)
                }
            }
            if (!player.isPlaying) {
                timer = null
            }
            updatePlayerState()
        }
    }

    private fun updatePlayerState(stop: Boolean = false) {
        val playbackState: PlaybackStateCompat
        if (stop) {
            playbackState = PlaybackStateCompat.Builder().setState(
                PlaybackStateCompat.STATE_STOPPED,
                player.currentPosition.toLong(), 1f, System.currentTimeMillis()
            ).build()
        } else if (player.isPlaying) {
            playbackState = PlaybackStateCompat.Builder().setState(
                PlaybackStateCompat.STATE_PLAYING,
                player.currentPosition.toLong(), 1f, System.currentTimeMillis()
            ).build()

        } else {
            playbackState = PlaybackStateCompat.Builder().setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    player.currentPosition.toLong(), 1f, System.currentTimeMillis()
                ).build()
        }
        mediaSession.setPlaybackState(playbackState)

        val notification = notificationManager.createNotification(playbackState, metadata)
        if (player.isPlaying) {
            startForeground(MyNotificationManager.NOTIFICATION_ID, notification)
        } else {
            stopForeground(false)
        }
    }


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
        player.release()
        timer = null
        super.onDestroy()
    }
}


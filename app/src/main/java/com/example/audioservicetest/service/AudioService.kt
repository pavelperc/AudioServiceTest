package com.example.audioservicetest.service

import android.app.Notification
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.example.audioservicetest.R
import com.example.audioservicetest.notification.MyNotificationManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.RawResourceDataSource


class AudioService : MediaBrowserServiceCompat() {
    companion object {
        const val TAG = "AudioService"
        const val MY_MEDIA_ROOT_ID = "root_id"
        const val MY_MEDIA_ID = "123"
    }

    private val context get() = applicationContext

    private val player by lazy {
        SimpleExoPlayer.Builder(this).setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(), true // handle audio focus (pause when a conflicting player starts)
        )
            .setHandleAudioBecomingNoisy(true) // pause player on headphones disconnect
            .build()
            .apply { addListener(playerEventListener) }
    }

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private lateinit var notificationManager: MyNotificationManager

    private var isForegroundService = false

    private val metadata = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, "Title from Metadata")
        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, "Subtitle from Metadata")
        .build()

    override fun onCreate() {
        super.onCreate()
        player.repeatMode = Player.REPEAT_MODE_ONE

        mediaSession = MediaSessionCompat(context, TAG)
        sessionToken = mediaSession.sessionToken

        // ExoPlayer manages the MediaSession for us.
        mediaSessionConnector = MediaSessionConnector(mediaSession)
        mediaSessionConnector.setPlaybackPreparer(myPlaybackPreparer)
        mediaSessionConnector.setPlayer(player)
        mediaSessionConnector.setMediaMetadataProvider { player ->
            metadata
        }

        notificationManager = MyNotificationManager(
            context, mediaSession, player,
            notificationListener
        )

        // active session enables media controls on the lock screen
        mediaSession.isActive = true
    }

    private val myPlaybackPreparer = object : MediaSessionConnector.PlaybackPreparer {
        override fun getSupportedPrepareActions() =
            PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE

        override fun onPrepare(playWhenReady: Boolean) {
            val mediaItem =
                MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(R.raw.sample))
            player.stop(true)
            player.setMediaItem(mediaItem)
            player.playWhenReady = playWhenReady
            player.prepare()
        }

        override fun onPrepareFromMediaId(
            mediaId: String,
            playWhenReady: Boolean,
            extras: Bundle?
        ) {
            onPrepare(playWhenReady)
        }

        override fun onCommand(
            player: Player, controlDispatcher: ControlDispatcher,
            command: String, extras: Bundle?, cb: ResultReceiver?
        ) = false

        override fun onPrepareFromSearch(query: String, playWhenReady: Boolean, extras: Bundle?) {}
        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) {}

    }

    private val playerEventListener = object : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    // need to start foreground after showing
                    notificationManager.showNotification()
                }
                Player.STATE_READY -> {
                    notificationManager.showNotification()

                    if (!playWhenReady) { // paused
                        stopForeground(false)
                    }
                }
                else -> {
                    notificationManager.hideNotification()
                }
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            Log.e(TAG, "Player error:", error)
            Toast.makeText(context, error.toString(), Toast.LENGTH_LONG).show()
        }
    }

    private val notificationListener = object : PlayerNotificationManager.NotificationListener {
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean // should be in the foreground or not
        ) {
            if (ongoing && !isForegroundService) {
                ContextCompat.startForegroundService(
                    context, Intent(context, AudioService::class.java)
                )
                startForeground(notificationId, notification)
                isForegroundService = true
            }
        }

        override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
            stopForeground(true)
            isForegroundService = false
            stopSelf()
        }
    }

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
        player.release()
        Toast.makeText(context, "Service Destroyed!", Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }
}


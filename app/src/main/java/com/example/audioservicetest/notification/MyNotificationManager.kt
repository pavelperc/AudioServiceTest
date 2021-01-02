package com.example.audioservicetest.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import com.example.audioservicetest.R
import com.example.audioservicetest.ui.MediaPlayerActivity
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerNotificationManager


class MyNotificationManager(
    private val context: Context,
    private val session: MediaSessionCompat,
    private val player: Player,
    notificationListener: PlayerNotificationManager.NotificationListener
) {
    companion object {
        const val TAG = "MyNotificationManager"
        val CHANNEL_ID = "com.example.test_channel"
        const val NOTIFICATION_ID = 123
        const val REQUEST_CODE = 100
    }

    private val mediaController = MediaControllerCompat(context, session)

    private val notificationManager = PlayerNotificationManager.createWithNotificationChannel(
        context,
        CHANNEL_ID,
        R.string.notification_channel,
        R.string.notification_channel_description,
        NOTIFICATION_ID,
        MyDescriptionAdapter(),
        notificationListener
    )

    init {
        notificationManager.setMediaSessionToken(session.sessionToken)
        notificationManager.setColor(Color.GREEN)
    }

    fun hideNotification() {
        notificationManager.setPlayer(null)
    }

    fun showNotification() {
        notificationManager.setPlayer(player)
    }

    private inner class MyDescriptionAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player) =
            mediaController.metadata.description.title.toString()

        override fun getCurrentContentText(player: Player) =
            mediaController.metadata.description.subtitle.toString()

        override fun createCurrentContentIntent(player: Player): PendingIntent {
            val intent = Intent(context, MediaPlayerActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(
                context, REQUEST_CODE, intent, PendingIntent.FLAG_CANCEL_CURRENT
            )
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? = null
    }
}
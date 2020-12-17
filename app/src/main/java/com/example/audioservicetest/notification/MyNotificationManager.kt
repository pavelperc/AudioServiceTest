package com.example.audioservicetest.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.audioservicetest.R
import com.example.audioservicetest.ui.MediaPlayerActivity


class MyNotificationManager(
    val context: Context,
    val session: MediaSessionCompat,
    val onStop: () -> Unit
) {
    companion object {
        const val TAG = "MyNotificationManager"
        val CHANNEL_ID = "com.example.test_channel"
        const val NOTIFICATION_ID = 123
        const val REQUEST_CODE = 100

        const val ACTION_PLAY = "com.example.play"
        const val ACTION_PAUSE = "com.example.pause"
//        const val ACTION_SEEK_NEXT = "com.example.seek.next"
//        const val ACTION_SEEK_PREV = "com.example.seek.prev"
        const val ACTION_CLOSE = "com.example.close"
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    private val mediaController = MediaControllerCompat(context, session)
    private val transportControls get() = mediaController.transportControls


    val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            when (action) {
                ACTION_PLAY -> {
                    transportControls.play()
                }
                ACTION_PAUSE -> {
                    transportControls.pause()
                }
                ACTION_CLOSE -> {
                    transportControls.stop()
                }
                else -> {
                    Log.w(TAG, "unknown notification command $action")
                }
            }
        }
    }

    private val playIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(ACTION_PLAY),
        PendingIntent.FLAG_CANCEL_CURRENT
    )
    private val pauseIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(ACTION_PAUSE),
        PendingIntent.FLAG_CANCEL_CURRENT
    )

    private val closeIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(ACTION_CLOSE),
        PendingIntent.FLAG_CANCEL_CURRENT
    )


    fun showNotification(
        state: PlaybackStateCompat,
        metadata: MediaMetadataCompat
    ): Notification {
        createChannel()

        val notification = buildNotification(state, metadata)
        notificationManager.notify(
            NOTIFICATION_ID,
            notification
        )
        return notification
    }

    private fun buildNotification(
        state: PlaybackStateCompat,
        metadata: MediaMetadataCompat
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)

        builder.setOngoing(false)
            .setContentIntent(createContentIntent())
            .setContentTitle(metadata.description.title)
            .setContentText(metadata.description.subtitle)
            .setColor(ContextCompat.getColor(context, R.color.purple_200))
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0)
                .setShowCancelButton(true)
                .setCancelButtonIntent(closeIntent)
                .setMediaSession(session.sessionToken)
        )
        builder.setCategory(Notification.CATEGORY_TRANSPORT)


        val playPauseAction: NotificationCompat.Action =
            if (state.state == PlaybackStateCompat.STATE_PLAYING)
                NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            else
                NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", playIntent)

        builder.addAction(playPauseAction)
        return builder.build()
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MediaPlayerActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "My channel",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "My channel description"
            channel.enableVibration(false)
            notificationManager.createNotificationChannel(channel)
        }

    }
}
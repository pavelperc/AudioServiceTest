package com.example.audioservicetest.ui

import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.observe
import com.example.audioservicetest.R
import kotlinx.android.synthetic.main.activity_media_player.*


class MediaPlayerActivity : AppCompatActivity() {

    val viewModel by viewModels<MediaPlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)

        viewModel.isConnected.observe(this) { isConnected ->
            if (isConnected) {
                tvConnectionState.text = "Connected to service"
            } else {
                tvConnectionState.text = "Disconnected from service"
            }
        }
        viewModel.playbackState.observe(this) { playbackState ->
            val seconds = playbackState.position / 1000
            val s: Long = seconds % 60
            val m: Long = seconds / 60 % 60
            val h: Long = seconds / (60 * 60) % 24
            tvTime.text = "%d:%02d:%02d".format(h, m, s)

            tvPlaybackState.text = when (playbackState.state) {
                PlaybackStateCompat.STATE_NONE -> "STATE_NONE"
                PlaybackStateCompat.STATE_PLAYING -> "STATE_PLAYING"
                PlaybackStateCompat.STATE_PAUSED -> "STATE_PAUSED"
                PlaybackStateCompat.STATE_STOPPED -> "STATE_STOPPED"
                else -> "Other state: ${playbackState.state}"

            }
            if (playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                btnPlayPause.text = "Pause"
            } else {
                btnPlayPause.text = "Play"
            }
        }
        viewModel.mediaMetadata.observe(this) { metadata ->
            if (metadata == null) {
                tvTitle.text = "Loading Title"
                tvSubtitle.text = "Loading Subtitle"
            } else {
                tvTitle.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                tvSubtitle.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
            }
        }

        setupControls()
    }

    fun setupControls() {
        btnPlayPause.setOnClickListener {
            val playbackState = viewModel.playbackState.value!!
            when (playbackState.state) {
                PlaybackStateCompat.STATE_PLAYING -> {
                    viewModel.pause()
                }
                else -> {
                    viewModel.play()
                }
            }
        }
        btnStop.setOnClickListener {
            viewModel.stop()
        }
    }

    public override fun onStart() {
        super.onStart()
        viewModel.connectToMediaService()
    }


    public override fun onStop() {
        super.onStop()
        viewModel.disconnectFromMediaService()
    }
}
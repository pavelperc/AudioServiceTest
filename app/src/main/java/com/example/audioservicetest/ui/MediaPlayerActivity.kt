package com.example.audioservicetest.ui

import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.audioservicetest.R
import kotlinx.android.synthetic.main.activity_media_player.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class MediaPlayerActivity : AppCompatActivity() {

    val viewModel by viewModels<MediaPlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_player)

        lifecycleScope.launchWhenCreated {
            launch {
                viewModel.isConnected
                    .collect { isConnected ->
                        if (isConnected) {
                            tvConnectionState.text = "Connected to service"
                        } else {
                            tvConnectionState.text = "Disconnected from service"
                        }
                    }
            }
            launch {
                viewModel.playbackPosition.collect { position ->
                    val seconds = position / 1000
                    val s: Long = seconds % 60
                    val m: Long = seconds / 60 % 60
                    val h: Long = seconds / (60 * 60) % 24
                    tvTime.text = "%d:%02d:%02d".format(h, m, s)
                }
            }
            launch {
                viewModel.playbackState.collect { playbackState ->
                    tvPlaybackState.text = when (playbackState.state) {
                        PlaybackStateCompat.STATE_NONE -> "STATE_NONE"
                        PlaybackStateCompat.STATE_PLAYING -> "STATE_PLAYING"
                        PlaybackStateCompat.STATE_BUFFERING -> "STATE_BUFFERING"
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
            }
            launch {
                viewModel.mediaMetadata.collect { metadata ->
                    if (metadata == null) {
                        tvTitle.text = "Loading Title"
                        tvSubtitle.text = "Loading Subtitle"
                    } else {
                        tvTitle.text = metadata.description.title ?: "null"
                        tvSubtitle.text = metadata.description.subtitle ?: "null"
                    }
                }
            }
        }

        setupControls()
        viewModel.connectToMediaService()
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

    public override fun onDestroy() {
        viewModel.disconnectFromMediaService()
        super.onDestroy()
    }
}
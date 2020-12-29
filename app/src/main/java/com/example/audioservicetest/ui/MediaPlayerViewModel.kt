package com.example.audioservicetest.ui

import android.app.Application
import android.content.ComponentName
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.audioservicetest.service.AudioService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.fixedRateTimer


class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>().applicationContext

    private var mediaController: MediaControllerCompat? = null

    val isConnected = MutableLiveData<Boolean>().apply { value = false }

    val playbackState = MutableLiveData<PlaybackStateCompat>().apply {
        value =
            PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_NONE, 0, 1f).build()
    }

    val playbackPosition = MutableLiveData<Long>().apply { value = 0 }

    val mediaMetadata = MutableLiveData<MediaMetadataCompat>().apply { value = null }

    private var timer: Timer? = null
        set(value) {
            field?.cancel()
            field = value
        }


    init {
        playbackState.observeForever { playbackState ->
            if (playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                val initDelay = (1000 - (playbackState.currentPlayBackPosition % 1000)) + 1
                checkPlaybackPosition()
                timer = fixedRateTimer("ProgressTimer", false, initDelay, 1000) {
                    checkPlaybackPosition()
                }
            } else {
                timer = null
                checkPlaybackPosition()
            }
        }
    }

    private fun checkPlaybackPosition() {
        playbackPosition.postValue(mediaController?.playbackState?.currentPlayBackPosition ?: 0)
    }

    private inline val PlaybackStateCompat.currentPlayBackPosition: Long
        get() = if (state == PlaybackStateCompat.STATE_PLAYING) {
            val timeDelta = SystemClock.elapsedRealtime() - lastPositionUpdateTime
            (position + (timeDelta * playbackSpeed)).toLong()
        } else {
            position
        }


    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            viewModelScope.launch {
                delay(500)
                mediaController =
                    MediaControllerCompat(context, mediaBrowser.sessionToken).also { controller ->
                        controller.registerCallback(controllerCallback)
                        isConnected.postValue(true)
                        playbackState.postValue(controller.playbackState)
                        mediaMetadata.postValue(controller.metadata)
                    }
            }
        }

        override fun onConnectionSuspended() {
            isConnected.postValue(false)
        }

        override fun onConnectionFailed() {
            isConnected.postValue(false)
        }
    }

    private val mediaBrowser: MediaBrowserCompat = MediaBrowserCompat(
        context,
        ComponentName(application.applicationContext, AudioService::class.java),
        connectionCallback,
        null // optional Bundle
    )

    fun connectToMediaService() {
        mediaBrowser.connect()
    }

    fun disconnectFromMediaService() {
        mediaBrowser.disconnect()
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat) {
            mediaMetadata.postValue(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            playbackState.postValue(state)
        }
    }

    fun pause() {
        mediaController?.transportControls?.pause()
    }

    fun play() {
        mediaController?.transportControls?.play()
    }

    fun stop() {
        mediaController?.transportControls?.stop()
    }
}
package com.example.audioservicetest.ui

import android.app.Application
import android.content.ComponentName
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


class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>().applicationContext

    private var mediaController: MediaControllerCompat? = null

    val isConnected = MutableLiveData<Boolean>().apply { value = false }

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            viewModelScope.launch {
                delay(500)
                mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken).also { controller ->
                    controller.registerCallback(controllerCallback)
                    isConnected.postValue(true)
                    playbackState.postValue(controller.playbackState)
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

    val playbackState = MutableLiveData<PlaybackStateCompat>().apply {
        value = PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_NONE, 0, 1f).build()
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat) {

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
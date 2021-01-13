package com.example.audioservicetest.ui

import android.app.Application
import android.content.ComponentName
import android.os.SystemClock
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audioservicetest.service.AudioService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.fixedRateTimer


class MediaPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>().applicationContext

    private var mediaController: MediaControllerCompat? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    // warning: doesn't update with the current position change
    private val _playbackState = MutableStateFlow<PlaybackStateCompat>(
        PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_NONE, 0, 1f)
            .build()
    )
    val playbackState = _playbackState.asStateFlow()

    private val _playbackPosition = MutableStateFlow<Long>(0)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _mediaMetadata = MutableStateFlow<MediaMetadataCompat?>(null)
    val mediaMetadata = _mediaMetadata.asStateFlow()

    // todo maybe move to the audio service?
    private var timer: Timer? = null
        set(value) {
            field?.cancel()
            field = value
        }


    init {
        viewModelScope.launch {
            _playbackState.collect { playbackState ->
                if (playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                    val initDelay = (1000 - (playbackState.currentPlayBackPosition % 1000)) + 1
                    checkPlaybackPosition()
                    timer = fixedRateTimer("ProgressTimer", false, initDelay, 1000) {
                        viewModelScope.launch {
                            checkPlaybackPosition()
                        }
                    }
                } else {
                    timer = null
                    checkPlaybackPosition()
                }
            }
        }
    }

    private suspend fun checkPlaybackPosition() {
        _playbackPosition.emit(mediaController?.playbackState?.currentPlayBackPosition ?: 0)
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
                        _isConnected.emit(true)
                        _playbackState.emit(controller.playbackState)
                        _mediaMetadata.emit(controller.metadata)
                    }
            }
        }

        override fun onConnectionSuspended() {
            viewModelScope.launch {
                _isConnected.emit(false)
            }
        }

        override fun onConnectionFailed() {
            viewModelScope.launch {
                _isConnected.emit(false)
            }
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
            viewModelScope.launch {
                _mediaMetadata.emit(metadata)
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            viewModelScope.launch {
                _playbackState.emit(state)
            }
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
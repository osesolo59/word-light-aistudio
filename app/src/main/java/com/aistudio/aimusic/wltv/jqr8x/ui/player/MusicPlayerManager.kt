package com.aistudio.aimusic.wltv.jqr8x.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class MusicPlayerManager(
    context: Context,
    connectController: Boolean = true
) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingMediaItem: MediaItem? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentSongTitle = MutableStateFlow<String?>(null)
    val currentSongTitle = _currentSongTitle.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _queueTitles = MutableStateFlow<List<String>>(emptyList())
    val queueTitles = _queueTitles.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError = _playbackError.asStateFlow()

    init {
        if (connectController) {
            try {
                val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
                controllerFuture?.addListener({
                    mediaController = try {
                        controllerFuture?.get()
                    } catch (_: Exception) {
                        null
                    } ?: return@addListener
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                        _currentSongTitle.value = mediaMetadata.title?.toString()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                            _playbackError.value = null
                        }
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _shuffleEnabled.value = shuffleModeEnabled
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        _currentSongTitle.value = mediaItem?.mediaMetadata?.title?.toString()
                        _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                        _currentPosition.value = mediaController?.currentPosition ?: 0L
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _isPlaying.value = false
                        val detail = error.localizedMessage?.takeIf { it.isNotBlank() } ?: error.errorCodeName
                        _playbackError.value = "Unable to play audio ($detail). Check your connection and try again."
                    }
                })

                pendingMediaItem?.let { item ->
                    pendingMediaItem = null
                    mediaController?.let { controller ->
                        controller.setMediaItem(item)
                        controller.prepare()
                        controller.play()
                        refreshQueueTitles(controller)
                    }
                }

                _isPlaying.value = mediaController?.isPlaying ?: false
                _currentSongTitle.value = mediaController?.mediaMetadata?.title?.toString()
                _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                _shuffleEnabled.value = mediaController?.shuffleModeEnabled ?: false
                _repeatMode.value = mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
            }, MoreExecutors.directExecutor())
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerManager", "Failed to bind MediaController", e)
            }
        }
    }

    private fun refreshQueueTitles(controller: MediaController) {
        val titles = mutableListOf<String>()
        for (i in 0 until controller.mediaItemCount) {
            titles.add(controller.getMediaItemAt(i).mediaMetadata.title?.toString() ?: "Untitled")
        }
        _queueTitles.value = titles
    }

    fun updatePosition() {
        _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
        _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
    }

    fun playSong(url: String, title: String) {
        _playbackError.value = null
        val mediaItem = buildMediaItem(url, title)
        _currentSongTitle.value = title
        val controller = mediaController
        if (controller != null) {
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            refreshQueueTitles(controller)
        } else {
            pendingMediaItem = mediaItem
        }
    }

    fun addToQueue(url: String, title: String) {
        val mediaItem = buildMediaItem(url, title)
        val controller = mediaController
        if (controller != null) {
            if (controller.mediaItemCount == 0) {
                playSong(url, title)
            } else {
                controller.addMediaItem(mediaItem)
                refreshQueueTitles(controller)
            }
        } else {
            if (pendingMediaItem == null) {
                pendingMediaItem = mediaItem
                _currentSongTitle.value = title
            }
        }
    }

    fun toggleShuffle() {
        mediaController?.let { controller ->
            val next = !controller.shuffleModeEnabled
            controller.shuffleModeEnabled = next
            _shuffleEnabled.value = next
        }
    }

    fun toggleRepeat() {
        mediaController?.let { controller ->
            val next = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            controller.repeatMode = next
            _repeatMode.value = next
        }
    }

    private fun buildMediaItem(url: String, title: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .build()
        return MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun resume() {
        _playbackError.value = null
        mediaController?.play()
    }

    fun stop() {
        pendingMediaItem = null
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _isPlaying.value = false
        _currentSongTitle.value = null
        _duration.value = 0L
        _currentPosition.value = 0L
        _queueTitles.value = emptyList()
        _playbackError.value = null
    }

    fun clearPlaybackError() {
        _playbackError.value = null
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        _currentPosition.value = position
    }

    fun release() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}

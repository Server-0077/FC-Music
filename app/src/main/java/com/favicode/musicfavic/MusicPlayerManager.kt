package com.favicode.musicfavic

import android.content.Context
import android.media.MediaPlayer

object MusicPlayerManager {
    var mediaPlayer: MediaPlayer? = null
    var currentSongList: MutableList<MediaModel> = mutableListOf()
    var currentPlayingIndex: Int = -1
    var isShuffleEnabled: Boolean = false
    var isRepeatEnabled: Boolean = false

    var onSongChangeListener: ((MediaModel, Int) -> Unit)? = null
    var onPlayStateChangedListener: ((Boolean) -> Unit)? = null

    fun playSong(context: Context, songList: MutableList<MediaModel>, index: Int) {
        if (songList.isEmpty() || index !in songList.indices) return

        currentSongList = songList
        currentPlayingIndex = index
        val media = songList[index]

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, media.uri).apply {
            isLooping = isRepeatEnabled
            setOnPreparedListener { player ->
                player.start()
                onSongChangeListener?.invoke(media, index)
                onPlayStateChangedListener?.invoke(true)
            }
            setOnCompletionListener {
                if (!isRepeatEnabled) {
                    playNext(context)
                }
            }
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                onPlayStateChangedListener?.invoke(false)
            } else {
                player.start()
                onPlayStateChangedListener?.invoke(true)
            }
        }
    }

    fun playNext(context: Context) {
        if (currentSongList.isEmpty()) return
        currentPlayingIndex = if (isShuffleEnabled) {
            kotlin.random.Random.nextInt(currentSongList.size)
        } else {
            (currentPlayingIndex + 1) % currentSongList.size
        }
        playSong(context, currentSongList, currentPlayingIndex)
    }

    fun playPrev(context: Context) {
        if (currentSongList.isEmpty()) return
        currentPlayingIndex = if (currentPlayingIndex - 1 < 0) currentSongList.size - 1 else currentPlayingIndex - 1
        playSong(context, currentSongList, currentPlayingIndex)
    }
}
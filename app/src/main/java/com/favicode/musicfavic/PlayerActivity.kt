package com.favicode.musicfavic

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlayerActivity : AppCompatActivity() {

    private lateinit var imgPlayerCover: ImageView
    private lateinit var tvPlayerTitle: TextView
    private lateinit var tvPlayerArtist: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: FloatingActionButton
    private lateinit var btnPrevious: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnShuffle: ImageView
    private lateinit var btnRepeat: ImageView
    private lateinit var btnDown: ImageView

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateSeekBarRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        initViews()
        setupListeners()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initViews() {
        imgPlayerCover = findViewById(R.id.imgPlayerCover)
        tvPlayerTitle = findViewById(R.id.tvPlayerTitle)
        tvPlayerArtist = findViewById(R.id.tvPlayerArtist)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrevious = findViewById(R.id.btnPrevious)
        btnNext = findViewById(R.id.btnNext)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        btnDown = findViewById(R.id.btnDown)
    }

    private fun setupListeners() {
        btnDown.setOnClickListener {
            finish()
        }

        btnPlayPause.setOnClickListener {
            MusicPlayerManager.togglePlayPause()
        }

        btnNext.setOnClickListener {
            MusicPlayerManager.playNext(this)
            updateUI()
        }

        btnPrevious.setOnClickListener {
            MusicPlayerManager.playPrev(this)
            updateUI()
        }

        btnShuffle.setOnClickListener {
            MusicPlayerManager.isShuffleEnabled = !MusicPlayerManager.isShuffleEnabled
            updateShuffleButtonState()
        }

        btnRepeat.setOnClickListener {
            MusicPlayerManager.isRepeatEnabled = !MusicPlayerManager.isRepeatEnabled
            MusicPlayerManager.mediaPlayer?.isLooping = MusicPlayerManager.isRepeatEnabled
            updateRepeatButtonState()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    MusicPlayerManager.mediaPlayer?.seekTo(progress)
                    tvCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        MusicPlayerManager.onSongChangeListener = { _, _ ->
            updateUI()
        }

        MusicPlayerManager.onPlayStateChangedListener = { isPlaying ->
            updatePlayPauseButton(isPlaying)
        }

        updateSeekBarRunnable = object : Runnable {
            override fun run() {
                MusicPlayerManager.mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        seekBar.progress = player.currentPosition
                        tvCurrentTime.text = formatTime(player.currentPosition.toLong())
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateSeekBarRunnable)
    }

    private fun updateUI() {
        val currentMedia = MusicPlayerManager.currentSongList.getOrNull(MusicPlayerManager.currentPlayingIndex)
        val player = MusicPlayerManager.mediaPlayer

        if (currentMedia != null) {
            tvPlayerTitle.text = currentMedia.title
            tvPlayerArtist.text = currentMedia.bucketName

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, currentMedia.uri)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    imgPlayerCover.setImageBitmap(bitmap)
                } else {
                    imgPlayerCover.setImageResource(R.drawable.default_song_cover)
                }
            } catch (_: Exception) {
                imgPlayerCover.setImageResource(R.drawable.default_song_cover)
            } finally {
                retriever.release()
            }
        }

        if (player != null) {
            seekBar.max = player.duration
            tvTotalTime.text = formatTime(player.duration.toLong())
            updatePlayPauseButton(player.isPlaying)
        }

        updateShuffleButtonState()
        updateRepeatButtonState()
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun updateShuffleButtonState() {
        val color = if (MusicPlayerManager.isShuffleEnabled) 0xFF1DB954.toInt() else 0xFFB3B3B3.toInt()
        btnShuffle.setColorFilter(color)
    }

    private fun updateRepeatButtonState() {
        val color = if (MusicPlayerManager.isRepeatEnabled) 0xFF1DB954.toInt() else 0xFFB3B3B3.toInt()
        btnRepeat.setColorFilter(color)
    }

    private fun formatTime(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
    }
}
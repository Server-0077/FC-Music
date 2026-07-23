package com.favicode.musicfavic

import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import java.util.concurrent.TimeUnit

class AlbumDetailActivity : AppCompatActivity() {

    // ==========================================
    // DECLARACIÓN DE VISTAS Y VARIABLES
    // ==========================================
    private lateinit var containerAlbumSongs: LinearLayout
    private lateinit var tvAlbumTitleHeader: TextView
    private val albumSongsList = mutableListOf<MediaModel>()

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var tvCurrentSong: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateSeekBarRunnable: Runnable
    private val cardViewsList = mutableListOf<MaterialCardView>()

    // ==========================================
    // CICLO DE VIDA: onCreate
    // ==========================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0A0A0F.toInt())
            setPadding((16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        tvAlbumTitleHeader = TextView(this).apply {
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, (12 * density).toInt(), 0, (16 * density).toInt())
        }
        rootLayout.addView(tvAlbumTitleHeader)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }

        containerAlbumSongs = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        scrollView.addView(containerAlbumSongs)
        rootLayout.addView(scrollView)

        val playerBar = createPlayerBarLayout(density)
        rootLayout.addView(playerBar)
        setContentView(rootLayout)

        val folderName = intent.getStringExtra("FOLDER_NAME") ?: getString(R.string.default_song_title)
        tvAlbumTitleHeader.text = folderName

        setupPlayerControls()
        loadSongsForAlbum(folderName)
        setupGlobalListeners()
    }

    // ==========================================
    // CICLO DE VIDA: onResume
    // ==========================================
    override fun onResume() {
        super.onResume()
        highlightCurrentPlayingSong()
        updatePlayerUIState()
    }

    // ==========================================
    // ESCUCHAS GLOBALES DEL REPRODUCTOR
    // ==========================================
    private fun setupGlobalListeners() {
        MusicPlayerManager.onSongChangeListener = { media, _ ->
            tvCurrentSong.text = media.title
            tvTotalTime.text = formatTime(media.duration)
            seekBar.max = MusicPlayerManager.mediaPlayer?.duration ?: 0
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            highlightCurrentPlayingSong()
        }

        MusicPlayerManager.onPlayStateChangedListener = { isPlaying ->
            if (isPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    private fun highlightCurrentPlayingSong() {
        val currentPlayingMedia = MusicPlayerManager.currentSongList.getOrNull(MusicPlayerManager.currentPlayingIndex)

        for ((idx, card) in cardViewsList.withIndex()) {
            val mediaInCard = albumSongsList.getOrNull(idx)
            if (mediaInCard != null && currentPlayingMedia != null && mediaInCard.id == currentPlayingMedia.id) {
                card.setCardBackgroundColor(0xFF3B1E54.toInt())
                card.strokeColor = 0xFFA855F7.toInt()
                card.strokeWidth = 3
            } else {
                card.setCardBackgroundColor(0xFF161622.toInt())
                card.strokeWidth = 0
            }
        }
    }

    private fun updatePlayerUIState() {
        MusicPlayerManager.mediaPlayer?.let { player ->
            tvCurrentSong.text = MusicPlayerManager.currentSongList.getOrNull(MusicPlayerManager.currentPlayingIndex)?.title ?: getString(R.string.default_song_title)
            tvTotalTime.text = formatTime(player.duration.toLong())
            seekBar.max = player.duration
            if (player.isPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    // ==========================================
    // CREACIÓN DINÁMICA DE LA BARRA INFERIOR
    // ==========================================
    private fun createPlayerBarLayout(density: Float): LinearLayout {
        val playerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1B1B2F.toInt())
            setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * density).toInt()
            }
        }

        tvCurrentSong = TextView(this).apply {
            text = getString(R.string.default_song_title)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 1
        }
        playerLayout.addView(tvCurrentSong)

        val timeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
        }

        tvCurrentTime = TextView(this).apply {
            text = getString(R.string.time_zero)
            textSize = 10f
            setTextColor(0xFF9E9EB5.toInt())
        }

        seekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * density).toInt()
                marginEnd = (8 * density).toInt()
            }
            progressTintList = android.content.res.ColorStateList.valueOf(0xFFA855F7.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xFFA855F7.toInt())
        }

        tvTotalTime = TextView(this).apply {
            text = getString(R.string.time_zero)
            textSize = 10f
            setTextColor(0xFF9E9EB5.toInt())
        }

        timeLayout.addView(tvCurrentTime)
        timeLayout.addView(seekBar)
        timeLayout.addView(tvTotalTime)
        playerLayout.addView(timeLayout)

        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            weightSum = 5f
        }

        btnShuffle = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (40 * density).toInt(), 1f)
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_menu_agenda)
            setColorFilter(0xFF707085.toInt())
        }
        btnPrev = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (40 * density).toInt(), 1f)
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(0xFFFFFFFF.toInt())
        }
        btnPlayPause = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (48 * density).toInt(), 1f)
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(0xFFA855F7.toInt())
        }
        btnNext = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (40 * density).toInt(), 1f)
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(0xFFFFFFFF.toInt())
        }
        btnRepeat = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, (40 * density).toInt(), 1f)
            setBackgroundColor(0x00000000)
            setImageResource(android.R.drawable.ic_menu_rotate)
            setColorFilter(0xFF707085.toInt())
        }

        buttonsLayout.addView(btnShuffle)
        buttonsLayout.addView(btnPrev)
        buttonsLayout.addView(btnPlayPause)
        buttonsLayout.addView(btnNext)
        buttonsLayout.addView(btnRepeat)
        playerLayout.addView(buttonsLayout)

        return playerLayout
    }

    // ==========================================
    // CONTROLES DE REPRODUCCIÓN Y EVENTOS
    // ==========================================
    private fun setupPlayerControls() {
        btnPlayPause.setOnClickListener {
            MusicPlayerManager.togglePlayPause()
        }

        btnNext.setOnClickListener { MusicPlayerManager.playNext(this) }
        btnPrev.setOnClickListener { MusicPlayerManager.playPrev(this) }

        btnShuffle.setOnClickListener {
            MusicPlayerManager.isShuffleEnabled = !MusicPlayerManager.isShuffleEnabled
            btnShuffle.setColorFilter(if (MusicPlayerManager.isShuffleEnabled) 0xFFFFD700.toInt() else 0xFF707085.toInt())
        }

        btnRepeat.setOnClickListener {
            MusicPlayerManager.isRepeatEnabled = !MusicPlayerManager.isRepeatEnabled
            MusicPlayerManager.mediaPlayer?.isLooping = MusicPlayerManager.isRepeatEnabled
            btnRepeat.setColorFilter(if (MusicPlayerManager.isRepeatEnabled) 0xFFFFD700.toInt() else 0xFF707085.toInt())
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

    private fun formatTime(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    // ==========================================
    // CARGA DE DATOS DESDE MEDIASTORE
    // ==========================================
    private fun loadSongsForAlbum(targetFolder: String) {
        albumSongsList.clear()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("audio/mpeg")

        contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val bucketCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val bucket = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Música" else "Música"
                if (bucket.equals(targetFolder, ignoreCase = true)) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: cursor.getString(displayCol) ?: "Sin título"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    albumSongsList.add(MediaModel(id, title, uri, duration, size, bucket))
                }
            }
        }
        displaySongs()
    }

    // ==========================================
    // RENDERIZADO VISUAL DE LAS CANCIONES
    // ==========================================
    private fun displaySongs() {
        containerAlbumSongs.removeAllViews()
        cardViewsList.clear()
        val density = resources.displayMetrics.density

        for ((index, media) in albumSongsList.withIndex()) {
            val cardView = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (6 * density).toInt(), 0, (6 * density).toInt())
                }
                radius = 16f * density
                setCardBackgroundColor(0xFF161622.toInt())
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    MusicPlayerManager.currentSongList = albumSongsList
                    MusicPlayerManager.playSong(this@AlbumDetailActivity, albumSongsList, index)
                }
            }

            cardViewsList.add(cardView)

            val innerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                gravity = Gravity.CENTER_VERTICAL
            }

            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
                setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            }

            val textLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (14 * density).toInt()
                }
                orientation = LinearLayout.VERTICAL
            }

            val titleView = TextView(this).apply {
                text = media.title
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                maxLines = 1
            }

            val minutes = TimeUnit.MILLISECONDS.toMinutes(media.duration)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(media.duration) % 60
            val durationView = TextView(this).apply {
                text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                textSize = 12f
                setTextColor(0xFFFFD700.toInt())
            }

            textLayout.addView(titleView)
            textLayout.addView(durationView)
            innerLayout.addView(iconView)
            innerLayout.addView(textLayout)
            cardView.addView(innerLayout)
            containerAlbumSongs.addView(cardView)
        }
        highlightCurrentPlayingSong()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
    }
}
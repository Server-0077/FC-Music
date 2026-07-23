package com.favicode.musicfavic

import android.content.ContentUris
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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

    // Vistas del Reproductor Inferior Flotante (Sincronizado con MainActivity)
    private lateinit var layoutPlayerBar: MaterialCardView
    private lateinit var tvCurrentSong: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var imgMiniAlbumArt: ImageView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var miniSeekBar: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateSeekBarRunnable: Runnable
    private val cardViewsList = mutableListOf<MaterialCardView>()

    // ==========================================
    // CICLO DE VIDA: onCreate
    // ==========================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_album_detail)

        containerAlbumSongs = findViewById(R.id.containerAlbumSongs)
        tvAlbumTitleHeader = findViewById(R.id.tvAlbumTitle)

        val folderName = intent.getStringExtra("FOLDER_NAME") ?: getString(R.string.default_song_title)
        tvAlbumTitleHeader.text = folderName

        initPlayerViews()
        setupBottomNavigation()
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
    // INICIALIZACIÓN DE VISTAS DEL REPRODUCTOR
    // ==========================================
    private fun initPlayerViews() {
        layoutPlayerBar = findViewById(R.id.layoutPlayerBar)
        tvCurrentSong = findViewById(R.id.tvCurrentSong)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        imgMiniAlbumArt = findViewById(R.id.imgMiniAlbumArt)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        miniSeekBar = findViewById(R.id.miniSeekBar)

        layoutPlayerBar.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }
    }

    // ==========================================
    // NAVEGACIÓN INFERIOR
    // ==========================================
    private fun setupBottomNavigation() {
        val navHome = findViewById<TextView>(R.id.navHome)
        val navSearch = findViewById<TextView>(R.id.navSearch)
        val navLibrary = findViewById<TextView>(R.id.navLibrary)
        val navSettings = findViewById<TextView>(R.id.navSettings)

        navHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        navSearch.setOnClickListener {
            val intent = Intent(this, BuscarActivity::class.java)
            startActivity(intent)
            finish()
        }

        navLibrary.setOnClickListener {
            val intent = Intent(this, LibraryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        navSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // ==========================================
    // ESCUCHAS GLOBALES DEL REPRODUCTOR
    // ==========================================
    private fun setupGlobalListeners() {
        MusicPlayerManager.onSongChangeListener = { media, _ ->
            tvCurrentSong.text = media.title
            tvMiniArtist.text = media.bucketName
            miniSeekBar.max = MusicPlayerManager.mediaPlayer?.duration ?: 0
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            loadMiniAlbumArt(media)
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
            val currentMedia = MusicPlayerManager.currentSongList.getOrNull(MusicPlayerManager.currentPlayingIndex)
            tvCurrentSong.text = currentMedia?.title ?: getString(R.string.default_song_title)
            tvMiniArtist.text = currentMedia?.bucketName ?: getString(R.string.default_artist_name)
            miniSeekBar.max = player.duration
            if (player.isPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
            currentMedia?.let { loadMiniAlbumArt(it) }
        }
    }

    private fun loadMiniAlbumArt(media: MediaModel) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, media.uri)
            val artBytes = retriever.embeddedPicture
            if (artBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                imgMiniAlbumArt.setImageBitmap(bitmap)
            } else {
                imgMiniAlbumArt.setImageResource(R.drawable.default_song_cover)
            }
        } catch (e: Exception) {
            imgMiniAlbumArt.setImageResource(R.drawable.default_song_cover)
        } finally {
            retriever.release()
        }
    }

    // ==========================================
    // CONTROLES DE REPRODUCCIÓN Y EVENTOS
    // ==========================================
    private fun setupPlayerControls() {
        btnPlayPause.setOnClickListener {
            MusicPlayerManager.togglePlayPause()
        }

        updateSeekBarRunnable = object : Runnable {
            override fun run() {
                MusicPlayerManager.mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        miniSeekBar.progress = player.currentPosition
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateSeekBarRunnable)
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
    // RENDERIZADO VISUAL UTILIZANDO EL XML `item_song`
    // ==========================================
    private fun displaySongs() {
        containerAlbumSongs.removeAllViews()
        cardViewsList.clear()
        val density = resources.displayMetrics.density

        for ((index, media) in albumSongsList.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_song, containerAlbumSongs, false)

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

                    val intent = Intent(this@AlbumDetailActivity, PlayerActivity::class.java)
                    startActivity(intent)
                }
            }

            cardViewsList.add(cardView)

            val iconView = itemView.findViewById<ImageView>(R.id.imgSongCover)
            val titleView = itemView.findViewById<TextView>(R.id.tvSongTitle)
            val artistDurationView = itemView.findViewById<TextView>(R.id.tvSongArtist)

            titleView.text = media.title

            val minutes = TimeUnit.MILLISECONDS.toMinutes(media.duration)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(media.duration) % 60
            artistDurationView.text = String.format(Locale.getDefault(), "%s • %02d:%02d", media.bucketName, minutes, seconds)

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this@AlbumDetailActivity, media.uri)
                val artBytes = retriever.embeddedPicture
                if (artBytes != null) {
                    val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                    iconView.setImageBitmap(bitmap)
                } else {
                    iconView.setImageResource(R.drawable.default_song_cover)
                }
            } catch (e: Exception) {
                iconView.setImageResource(R.drawable.default_song_cover)
            } finally {
                retriever.release()
            }

            cardView.addView(itemView)
            containerAlbumSongs.addView(cardView)
        }
        highlightCurrentPlayingSong()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
    }
}
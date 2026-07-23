package com.favicode.musicfavic

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import java.util.concurrent.TimeUnit

// ==========================================
// MODELO DE DATOS
// ==========================================
data class MediaModel(
    val id: Long,
    val title: String,
    val uri: android.net.Uri,
    val duration: Long,
    val size: Long,
    val bucketName: String
)

class MainActivity : AppCompatActivity() {

    // ==========================================
    // DECLARACIÓN DE COMPONENTES Y VARIABLES
    // ==========================================
    private lateinit var containerMediaList: LinearLayout
    private lateinit var containerFolders: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText

    // Componentes del Reproductor Inferior (Mini Player estilo Spotify actual)
    private lateinit var layoutPlayerBar: MaterialCardView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var tvCurrentSong: TextView
    private lateinit var tvMiniArtist: TextView
    private lateinit var miniSeekBar: ProgressBar
    private lateinit var imgMiniAlbumArt: ImageView

    private val allMusicList = mutableListOf<MediaModel>()
    private var filteredMusicList = mutableListOf<MediaModel>()
    private var selectedFolder: String = "Todas"

    // Lista para almacenar las referencias de las tarjetas visuales
    private val mainCardViewsList = mutableListOf<MaterialCardView>()

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var updateSeekBarRunnable: Runnable

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            loadMp3Files()
        } else {
            Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_LONG).show()
        }
    }

    // ==========================================
    // CICLO DE VIDA: onCreate
    // ==========================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        containerMediaList = findViewById(R.id.containerMediaList)
        containerFolders = findViewById(R.id.containerFolders)
        tvEmpty = findViewById(R.id.tvEmpty)
        etSearch = findViewById(R.id.etSearch)

        // Referencia a la barra reproductor inferior (Mini Player) y sus componentes reales del XML
        layoutPlayerBar = findViewById(R.id.layoutPlayerBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        tvCurrentSong = findViewById(R.id.tvCurrentSong)
        tvMiniArtist = findViewById(R.id.tvMiniArtist)
        miniSeekBar = findViewById(R.id.miniSeekBar)
        imgMiniAlbumArt = findViewById(R.id.imgMiniAlbumArt)

        // Configuración para abrir PlayerActivity al hacer clic en la barra inferior
        layoutPlayerBar.setOnClickListener {
            val intent = Intent(this, PlayerActivity::class.java)
            startActivity(intent)
        }

        setupBottomNavigation()
        setupPlayerControls()
        setupSearch()
        checkPermissionAndLoad()
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
    // NAVEGACIÓN INFERIOR UNIFICADA
    // ==========================================
    private fun setupBottomNavigation() {
        val navHome = findViewById<TextView>(R.id.navHome)
        val navSearch = findViewById<TextView>(R.id.navSearch)
        val navLibrary = findViewById<TextView>(R.id.navLibrary)
        val navSettings = findViewById<TextView>(R.id.navSettings)

        navHome?.setOnClickListener {
            // Ya estamos en Home, no es necesario recargar
        }

        navSearch?.setOnClickListener {
            val intent = Intent(this, BuscarActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        navLibrary?.setOnClickListener {
            val intent = Intent(this, LibraryActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        navSettings?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }
    }

    // ==========================================
    // ESCUCHAS GLOBALES (MusicPlayerManager)
    // ==========================================
    private fun setupGlobalListeners() {
        MusicPlayerManager.onSongChangeListener = { media, _ ->
            tvCurrentSong.text = media.title
            tvMiniArtist.text = media.bucketName
            MusicPlayerManager.mediaPlayer?.let { miniSeekBar.max = it.duration }
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
        } catch (_: Exception) {
            imgMiniAlbumArt.setImageResource(R.drawable.default_song_cover)
        } finally {
            retriever.release()
        }
    }

    // ==========================================
    // RESALTADO VISUAL DE LA TARJETA ACTIVA
    // ==========================================
    private fun highlightCurrentPlayingSong() {
        val currentPlayingMedia = MusicPlayerManager.currentSongList.getOrNull(MusicPlayerManager.currentPlayingIndex)

        for ((idx, card) in mainCardViewsList.withIndex()) {
            val mediaInCard = filteredMusicList.getOrNull(idx)
            if (mediaInCard != null && currentPlayingMedia != null && mediaInCard.id == currentPlayingMedia.id) {
                card.setCardBackgroundColor(0xFF3B1E54.toInt())
                card.strokeColor = 0xFFA855F7.toInt()
                card.strokeWidth = 3
            } else {
                card.setCardBackgroundColor(0xFF1A2238.toInt())
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

    // ==========================================
    // CONTROLES DEL REPRODUCTOR
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
    // PERMISOS Y CARGA DE ARCHIVOS
    // ==========================================
    private fun checkPermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                loadMp3Files()
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "La app necesita acceso a la música para reproducirla", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(permission)
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun loadMp3Files() {
        allMusicList.clear()

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
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val bucketColumn = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: cursor.getString(displayNameColumn) ?: "Sin título"
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val bucket = if (bucketColumn != -1) cursor.getString(bucketColumn) ?: "Música" else "Música"
                val contentUri = ContentUris.withAppendedId(collection, id)

                allMusicList.add(MediaModel(id, title, contentUri, duration, size, bucket))
            }
        }

        setupFolderChips()
        filterAndDisplayMusic()
    }

    // ==========================================
    // FILTROS Y CHIPS
    // ==========================================
    private fun setupFolderChips() {
        containerFolders.removeAllViews()
        val folders = mutableSetOf("Todas")
        for (media in allMusicList) {
            folders.add(media.bucketName)
        }

        for (folder in folders) {
            val chipButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12
                }
                text = folder
                isCheckable = true
                isChecked = (folder == selectedFolder)

                setOnClickListener {
                    selectedFolder = folder
                    setupFolderChips()
                    filterAndDisplayMusic()
                }
            }
            containerFolders.addView(chipButton)
        }
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterAndDisplayMusic()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterAndDisplayMusic() {
        val query = etSearch.text.toString().lowercase(Locale.getDefault())

        filteredMusicList = allMusicList.filter { media ->
            val matchesFolder = (selectedFolder == "Todas" || media.bucketName == selectedFolder)
            val matchesSearch = media.title.lowercase(Locale.getDefault()).contains(query)
            matchesFolder && matchesSearch
        }.toMutableList()

        displayMusicList()
    }

    // ==========================================
    // RENDERIZADO VISUAL UTILIZANDO EL XML `item_song`
    // ==========================================
    private fun displayMusicList() {
        containerMediaList.removeAllViews()
        mainCardViewsList.clear()

        if (filteredMusicList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
            for ((index, media) in filteredMusicList.withIndex()) {

                val itemView = layoutInflater.inflate(R.layout.item_song, containerMediaList, false)

                val cardView = MaterialCardView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(24, 10, 24, 10)
                    }
                    radius = 20f * resources.displayMetrics.density
                    cardElevation = 4f * resources.displayMetrics.density

                    val isCurrentList = MusicPlayerManager.currentSongList == filteredMusicList
                    if (isCurrentList && index == MusicPlayerManager.currentPlayingIndex) {
                        setCardBackgroundColor(0xFF3B1E54.toInt())
                        strokeColor = 0xFFA855F7.toInt()
                        strokeWidth = 3
                    } else {
                        setCardBackgroundColor(0xFF1A2238.toInt())
                        strokeWidth = 0
                    }

                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        MusicPlayerManager.currentSongList = filteredMusicList
                        MusicPlayerManager.playSong(this@MainActivity, filteredMusicList, index)

                        val intent = Intent(this@MainActivity, PlayerActivity::class.java)
                        startActivity(intent)
                    }
                }

                mainCardViewsList.add(cardView)

                val iconView = itemView.findViewById<ImageView>(R.id.imgSongCover)
                val titleView = itemView.findViewById<TextView>(R.id.tvSongTitle)
                val artistDurationView = itemView.findViewById<TextView>(R.id.tvSongArtist)

                titleView.text = media.title

                val minutes = TimeUnit.MILLISECONDS.toMinutes(media.duration)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(media.duration) % 60
                artistDurationView.text = String.format(Locale.getDefault(), "%s • %02d:%02d", media.bucketName, minutes, seconds)

                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(this@MainActivity, media.uri)
                    val artBytes = retriever.embeddedPicture
                    if (artBytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                        iconView.setImageBitmap(bitmap)
                    } else {
                        iconView.setImageResource(R.drawable.default_song_cover)
                    }
                } catch (_: Exception) {
                    iconView.setImageResource(R.drawable.default_song_cover)
                } finally {
                    retriever.release()
                }

                cardView.addView(itemView)
                containerMediaList.addView(cardView)
            }
        }
        highlightCurrentPlayingSong()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
    }
}
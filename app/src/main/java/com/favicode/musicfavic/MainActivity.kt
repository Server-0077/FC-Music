package com.favicode.musicfavic

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class MediaModel(
    val id: Long,
    val title: String,
    val uri: Uri,
    val duration: Long,
    val size: Long,
    val bucketName: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var containerMediaList: LinearLayout
    private lateinit var containerFolders: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText

    // Componentes del Reproductor Inferior
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var tvCurrentSong: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar

    private var mediaPlayer: MediaPlayer? = null
    private val allMusicList = mutableListOf<MediaModel>()
    private var filteredMusicList = mutableListOf<MediaModel>()
    private var selectedFolder: String = "Todas"
    private var currentSongIndex: Int = -1

    private var isShuffleEnabled = false
    private var isRepeatEnabled = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        containerMediaList = findViewById(R.id.containerMediaList)
        containerFolders = findViewById(R.id.containerFolders)
        tvEmpty = findViewById(R.id.tvEmpty)
        etSearch = findViewById(R.id.etSearch)

        // Vincular controles de la barra inferior
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        tvCurrentSong = findViewById(R.id.tvCurrentSong)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        seekBar = findViewById(R.id.seekBar)

        setupPlayerControls()
        setupSearch()
        checkPermissionAndLoad()
    }

    private fun setupPlayerControls() {
        // Play / Pause
        btnPlayPause.setOnClickListener {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                } else {
                    player.start()
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                }
            } ?: Toast.makeText(this, "Selecciona una canción primero", Toast.LENGTH_SHORT).show()
        }

        // Siguiente canción
        btnNext.setOnClickListener {
            playNextSong()
        }

        // Canción anterior
        btnPrev.setOnClickListener {
            if (filteredMusicList.isNotEmpty()) {
                currentSongIndex = if (currentSongIndex - 1 < 0) filteredMusicList.size - 1 else currentSongIndex - 1
                playSong(filteredMusicList[currentSongIndex], currentSongIndex)
            }
        }

        // Modo Aleatorio (Shuffle)
        btnShuffle.setOnClickListener {
            isShuffleEnabled = !isShuffleEnabled
            if (isShuffleEnabled) {
                btnShuffle.setColorFilter(0xFFFFD700.toInt()) // Dorado activo
                Toast.makeText(this, "Aleatorio activado", Toast.LENGTH_SHORT).show()
            } else {
                btnShuffle.setColorFilter(0xFF808080.toInt()) // Gris inactivo
                Toast.makeText(this, "Aleatorio desactivado", Toast.LENGTH_SHORT).show()
            }
        }

        // Modo Repetir (Loop)
        btnRepeat.setOnClickListener {
            isRepeatEnabled = !isRepeatEnabled
            mediaPlayer?.isLooping = isRepeatEnabled
            if (isRepeatEnabled) {
                btnRepeat.setColorFilter(0xFFFFD700.toInt())
                Toast.makeText(this, "Repetición activada", Toast.LENGTH_SHORT).show()
            } else {
                btnRepeat.setColorFilter(0xFF808080.toInt())
                Toast.makeText(this, "Repetición desactivada", Toast.LENGTH_SHORT).show()
            }
        }

        // Control manual de la barra de progreso (SeekBar)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                    tvCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Bucle en tiempo real para actualizar la barra y los segundos transcurridos
        updateSeekBarRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val currentPosition = player.currentPosition
                        seekBar.progress = currentPosition
                        tvCurrentTime.text = formatTime(currentPosition.toLong())
                    }
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateSeekBarRunnable)
    }

    private fun playNextSong() {
        if (filteredMusicList.isEmpty()) return

        currentSongIndex = if (isShuffleEnabled) {
            Random.nextInt(filteredMusicList.size)
        } else {
            (currentSongIndex + 1) % filteredMusicList.size
        }
        playSong(filteredMusicList[currentSongIndex], currentSongIndex)
    }

    private fun playSong(media: MediaModel, index: Int) {
        currentSongIndex = index
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, media.uri).apply {
            isLooping = isRepeatEnabled
            setOnPreparedListener { player ->
                player.start()
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                tvCurrentSong.text = media.title
                tvTotalTime.text = formatTime(media.duration)
                seekBar.max = player.duration
            }
            setOnCompletionListener {
                if (!isRepeatEnabled) {
                    playNextSong()
                }
            }
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

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

    private fun displayMusicList() {
        containerMediaList.removeAllViews()

        if (filteredMusicList.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
            for ((index, media) in filteredMusicList.withIndex()) {
                val cardView = MaterialCardView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(24, 10, 24, 10)
                    }
                    radius = 20f * resources.displayMetrics.density
                    cardElevation = 4f * resources.displayMetrics.density
                    setCardBackgroundColor(0xFF1A2238.toInt())
                    isClickable = true
                    isFocusable = true

                    setOnClickListener {
                        playSong(media, index)
                    }
                }

                val innerLayout = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(20, 20, 20, 20)
                    gravity = Gravity.CENTER_VERTICAL
                }

                val iconView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        (42 * resources.displayMetrics.density).toInt(),
                        (42 * resources.displayMetrics.density).toInt()
                    )
                    setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                }

                val textLayout = LinearLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginStart = (14 * resources.displayMetrics.density).toInt()
                    }
                    orientation = LinearLayout.VERTICAL
                }

                val titleView = TextView(this).apply {
                    text = media.title
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    setTextColor(0xFFFFFFFF.toInt())
                }

                val folderSubtitle = TextView(this).apply {
                    text = getString(R.string.folder_label_format, media.bucketName)
                    textSize = 12f
                    setTextColor(0xFFB0B0B0.toInt())
                }

                val minutes = TimeUnit.MILLISECONDS.toMinutes(media.duration)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(media.duration) % 60
                val durationView = TextView(this).apply {
                    text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
                    textSize = 12f
                    setTextColor(0xFFFFD700.toInt())
                }

                textLayout.addView(titleView)
                textLayout.addView(folderSubtitle)
                textLayout.addView(durationView)

                innerLayout.addView(iconView)
                innerLayout.addView(textLayout)

                cardView.addView(innerLayout)
                containerMediaList.addView(cardView)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
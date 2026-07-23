package com.favicode.musicfavic

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class LibraryActivity : AppCompatActivity() {

    private lateinit var gridAlbums: GridLayout
    private val albumsMap = mutableMapOf<String, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)

        gridAlbums = findViewById(R.id.gridAlbums)
        gridAlbums.columnCount = 2

        // ==========================================
        // CONFIGURACIÓN DE LA NAVEGACIÓN INFERIOR
        // ==========================================
        val navHome = findViewById<TextView>(R.id.navHome)
        val navSearch = findViewById<TextView>(R.id.navSearch)
        val navSettings = findViewById<TextView>(R.id.navSettings)

        navHome.setOnClickListener {
            finish()
        }

        navSearch.setOnClickListener {
            // Reemplaza SearchActivity por el nombre exacto de tu clase de búsqueda
            val intent = Intent(this, BuscarActivity::class.java)
            startActivity(intent)
            finish()
        }

        navSettings.setOnClickListener {
            // Reemplaza SettingsActivity por el nombre exacto de tu clase de ajustes
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            finish()
        }

        loadRealAlbums()
    }

    private fun loadRealAlbums() {
        albumsMap.clear()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} = ?"
        val selectionArgs = arrayOf("audio/mpeg")

        contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            val bucketColumn = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val bucket = if (bucketColumn != -1) cursor.getString(bucketColumn) ?: getString(R.string.default_song_title) else getString(R.string.default_song_title)

                if (!bucket.equals("Favoritas", ignoreCase = true)) {
                    val count = albumsMap[bucket] ?: 0
                    albumsMap[bucket] = count + 1
                }
            }
        }

        displayAlbumsGrid()
    }

    private fun displayAlbumsGrid() {
        gridAlbums.removeAllViews()
        val density = resources.displayMetrics.density

        for ((folderName, songCount) in albumsMap) {
            val cardView = MaterialCardView(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                    setMargins(
                        (6 * density).toInt(),
                        (6 * density).toInt(),
                        (6 * density).toInt(),
                        (6 * density).toInt()
                    )
                }
                radius = 16f * density
                cardElevation = 4f * density
                setCardBackgroundColor(0xFF161622.toInt())
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    val intent = Intent(this@LibraryActivity, AlbumDetailActivity::class.java)
                    intent.putExtra("FOLDER_NAME", folderName)
                    startActivity(intent)
                }
            }

            val layoutVertical = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    (12 * density).toInt(),
                    (12 * density).toInt(),
                    (12 * density).toInt(),
                    (12 * density).toInt()
                )
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val albumImage = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (110 * density).toInt()
                )
                setImageResource(R.drawable.default_album)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val tvAlbumTitle = TextView(this).apply {
                text = folderName
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                maxLines = 1
                setPadding(0, (8 * density).toInt(), 0, (2 * density).toInt())
            }

            // Uso de recursos con formateador de strings para evitar advertencias de traducción y concatenación
            val tvSongCount = TextView(this).apply {
                text = getString(R.string.songs_count_format, songCount)
                textSize = 12f
                setTextColor(0xFF9E9EB5.toInt())
            }

            layoutVertical.addView(albumImage)
            layoutVertical.addView(tvAlbumTitle)
            layoutVertical.addView(tvSongCount)
            cardView.addView(layoutVertical)

            gridAlbums.addView(cardView)
        }
    }
}
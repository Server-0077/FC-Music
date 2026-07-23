package com.favicode.musicfavic

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class BuscarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buscar)

        setupNavigation()
    }

    private fun setupNavigation() {
        // Ir a Inicio
        findViewById<TextView>(R.id.navHome)?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Ya estás en Buscar, no requiere acción o puedes dejarlo libre

        // Ir a Biblioteca
        findViewById<TextView>(R.id.navLibrary)?.setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
            finish()
        }

        // Ir a Ajustes
        findViewById<TextView>(R.id.navSettings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
    }
}
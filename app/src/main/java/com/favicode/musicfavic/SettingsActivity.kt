package com.favicode.musicfavic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.UnknownHostException
import java.net.URL

class SettingsActivity : AppCompatActivity() {

    private lateinit var rgReportType: RadioGroup
    private lateinit var etReportDetails: EditText
    private lateinit var btnSendReport: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        rgReportType = findViewById(R.id.rgReportType)
        etReportDetails = findViewById(R.id.etReportDetails)
        btnSendReport = findViewById(R.id.btnSendReport)

        setupThemeSelectors()
        setupEmailJSReport()
        setupNavigation()
    }

    // ==========================================
    // CONFIGURACIÓN DE SELECTORES DE TEMAS (Persistente)
    // ==========================================
    private fun setupThemeSelectors() {
        val themeNative = findViewById<ImageView>(R.id.themeNative)
        val themeBlue = findViewById<ImageView>(R.id.themeBlue)
        val themeGreen = findViewById<ImageView>(R.id.themeGreen)
        val themeOrange = findViewById<ImageView>(R.id.themeOrange)
        val themePink = findViewById<ImageView>(R.id.themePink)

        themeNative.setOnClickListener { applyThemeColor("Nativo (Violeta)", "#A855F7") }
        themeBlue.setOnClickListener { applyThemeColor("Azul Neón", "#3B82F6") }
        themeGreen.setOnClickListener { applyThemeColor("Esmeralda", "#10B981") }
        themeOrange.setOnClickListener { applyThemeColor("Coral", "#F97316") }
        themePink.setOnClickListener { applyThemeColor("Rosa Fucsia", "#EC4899") }
    }

    private fun applyThemeColor(themeName: String, hexColor: String) {
        val prefs = getSharedPreferences("MusicFavicTheme", Context.MODE_PRIVATE)
        prefs.edit().putString("accent_color", hexColor).apply()

        Toast.makeText(this, "Tema aplicado: $themeName. Se mantendrá al reiniciar.", Toast.LENGTH_LONG).show()
    }

    // ==========================================
    // ENVÍO DE REPORTE AUTOMÁTICO DESDE LA APP
    // ==========================================
    private fun setupEmailJSReport() {
        btnSendReport.setOnClickListener {
            val selectedId = rgReportType.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(this, "Por favor selecciona un tipo de fallo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val rbSelected = findViewById<RadioButton>(selectedId)
            val reportCategory = rbSelected.text.toString()
            val extraDetails = etReportDetails.text.toString().trim()

            val messageBody = "⚠️ NUEVO REPORTE DESDE LA APP FC Music ⚠️\n\n- Usuario: User of App\n- Categoría: $reportCategory\n- Detalles adicionales: ${if (extraDetails.isEmpty()) "Ninguno" else extraDetails}"

            // Deshabilitar botón temporalmente
            btnSendReport.isEnabled = false
            btnSendReport.text = "Enviando reporte..."

            java.lang.Thread {
                var responseCode = -1
                var errorMessage: String? = null

                try {
                    val url = URL("https://api.emailjs.com/api/v1.0/email/send")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                    conn.setRequestProperty("Origin", "http://localhost")
                    conn.connectTimeout = 10000 // 10 segundos de timeout
                    conn.readTimeout = 10000
                    conn.doOutput = true
                    conn.doInput = true

                    val jsonParam = JSONObject().apply {
                        put("service_id", "service_5elbszl")
                        put("template_id", "template_j3gpian")
                        put("user_id", "3nwNlbB3g8rN_Kivp")
                        put("template_params", JSONObject().apply {
                            put("from_name", "[App MusicFavic]")
                            put("message", messageBody)
                            put("reply_to", "emersonarevalo77@gmail.com")
                            put("to_email", "emersonarevalo77@gmail.com")
                        })
                    }

                    val os = conn.outputStream
                    os.write(jsonParam.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                    os.close()

                    responseCode = conn.responseCode

                    runOnUiThread {
                        btnSendReport.isEnabled = true
                        btnSendReport.text = "Enviar Reporte por Correo"

                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            Toast.makeText(this, "¡Reporte enviado con éxito! Gracias por tus comentarios.", Toast.LENGTH_LONG).show()
                            etReportDetails.text.clear()
                            rgReportType.clearCheck()
                        } else {
                            // Manejo de códigos de respuesta del servidor de forma amigable
                            val friendlyMessage = when (responseCode) {
                                400, 401, 403 -> "Hubo un problema de autenticación con el servicio de correo. Inténtalo más tarde."
                                404 -> "No se pudo conectar con el servidor de reportes."
                                in 500..599 -> "Los servidores de correo están presentando fallas en este momento. Inténtalo más tarde."
                                else -> "No se pudo completar el envío. Código de error: $responseCode"
                            }
                            Toast.makeText(this, friendlyMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: UnknownHostException) {
                    runOnUiThread {
                        btnSendReport.isEnabled = true
                        btnSendReport.text = "Enviar Reporte por Correo"
                        Toast.makeText(this, "Sin conexión a internet. Verifica tu red o datos móviles e inténtalo de nuevo.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    runOnUiThread {
                        btnSendReport.isEnabled = true
                        btnSendReport.text = "Enviar Reporte por Correo"
                        Toast.makeText(this, "La red está tardando demasiado en responder. Comprueba tu conexión.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        btnSendReport.isEnabled = true
                        btnSendReport.text = "Enviar Reporte por Correo"
                        Toast.makeText(this, "Ocurrió un error inesperado al enviar el reporte. Por favor, verifica tu conexión a internet.", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    // ==========================================
    // NAVEGACIÓN INFERIOR (COMPLETA)
    // ==========================================
    private fun setupNavigation() {
        // Ir a Inicio
        findViewById<TextView>(R.id.navHome)?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Ir a Buscar
        findViewById<TextView>(R.id.navSearch)?.setOnClickListener {
            startActivity(Intent(this, BuscarActivity::class.java))
            finish()
        }

        // Ir a Biblioteca
        findViewById<TextView>(R.id.navLibrary)?.setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
            finish()
        }

        // Ya estás en Ajustes
        findViewById<TextView>(R.id.navSettingsActive)?.setOnClickListener {
            // Ya nos encontramos aquí
        }
    }
}
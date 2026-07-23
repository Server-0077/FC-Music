package com.favicode.musicfavic

import android.content.Context
import android.graphics.Color

object ThemeManager {
    private const val PREF_NAME = "MusicFavicTheme"
    private const val KEY_COLOR = "accent_color"

    // Colores disponibles
    const val COLOR_NATIVE = "#A855F7" // Violeta original
    const val COLOR_BLUE = "#3B82F6"
    const val COLOR_GREEN = "#10B981"
    const val COLOR_ORANGE = "#F97316"
    const val COLOR_PINK = "#EC4899"

    fun saveColor(context: Context, hexColor: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COLOR, hexColor).apply()
    }

    fun getCurrentColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val hex = prefs.getString(KEY_COLOR, COLOR_NATIVE) ?: COLOR_NATIVE
        return Color.parseColor(hex)
    }
}
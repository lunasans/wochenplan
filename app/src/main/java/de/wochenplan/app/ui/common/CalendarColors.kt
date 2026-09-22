package de.wochenplan.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

/** Farben der Kalender: vom Server uebernommen, sonst stabil aus dem Namen abgeleitet. */
object CalendarColors {

    private val palette = listOf(
        Color(0xFF3949AB),
        Color(0xFF00897B),
        Color(0xFFD81B60),
        Color(0xFF6D4C41),
        Color(0xFF00838F),
        Color(0xFF5E35B1),
        Color(0xFFEF6C00),
        Color(0xFF2E7D32),
    )

    fun resolve(hex: String?, key: String): Color = parseHex(hex) ?: palette[abs(key.hashCode()) % palette.size]

    /** Textfarbe mit ausreichendem Kontrast auf [background]. */
    fun contentColorOn(background: Color): Color =
        if (background.luminance() > 0.5f) Color(0xFF1B1B21) else Color.White

    private fun parseHex(hex: String?): Color? {
        val value = hex?.trim()?.removePrefix("#") ?: return null
        val rgb = when (value.length) {
            6 -> value
            8 -> value.substring(0, 6)
            3 -> value.map { "$it$it" }.joinToString("")
            else -> return null
        }
        val parsed = rgb.toLongOrNull(16) ?: return null
        return Color(0xFF000000 or parsed)
    }
}

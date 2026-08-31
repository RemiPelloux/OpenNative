package app.gamenative.enums

import androidx.compose.ui.graphics.Color

enum class AppTheme(val text: String, private val seedArgb: Long) {
    AUTO("System", 0xFF8B5CF6),
    DAY("Light", 0xFF8B5CF6),
    NIGHT("Dark", 0xFF8B5CF6),
    AMOLED("AMOLED", 0xFF8B5CF6),
    THOR("Thor Ember", 0xFFFF6A3D),
    OCEAN("Ocean", 0xFF00B4D8),
    FOREST("Forest", 0xFF34D399),
    DUSK("Dusk", 0xFFEC4899),
    SLATE("Slate", 0xFF94A3B8),
    ;

    val seedColor: Color get() = Color(seedArgb)

    val isAmoled: Boolean get() = this == AMOLED

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        AUTO -> systemDark
        DAY -> false
        else -> true
    }
}

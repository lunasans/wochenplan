package de.wochenplan.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Ruhige, kontraststarke Palette: Der Wochenplan lebt von den Farben der
// Kalender, das Geruest haelt sich bewusst zurueck.
private val Indigo40 = Color(0xFF4A5BB8)
private val Indigo80 = Color(0xFFB9C3FF)
private val Teal40 = Color(0xFF2C6E63)
private val Teal80 = Color(0xFF9FD5C9)
private val Amber40 = Color(0xFF8A5A00)
private val Amber80 = Color(0xFFFFD699)

val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBFECE1),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Amber40,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
    outlineVariant = Color(0xFFC7C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = Color(0xFF16277E),
    primaryContainer = Color(0xFF31409F),
    onPrimaryContainer = Color(0xFFDEE1FF),
    secondary = Teal80,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF12544A),
    onSecondaryContainer = Color(0xFFBFECE1),
    tertiary = Amber80,
    onTertiary = Color(0xFF492900),
    tertiaryContainer = Color(0xFF683D00),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF918F9A),
    outlineVariant = Color(0xFF46464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

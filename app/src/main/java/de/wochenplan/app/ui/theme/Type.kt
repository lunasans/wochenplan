package de.wochenplan.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaultTypography = Typography()

val WochenplanTypography = defaultTypography.copy(
    titleLarge = defaultTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = defaultTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = defaultTypography.labelSmall.copy(fontSize = 11.sp),
)

/** Ziffern des Timers: gleich breite Ziffern, damit nichts springt. */
val TimerTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Light,
    fontSize = 56.sp,
)

package com.glownote.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlowColors = lightColorScheme(
    primary = Color(0xFF2364AA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8FF),
    onPrimaryContainer = Color(0xFF08284A),
    secondary = Color(0xFFB66C2C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDBD),
    onSecondaryContainer = Color(0xFF3A1800),
    tertiary = Color(0xFF5A7350),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF17202A),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF17202A),
    surfaceVariant = Color(0xFFEDE7DD),
    onSurfaceVariant = Color(0xFF5D5A55),
    outline = Color(0xFFB7B0A5),
)

@Composable
fun GlowNoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GlowColors,
        typography = Typography(),
        content = content,
    )
}

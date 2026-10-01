package com.osx23.avi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AviColors = lightColorScheme(
    primary = Color(0xFF0B64D8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF083B7A),
    secondary = Color(0xFF41637A),
    background = Color(0xFFF4F7FA),
    surface = Color.White,
    onSurface = Color(0xFF16324A),
    error = Color(0xFFBA1A1A)
)

@Composable
fun AviTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AviColors, content = content)
}

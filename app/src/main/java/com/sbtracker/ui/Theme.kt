package com.sbtracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ember = Color(0xFFFF6B35)
private val Ink   = Color(0xFF0B0D10)
private val Paper = Color(0xFFF4F4F4)

private val DarkColors  = darkColorScheme(primary = Ember, background = Ink, surface = Ink)
private val LightColors = lightColorScheme(primary = Ember, background = Paper, surface = Paper)

@Composable
fun SBTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content     = content,
    )
}

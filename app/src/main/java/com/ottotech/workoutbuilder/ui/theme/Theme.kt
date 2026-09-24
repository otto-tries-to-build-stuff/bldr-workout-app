package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * [dark] is resolved by the caller (MainActivity) from the saved `ThemeMode`, because it also
 * decides the status- and navigation-bar icon colours, which live outside Compose.
 */
@Composable
fun WorkoutTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(content = content)
    }
}

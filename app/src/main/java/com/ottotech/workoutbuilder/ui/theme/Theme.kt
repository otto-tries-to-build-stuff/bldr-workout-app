package com.ottotech.workoutbuilder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
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
    CompositionLocalProvider(
        LocalAppColors provides if (dark) DarkColors else LightColors,
        LocalExecutionColors provides if (dark) DarkExecutionColors else LightExecutionColors,
    ) {
        MaterialTheme {
            // Safety net: Text with no AppType style still renders in Space Grotesk, not Roboto.
            ProvideTextStyle(AppType.cardMeta, content)
        }
    }
}

package com.ottotech.workoutbuilder

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ottotech.workoutbuilder.ui.AppNav
import com.ottotech.workoutbuilder.ui.brand.BldrLoader
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import com.ottotech.workoutbuilder.ui.theme.WorkoutTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WorkoutApp

        // Seeding runs before the UI is composed rather than alongside it. It is a few
        // hundred inserts on a cold start only, and gating on it avoids Home flashing its
        // "Nothing here yet" empty state for one frame before the seed lands.
        var ready by mutableStateOf(false)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Order matters: the sample workout references seeded exercise ids.
                app.exercises.seedIfNeeded()
                app.workouts.seedSampleIfNeeded(app.prefs)
            }
            ready = true
        }

        setContent {
            val mode by app.prefs.themeMode.collectAsStateWithLifecycle()
            val dark = mode.isDark(isSystemInDarkTheme())
            // Bar icons must follow the app's theme, not the phone's, or a Light choice on a dark
            // phone leaves white icons on a white status bar.
            LaunchedEffect(dark) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            WorkoutTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(LocalAppColors.current.background)) {
                    if (ready) AppNav() else LaunchLoader()
                }
            }
        }
    }
}

/**
 * Shown while [MainActivity] waits on seeding: ~10 s on a fresh install, a few frames otherwise.
 * It waits [LaunchLoaderDelayMs] before appearing, so a normal launch never flashes it.
 */
@Composable
private fun LaunchLoader() {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LaunchLoaderDelayMs)
        show = true
    }
    if (!show) return
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BldrLoader(width = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text("Getting ready", style = AppType.cardMeta, color = LocalAppColors.current.textMuted)
    }
}

private const val LaunchLoaderDelayMs = 300L


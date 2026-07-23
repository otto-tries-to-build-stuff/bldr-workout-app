package com.ottotech.workoutbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ottotech.workoutbuilder.ui.AppNav
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import com.ottotech.workoutbuilder.ui.theme.WorkoutTheme
import kotlinx.coroutines.Dispatchers
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
            WorkoutTheme {
                Box(Modifier.fillMaxSize().background(LocalAppColors.current.background)) {
                    if (ready) AppNav()
                }
            }
        }
    }
}

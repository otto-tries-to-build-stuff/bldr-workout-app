package com.ottotech.workoutbuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.ottotech.workoutbuilder.data.ExerciseRepository
import com.ottotech.workoutbuilder.data.db.AppDatabase
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import com.ottotech.workoutbuilder.ui.theme.WorkoutTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Placeholder shell for Phase 1A. It exists to prove the database seeds correctly and
 * the theme resolves; Plan 1B replaces this with the real Home screen.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val exercises = ExerciseRepository(AppDatabase.get(this).exerciseDao())

        var seededCount by mutableIntStateOf(-1)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                exercises.seedIfNeeded()
                seededCount = exercises.count()
            }
        }

        setContent {
            WorkoutTheme {
                val colors = LocalAppColors.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (seededCount < 0) "Seeding…" else "Library: $seededCount exercises",
                        style = AppType.heroName,
                        color = colors.onBackground,
                    )
                }
            }
        }
    }
}

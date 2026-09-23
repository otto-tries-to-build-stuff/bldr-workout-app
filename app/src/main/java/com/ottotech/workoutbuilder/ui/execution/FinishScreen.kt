package com.ottotech.workoutbuilder.ui.execution

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors

/**
 * Placeholder for the workout-complete screen. Task 7 replaces this with the full summary
 * (spec §6.7); this stub only needs to keep the execution screen compiling and let a finished
 * session be dismissed during Task 5 verification.
 */
@Composable
fun FinishScreen(workoutName: String, onDone: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(ExecutionColors.background).padding(Dimens.screenPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "$workoutName complete",
                style = AppType.moveName,
                color = ExecutionColors.onBackground,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .height(Dimens.primaryButtonHeight)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusButton))
                    .background(ExecutionColors.onBackground)
                    .clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) {
                Text("Done", style = AppType.primaryButton, color = ExecutionColors.background)
            }
        }
    }
}

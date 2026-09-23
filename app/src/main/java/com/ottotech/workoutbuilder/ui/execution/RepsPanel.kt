package com.ottotech.workoutbuilder.ui.execution

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.domain.Segment
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors

/**
 * Placeholder for the REPS set checklist. Task 6 replaces this with the full sets/reps/weight
 * tracker (spec §6.6); this stub only needs to keep the execution screen compiling and let a
 * REPS segment be advanced past during Task 5 verification.
 */
@Composable
fun RepsPanel(segment: Segment, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val setsReps = listOfNotNull(
            segment.sets?.let { "$it sets" },
            segment.reps?.let { "$it reps" },
        ).joinToString(" · ")
        Text(
            setsReps.ifBlank { "Reps" },
            style = AppType.moveName,
            color = ExecutionColors.onBackground,
        )
        Box(
            modifier = Modifier
                .height(Dimens.primaryButtonHeight)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.radiusButton))
                .background(ExecutionColors.onBackground)
                .clickable(onClick = onDone)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Done ›", style = AppType.primaryButton, color = ExecutionColors.background)
        }
    }
}

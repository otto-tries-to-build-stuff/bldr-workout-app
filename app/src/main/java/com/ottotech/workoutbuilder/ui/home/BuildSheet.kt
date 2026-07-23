package com.ottotech.workoutbuilder.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/**
 * Spec §6.2. Two equal-weight bordered cards — building it yourself and asking the Coach
 * are two ways to do the same thing, so neither gets the filled treatment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildSheet(
    onDismiss: () -> Unit,
    onBuildYourself: () -> Unit,
    onAskCoach: () -> Unit,
) {
    val colors = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.screenPadding)
                .padding(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            SectionLabel("Build a workout")
            Spacer(Modifier.height(2.dp))
            Option(
                title = "Build it yourself",
                subtitle = "Pick exercises from the library",
                onClick = onBuildYourself,
            )
            Option(
                title = "Ask the Coach",
                subtitle = "Describe your goal, get a workout drafted",
                onClick = onAskCoach,
            )
        }
    }
}

@Composable
private fun Option(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    HairlineCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
            Text(title, style = AppType.cardTitle, color = colors.onBackground)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = AppType.cardMeta, color = colors.textMuted)
        }
    }
}

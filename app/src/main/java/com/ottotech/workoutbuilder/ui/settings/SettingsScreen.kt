package com.ottotech.workoutbuilder.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ottotech.workoutbuilder.domain.ThemeMode
import com.ottotech.workoutbuilder.ui.brand.BldrLogo
import com.ottotech.workoutbuilder.ui.components.AppTextField
import com.ottotech.workoutbuilder.ui.components.Chip
import com.ottotech.workoutbuilder.ui.components.ConfirmDialog
import com.ottotech.workoutbuilder.ui.components.HairlineCard
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.ScreenHeader
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.components.TextAction
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

private const val KEYS_URL = "https://console.anthropic.com/settings/keys"

/**
 * Spec §6.7: the Coach's API key, the theme and the cue sound switch (keep-screen-on is deferred).
 * The saved key is never shown back, not even masked — the field only takes a new one.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            // Pushed outside AppScaffold, so nothing else keeps the list's end clear of a
            // 3-button nav bar (edge-to-edge is on).
            .navigationBarsPadding(),
    ) {
        ScreenHeader(title = "Settings", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item { SectionLabel("Anthropic API key") }

            if (state.hasKey) {
                item {
                    HairlineCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Dimens.cardPadding, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Key saved", style = AppType.cardTitle, color = colors.onBackground)
                                Text("Stored encrypted on this phone", style = AppType.cardMeta, color = colors.textMuted)
                            }
                            TextAction("Remove", onClick = { confirmRemove = true })
                        }
                    }
                }
            } else {
                item {
                    AppTextField(
                        value = state.draftKey,
                        onValueChange = viewModel::onDraftKey,
                        placeholder = "sk-ant-…",
                        masked = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.draftError?.let { error ->
                    item { Text(error, style = AppType.cardMeta, color = colors.textMuted) }
                }
                item {
                    InverseButton(
                        text = "SAVE KEY",
                        height = Dimens.primaryButtonHeight,
                        style = AppType.primaryButton,
                        onClick = viewModel::saveKey,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    TextAction("Get a key at console.anthropic.com", onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KEYS_URL)))
                    })
                }
            }

            item {
                Text(
                    "The Coach sends your goal and the exercise library to Anthropic using this key. " +
                        "Usage is billed to your Anthropic account — roughly 5–15¢ per generated workout.",
                    style = AppType.cardMeta,
                    color = colors.textMuted,
                )
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { SectionLabel("Appearance") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        Chip(mode.label, selected = state.theme == mode, onClick = { viewModel.onTheme(mode) })
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { SectionLabel("Sound") }
            item {
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.minTouchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Workout cues", style = AppType.cardTitle, color = colors.onBackground, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.soundOn,
                        onCheckedChange = viewModel::onSoundOn,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colors.surfaceInverse,
                            checkedThumbColor = colors.onSurfaceInverse,
                            checkedBorderColor = colors.surfaceInverse,
                            uncheckedTrackColor = colors.surface,
                            uncheckedThumbColor = colors.textMuted,
                            uncheckedBorderColor = colors.border,
                        ),
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { SectionLabel("About") }
            item {
                val version = remember {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BldrLogo(height = 24.dp)
                    Text("Version $version", style = AppType.cardMeta, color = colors.textMuted)
                    Text(
                        "Set in Archivo and Space Grotesk, used under the SIL Open Font License.",
                        style = AppType.cardMeta,
                        color = colors.textMuted,
                    )
                }
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "Remove your API key?",
            body = "The Coach won't work until you add one again.",
            confirmLabel = "Remove",
            dismissLabel = "Cancel",
            onConfirm = { confirmRemove = false; viewModel.removeKey() },
            onDismiss = { confirmRemove = false },
        )
    }
}

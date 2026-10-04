package com.ottotech.workoutbuilder.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.components.ScreenHeader
import com.ottotech.workoutbuilder.ui.components.SectionLabel
import com.ottotech.workoutbuilder.ui.components.TextAction
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

private const val ANTHROPIC_PRIVACY_URL = "https://www.anthropic.com/legal/privacy"

/** Bold lead-in, then the rest of the point. */
private val KEY_REASONS = listOf(
    "You control the cost." to "Every charge shows up in your own Anthropic account, where you can set a spending limit.",
    "You can switch it off any time." to "Remove the key in Settings, or delete it in the Claude Console.",
    "Nobody in the middle." to "Your requests go from your phone straight to Anthropic. BLDR never sees them.",
    "Your key stays locked away." to "It's stored encrypted on this phone and never shown again, even to you.",
)

/**
 * Plain-language privacy notes, opened from Settings. Keep the wording true to the app:
 * allowBackup is off, and the Coach's request (CoachPrompt) is the only thing sent off the phone.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(title = "Privacy", onBack = onBack)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            SectionLabel("Your data stays on your phone")
            Body(
                "Your workouts, exercises, history and settings are saved only on this phone. " +
                    "BLDR has no accounts, no ads and no tracking. Nothing is backed up to the cloud, " +
                    "so uninstalling the app deletes it all.",
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel("When you use the Coach")
            Body(
                "Asking the Coach for a workout is the only time anything leaves your phone. It sends what " +
                    "you type, the length and equipment you pick, and the names of the exercises in your " +
                    "library. It goes straight to Anthropic, the company behind Claude, and never to us. " +
                    "Your workout history is never sent.",
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel("Why you bring your own key")
            KEY_REASONS.forEach { (lead, rest) ->
                Row {
                    Text("•", style = AppType.heroMeta, color = colors.onBackground, modifier = Modifier.width(18.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(lead) }
                            append(" $rest")
                        },
                        style = AppType.heroMeta,
                        color = colors.onBackground,
                    )
                }
            }

            TextAction("Read Anthropic's privacy policy", onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ANTHROPIC_PRIVACY_URL)))
            })
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = AppType.heroMeta, color = LocalAppColors.current.onBackground)
}

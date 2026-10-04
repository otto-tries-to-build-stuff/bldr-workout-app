package com.ottotech.workoutbuilder.ui.help

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.components.LinkCard
import com.ottotech.workoutbuilder.ui.components.ScreenHeader
import com.ottotech.workoutbuilder.ui.components.TextAction
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

/** Settings → How to use BLDR: the list of topics. */
@Composable
fun HelpScreen(onOpenTopic: (HelpTopic) -> Unit, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(title = "How to", onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            HelpTopic.entries.forEach { topic ->
                LinkCard(topic.title, topic.summary, onClick = { onOpenTopic(topic) })
            }
        }
    }
}

/**
 * One topic: the animation pinned at the top, the steps scrolling beneath it. The step the
 * animation is showing is lit; tapping a step jumps the animation to it.
 */
@Composable
fun HelpTopicScreen(topic: HelpTopic, onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val clock = rememberScriptClock(topic.script)
    // Derived, so only a change of step recomposes the list, not every animation frame.
    val lit by remember(clock) { derivedStateOf { clock.position.step } }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        ScreenHeader(title = "How to", onBack = onBack)
        Text(
            topic.title,
            style = AppType.heroName,
            color = colors.onBackground,
            modifier = Modifier.padding(horizontal = Dimens.screenPadding),
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f), contentAlignment = Alignment.Center) {
            MiniPhone { TopicScene(topic, clock) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.screenPadding - 8.dp, end = Dimens.screenPadding, top = 12.dp, bottom = 24.dp),
        ) {
            topic.steps.forEachIndexed { i, step ->
                StepRow(number = i + 1, text = step, lit = i == lit, onClick = { clock.show(i) })
            }
            if (topic == HelpTopic.SetUpCoach) {
                TextAction(
                    "Open Claude Console",
                    modifier = Modifier.padding(start = 8.dp),
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KEYS_URL))) },
                )
            }
        }
    }
}

@Composable
private fun StepRow(number: Int, text: String, lit: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val ink by animateColorAsState(if (lit) colors.onBackground else colors.textMuted, label = "stepInk")
    val badge by animateColorAsState(if (lit) colors.surfaceInverse else colors.surface, label = "stepBadge")
    val badgeInk by animateColorAsState(if (lit) colors.onSurfaceInverse else colors.onSurface, label = "stepBadgeInk")
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.minTouchTarget)
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .clickable(onClickLabel = "Show this step", onClick = onClick)
            .semantics { selected = lit }
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
            Text("$number", style = AppType.chip, color = badgeInk)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            buildAnnotatedString {
                boldRuns(text).forEach { (run, bold) ->
                    if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(run) } else append(run)
                }
            },
            style = AppType.heroMeta,
            color = ink,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

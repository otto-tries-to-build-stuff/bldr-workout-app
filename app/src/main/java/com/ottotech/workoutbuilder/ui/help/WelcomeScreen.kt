package com.ottotech.workoutbuilder.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.brand.BldrLoader
import com.ottotech.workoutbuilder.ui.components.InverseButton
import com.ottotech.workoutbuilder.ui.components.ScreenTitle
import com.ottotech.workoutbuilder.ui.components.TextAction
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * The welcome tour: five swipeable cards, shown on first open and replayable from Settings.
 * Skip on every card but the last, which ends on GET STARTED instead. Back steps to the
 * previous card rather than leaving.
 */
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val colors = LocalAppColors.current
    val cards = WelcomeCard.entries
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == cards.lastIndex

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(Dimens.minTouchTarget).padding(horizontal = Dimens.screenPadding),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!last) TextAction("Skip", color = colors.textMuted, onClick = onDone)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            // Only the card in view plays; a neighbour peeking in mid-swipe holds still.
            WelcomePage(cards[page], playing = pager.currentPage == page)
        }
        PageDots(count = cards.size, current = pager.currentPage)
        Spacer(Modifier.height(18.dp))
        InverseButton(
            text = if (last) "GET STARTED" else "NEXT",
            onClick = {
                if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding),
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WelcomePage(card: WelcomeCard, playing: Boolean) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = Dimens.screenPadding)) {
        Box(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            val script = card.script
            if (script == null) {
                // The brand's own build-the-B, with headroom above it for the falling bars.
                BldrLoader(width = 104.dp, contentDescription = "BLDR", modifier = Modifier.padding(top = 24.dp))
            } else {
                val clock = rememberScriptClock(script, playing)
                MiniPhone { WelcomeScene(card, clock.position) }
            }
        }
        Spacer(Modifier.height(24.dp))
        ScreenTitle(card.title, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        Text(card.body, style = AppType.heroMeta, color = colors.onBackground)
        Spacer(Modifier.height(20.dp))
    }
}

/** The current card as a stretched pill, the rest as dots. */
@Composable
private fun PageDots(count: Int, current: Int) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Card ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 8.dp, label = "dotWidth")
            val tint by animateColorAsState(if (i == current) colors.onBackground else colors.textDisabled, label = "dotTint")
            Box(Modifier.size(width = width, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(tint))
        }
    }
}

@Composable
private fun WelcomeScene(card: WelcomeCard, pos: ScenePosition) {
    when (card) {
        WelcomeCard.Welcome -> Unit
        WelcomeCard.Build -> TourBuildScene(pos)
        WelcomeCard.Coach -> TourCoachScene(pos)
        WelcomeCard.Train -> TourTrainScene(pos)
        WelcomeCard.Private -> TourPrivateScene(pos)
    }
}

@Composable
private fun TourBuildScene(pos: ScenePosition) {
    val rows = pos.progressOf(TourBuildBeat.RowsIn)
    MiniBuilder(
        title = "BUILD",
        name = "Leg day",
        restOn = true,
        rounds = if (pos.progressOf(TourBuildBeat.Rounds) > 0.4f) "3" else "1",
        items = listOf(
            MiniExercise("Goblet squat", pos.progressOf(TourBuildBeat.Flip) > 0.4f, ease(window(rows, 0f, 0.45f))),
            MiniExercise("Reverse lunge", false, ease(window(rows, 0.35f, 0.8f))),
        ),
        timedTap = pos.during(TourBuildBeat.Flip),
        plusTap = pos.during(TourBuildBeat.Rounds),
    )
}

@Composable
private fun TourCoachScene(pos: ScenePosition) {
    val type = pos.progressOf(TourCoachBeat.Type)
    when {
        !pos.reached(TourCoachBeat.Think) -> MiniCoachForm(
            goal = "30 min legs, dumbbells".take((22 * window(type, 0.05f, 0.6f)).toInt()),
            typing = type < 0.62f,
            length = type > 0.68f,
            dumbbell = type > 0.68f,
            lengthTap = 0f,
            kitTap = 0f,
            goTap = window(type, 0.72f, 1f),
        )
        !pos.reached(TourCoachBeat.Card) -> MiniCoachLoading()
        else -> Box(Modifier.fillMaxSize()) {
            MiniCoachLoading()
            Box(Modifier.fillMaxSize().slideY(1f - ease(pos.progressOf(TourCoachBeat.Card)))) {
                MiniCoachDraft(name = "Dumbbell legs")
            }
        }
    }
}

@Composable
private fun TourTrainScene(pos: ScenePosition) {
    // Seconds gone, summed over the beats so the ring keeps counting straight through the dimming.
    val elapsed = pos.progressOf(TourTrainBeat.Count) * 1.4f + pos.progressOf(TourTrainBeat.Voice) * 1.4f +
        pos.progressOf(TourTrainBeat.Dim) * 1.8f + pos.progressOf(TourTrainBeat.Hold) * 0.6f
    val remaining = 30f - elapsed
    val voice = pos.during(TourTrainBeat.Voice)
    val bubble = window(voice, 0f, 0.15f) * (1f - window(voice, 0.85f, 1f))
    Box(Modifier.fillMaxSize()) {
        MiniExecution(name = "Squats", position = 3, remaining = remaining)
        if (bubble > 0f) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ExecutionColors.onBackground.copy(alpha = bubble))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text("“Next up: Lunges”", style = AppType.cardTitle, color = ExecutionColors.background.copy(alpha = bubble))
            }
        }
        if (pos.reached(TourTrainBeat.Dim)) {
            MiniLockScreen(dim = ease(window(pos.progressOf(TourTrainBeat.Dim), 0f, 0.3f)), voice = 0f, remaining = remaining.toInt(), exercise = "Squats")
        }
    }
}

@Composable
private fun TourPrivateScene(pos: ScenePosition) {
    val colors = LocalAppColors.current
    val slide = ease(pos.progressOf(TourPrivateBeat.Slide))
    val locked = ease(pos.progressOf(TourPrivateBeat.Lock))
    Column(
        Modifier.fillMaxSize().background(colors.background).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        // The workout drops in from above the screen's top edge.
        Box(Modifier.fillMaxWidth().slideY(-2.5f * (1f - slide))) {
            MiniWorkoutCard("Leg day", "24 MIN", "2 exercises · Strength", "Never done")
        }
        Spacer(Modifier.height(40.dp))
        Padlock(closed = locked)
        Spacer(Modifier.height(20.dp))
        Text("Stored on this phone", style = AppType.cardTitle, color = colors.onBackground.copy(alpha = locked))
        Spacer(Modifier.weight(1.2f))
    }
}

/** A padlock drawn in the mono palette; the shackle drops shut as [closed] goes 0..1. */
@Composable
private fun Padlock(closed: Float) {
    val ink = LocalAppColors.current.onBackground
    Canvas(Modifier.width(72.dp).height(92.dp)) {
        val stroke = 9.dp.toPx()
        val bodyTop = size.height * 0.45f
        val shackleWidth = size.width * 0.62f
        val left = (size.width - shackleWidth) / 2f
        val lift = 14.dp.toPx() * (1f - closed)
        val arcTop = 4.dp.toPx() + stroke / 2f - lift
        val legTop = arcTop + shackleWidth / 2f
        val inBody = bodyTop + 2.dp.toPx()
        drawArc(
            color = ink,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(left, arcTop),
            size = Size(shackleWidth, shackleWidth),
            style = Stroke(stroke),
        )
        // The right leg stays in the body; the left one lifts clear of it while open.
        drawLine(ink, Offset(left + shackleWidth, legTop), Offset(left + shackleWidth, inBody), stroke)
        drawLine(ink, Offset(left, legTop), Offset(left, inBody - lift), stroke)
        drawRoundRect(
            color = ink,
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, size.height - bodyTop),
            cornerRadius = CornerRadius(12.dp.toPx()),
        )
    }
}

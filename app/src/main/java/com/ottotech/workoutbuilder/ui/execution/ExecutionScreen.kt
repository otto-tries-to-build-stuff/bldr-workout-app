package com.ottotech.workoutbuilder.ui.execution

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ottotech.workoutbuilder.domain.ItemMode
import com.ottotech.workoutbuilder.domain.Segment
import com.ottotech.workoutbuilder.domain.SegmentKind
import com.ottotech.workoutbuilder.ui.demoIntent
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors
import kotlinx.coroutines.delay

@Composable
fun ExecutionScreen(viewModel: ExecutionViewModel, onExit: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.session

    // The service owns the clock; the screen samples it so the ring moves smoothly. Gated on
    // the lifecycle so this 10Hz recomposition loop actually pauses when the app is
    // backgrounded rather than spinning unseen — the service's own clock is what must keep
    // running with the screen off, not this UI sampler.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(100L)
            }
        }
    }

    // Spec §7: the screen stays awake for the length of the workout. Scoped to this screen
    // and released on the way out, so it can never leak into the rest of the app.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // A running workout asks before quitting; a finished one (or the brief window before the
    // service has published its first state) has nothing left to confirm — Back there does
    // exactly what Done/the close button does, so it never leaves the service running with no
    // UI able to stop it.
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler {
        if (session == null || session.finished) onExit() else confirmExit = true
    }

    if (session == null) {
        Box(Modifier.fillMaxSize().background(ExecutionColors.background))
        return
    }
    if (session.finished) {
        FinishScreen(workoutName = state.workoutName, onDone = onExit)
        return
    }

    val segment = session.current ?: return

    Column(
        Modifier
            .fillMaxSize()
            .background(ExecutionColors.background)
            .statusBarsPadding()
            // This screen is a full-screen route outside AppScaffold, so — unlike every
            // bottom-anchored surface inside it — it inherits no nav-bar inset on its own.
            // Edge-to-edge is on (enableEdgeToEdge in MainActivity), so without this the
            // Pause/Skip row sits inside the 3-button nav bar and taps land on Back/Home.
            .navigationBarsPadding()
            .padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExecutionHeader(
            title = "${state.workoutName.uppercase()} · ${session.workIndex}/${session.workTotal}",
            muted = state.muted,
            onToggleMute = viewModel::toggleMute,
            onClose = { confirmExit = true },
        )
        // workIndex is 1-based and includes the in-progress exercise; done must count only
        // what's actually finished, or the bar reads fully solid on an exercise not yet done.
        ProgressBar(done = session.workIndex - 1, total = session.workTotal)

        Spacer(Modifier.height(24.dp))
        Text(
            segment.exerciseName,
            style = AppType.moveName,
            color = ExecutionColors.onBackground,
            textAlign = TextAlign.Center,
        )
        // REPS mode renders its own "Chest · Barbell" sub-line (RepsPanel) that already
        // carries the muscle — showing the tag here too would say it twice on screen.
        if (segment.mode != ItemMode.REPS) {
            segment.primaryMuscle?.let {
                Spacer(Modifier.height(8.dp))
                Text(it.label.uppercase(), style = AppType.muscleTag, color = ExecutionColors.textMuted)
            }
        }

        Spacer(Modifier.height(28.dp))
        when (segment.mode) {
            ItemMode.TIMED -> {
                val remaining = session.remainingSecDisplay(now) ?: 0
                val total = (segment.durationSec ?: 1).coerceAtLeast(1)
                TimerRing(
                    progress = remaining.toFloat() / total,
                    centreTop = formatClock(remaining),
                    centreBottom = "REMAINING",
                )
            }
            ItemMode.MANUAL -> TimerRing(
                progress = 1f,
                centreTop = formatClock((session.elapsedMs(now) / 1000L).toInt()),
                centreBottom = "ELAPSED",
                onTap = viewModel::complete,
            )
            ItemMode.REPS -> RepsPanel(segment = segment)
        }

        Spacer(Modifier.weight(1f))
        if (segment.kind == SegmentKind.WORK) {
            WatchDemoCard(segment)
            Spacer(Modifier.height(10.dp))
        }
        UpNextCard(session.segments.getOrNull(session.index + 1))
        Spacer(Modifier.height(14.dp))
        ExecutionControls(
            paused = session.isPaused,
            mode = segment.mode,
            onPauseResume = { if (session.isPaused) viewModel.resume() else viewModel.pause() },
            onAdvance = viewModel::skip,
            onDone = viewModel::complete,
        )
        Spacer(Modifier.height(20.dp))
    }

    if (confirmExit) {
        DiscardWorkoutDialog(
            onKeepGoing = { confirmExit = false },
            onQuit = { confirmExit = false; onExit() },
        )
    }
}

/** mm:ss for a minute or more, bare seconds below that — matches the reference app. */
private fun formatClock(seconds: Int): String =
    if (seconds >= 60) "%d:%02d".format(seconds / 60, seconds % 60) else seconds.toString()

/** `WORKOUT NAME · 2/5`, mute toggle, close — spec §6.5. */
@Composable
private fun ExecutionHeader(
    title: String,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = AppType.sectionLabel,
            color = ExecutionColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(Dimens.minTouchTarget)
                .clip(RoundedCornerShape(Dimens.radiusChip))
                .clickable(onClick = onToggleMute)
                // The glyph alone is silent to a screen reader; the label names both the
                // action and the current state, like a physical mute button's own label would.
                .semantics { contentDescription = if (muted) "Unmute cues" else "Mute cues" },
            contentAlignment = Alignment.Center,
        ) {
            MuteGlyph(muted = muted, color = ExecutionColors.onBackground)
        }
        Box(
            modifier = Modifier
                .size(Dimens.minTouchTarget)
                .clip(RoundedCornerShape(Dimens.radiusChip))
                .clickable(onClick = onClose)
                .semantics { contentDescription = "Quit workout" },
            contentAlignment = Alignment.Center,
        ) {
            // Cleared so TalkBack announces the Box's "Quit workout" label instead of the
            // glyph character itself.
            Text(
                "✕",
                style = AppType.cardTitle,
                color = ExecutionColors.onBackground,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** A small speaker glyph, canvas-drawn to match [com.ottotech.workoutbuilder.ui.components.HomeGlyph] and friends. */
@Composable
private fun MuteGlyph(muted: Boolean, color: Color, size: Dp = 20.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val bodyWidth = w * 0.32f
        val bodyHeight = h * 0.42f
        val bodyTop = (h - bodyHeight) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        val cone = Path().apply {
            moveTo(bodyWidth, bodyTop)
            lineTo(w * 0.6f, 0f)
            lineTo(w * 0.6f, h)
            lineTo(bodyWidth, bodyTop + bodyHeight)
            close()
        }
        drawPath(cone, color = color)
        if (muted) {
            drawLine(
                color = color,
                start = Offset(w * 0.66f, h * 0.14f),
                end = Offset(w, h * 0.86f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        } else {
            drawArc(
                color = color,
                startAngle = -40f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(w * 0.66f, h * 0.12f),
                size = Size(w * 0.34f, h * 0.76f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

/** One segment per WORK item (rests are not steps) — the first [done] filled, the rest track-coloured. */
@Composable
private fun ProgressBar(done: Int, total: Int) {
    if (total <= 0) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .height(Dimens.progressBarHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.progressBarGap),
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < done) ExecutionColors.ringProgress else ExecutionColors.ringTrack),
            )
        }
    }
}

/** Opens the exercise's demo video. Hidden entirely when the segment has no demo link. */
@Composable
private fun WatchDemoCard(segment: Segment) {
    val hasDemo = !segment.demoUrl.isNullOrBlank() || !segment.demoQuery.isNullOrBlank()
    if (!hasDemo) return
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(ExecutionColors.card)
            .clickable { context.startActivity(demoIntent(segment.demoUrl, segment.demoQuery)) }
            .padding(horizontal = Dimens.cardPadding, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PlayGlyph(color = ExecutionColors.onBackground)
            Text("Watch demo", style = AppType.cardTitle, color = ExecutionColors.onBackground)
        }
    }
}

@Composable
private fun PlayGlyph(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val triangle = Path().apply {
            moveTo(w * 0.2f, 0f)
            lineTo(w, h / 2f)
            lineTo(w * 0.2f, h)
            close()
        }
        drawPath(triangle, color = color)
    }
}

/** `UP NEXT` over the following segment's name, or "Last one" at the end of the workout. */
@Composable
private fun UpNextCard(next: Segment?) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(Dimens.hairline, ExecutionColors.border), shape)
            .padding(horizontal = Dimens.cardPadding, vertical = 14.dp),
    ) {
        Column {
            Text("UP NEXT", style = AppType.heroLabel, color = ExecutionColors.textMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                next?.exerciseName ?: "Last one",
                style = AppType.cardTitle,
                color = ExecutionColors.onBackground,
            )
        }
    }
}

/** Pause/Resume always; Skip for a TIMED segment, Done for MANUAL and REPS — spec §6.5. */
@Composable
private fun ExecutionControls(
    paused: Boolean,
    mode: ItemMode,
    onPauseResume: () -> Unit,
    onAdvance: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ControlButton(
            text = if (paused) "Resume" else "Pause",
            filled = false,
            onClick = onPauseResume,
            modifier = Modifier.weight(1f),
        )
        val (label, action) = when (mode) {
            ItemMode.TIMED -> "Skip ›" to onAdvance
            ItemMode.MANUAL, ItemMode.REPS -> "Done ›" to onDone
        }
        ControlButton(
            text = label,
            filled = true,
            onClick = action,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ControlButton(text: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Dimens.radiusButton)
    val container = if (filled) ExecutionColors.onBackground else ExecutionColors.card
    val content = if (filled) ExecutionColors.background else ExecutionColors.onBackground
    Box(
        modifier = modifier
            .height(Dimens.primaryButtonHeight)
            .clip(shape)
            .background(container)
            .then(
                if (!filled) {
                    Modifier.border(BorderStroke(Dimens.hairline, ExecutionColors.borderStrong), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppType.primaryButton, color = content)
    }
}

/** "Quit this workout?" confirmation, styled after `BuilderScreen`'s discard dialog. */
@Composable
private fun DiscardWorkoutDialog(onKeepGoing: () -> Unit, onQuit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        containerColor = ExecutionColors.card,
        title = { Text("Quit this workout?", style = AppType.cardTitle, color = ExecutionColors.onBackground) },
        text = {
            Text(
                "Your progress won't be saved.",
                style = AppType.cardMeta,
                color = ExecutionColors.textMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onQuit) {
                Text("Quit", style = AppType.chip, color = ExecutionColors.onBackground)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepGoing) {
                Text("Keep going", style = AppType.chip, color = ExecutionColors.onBackground)
            }
        },
    )
}

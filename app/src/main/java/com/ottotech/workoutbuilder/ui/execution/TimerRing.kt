package com.ottotech.workoutbuilder.ui.execution

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.ExecutionColors

/**
 * The countdown ring. Starts at 12 o'clock and sweeps clockwise (spec §6.5).
 *
 * [progress] is 0f..1f of the ring that should be drawn. No animation spec: the value is
 * already driven by the service's 10 Hz tick, and animating on top of a moving target makes
 * the sweep lag behind the digits.
 */
@Composable
fun TimerRing(
    progress: Float,
    centreTop: String,
    centreBottom: String,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
) {
    val tappable = if (onTap != null) {
        Modifier.clip(CircleShape).clickable(onClick = onTap)
    } else {
        Modifier
    }
    Box(modifier.size(Dimens.ringSize).then(tappable), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(Dimens.ringSize)) {
            val stroke = Dimens.ringStroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = ExecutionColors.ringTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = ExecutionColors.ringProgress,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(centreTop, style = AppType.timerDigits, color = ExecutionColors.onBackground)
            Text(centreBottom, style = AppType.timerCaption, color = ExecutionColors.textMuted)
        }
    }
}

package com.ottotech.workoutbuilder.execution

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import com.ottotech.workoutbuilder.MainActivity

/**
 * The persistent notification that keeps the service in the foreground.
 *
 * Not decoration: a `mediaPlayback` foreground service is required to post one, and it is
 * what lets the timer keep running once the app is backgrounded.
 */
object ExecutionNotification {

    const val CHANNEL_ID = "workout_execution"
    const val ID = 1

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Running workout",
            NotificationManager.IMPORTANCE_LOW, // silent: the cues are the audio, not this
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        title: String,
        text: String,
        paused: Boolean,
        sessionToken: MediaSession.Token? = null,
    ): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            // SINGLE_TOP + MainActivity's singleTop launch mode: if it's already running (the
            // normal case — this is the only way back into a backgrounded workout), reuse that
            // instance and its navigation state instead of stacking a second one on Home.
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(label: String, command: String) = Notification.Action.Builder(
            null as android.graphics.drawable.Icon?,
            label,
            PendingIntent.getService(
                context,
                command.hashCode(),
                Intent(context, ExecutionService::class.java).setAction(command),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                if (paused) action("Resume", ExecutionService.ACTION_RESUME)
                else action("Pause", ExecutionService.ACTION_PAUSE),
            )
            .addAction(action("Skip", ExecutionService.ACTION_SKIP))
            .apply {
                // Task 8 supplies the token; until then the notification is plain.
                sessionToken?.let { style = Notification.MediaStyle().setMediaSession(it) }
            }
            .build()
    }
}

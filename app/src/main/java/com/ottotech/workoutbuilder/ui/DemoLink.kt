package com.ottotech.workoutbuilder.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ottotech.workoutbuilder.domain.Exercise
import com.ottotech.workoutbuilder.ui.components.TextAction

/**
 * Spec §7. An implicit ACTION_VIEW on a youtube.com URL, which the YouTube app claims when
 * installed and the browser handles otherwise — no YouTube SDK, no API key, no package
 * check. A per-exercise `demoUrl` overrides the search.
 *
 * Lives in `ui/` rather than `domain/` because it returns an android.content.Intent, and
 * `domain/` is enforced Android-free by DomainPurityTest.
 */
fun demoIntent(demoUrl: String?, demoQuery: String?): Intent {
    val url = demoUrl?.takeIf { it.isNotBlank() }
        ?: "https://www.youtube.com/results?search_query=" + Uri.encode(demoQuery.orEmpty())
    return Intent(Intent.ACTION_VIEW, Uri.parse(url))
}

fun demoIntent(exercise: Exercise): Intent = demoIntent(exercise.demoUrl, exercise.demoQuery)

/** The "Demo" link beside an exercise (Overview, library lists). Its own 48 dp tap target, so it never triggers the row. */
@Composable
fun DemoAction(exercise: Exercise) {
    val context = LocalContext.current
    TextAction(
        "Demo",
        onClick = { context.startActivity(demoIntent(exercise)) },
        modifier = Modifier.semantics { contentDescription = "Watch a demo of ${exercise.name}" },
    )
}

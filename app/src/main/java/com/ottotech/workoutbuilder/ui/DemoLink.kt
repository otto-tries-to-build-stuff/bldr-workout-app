package com.ottotech.workoutbuilder.ui

import android.content.Intent
import android.net.Uri
import com.ottotech.workoutbuilder.domain.Exercise

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

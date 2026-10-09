package com.ottotech.workoutbuilder.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ottotech.workoutbuilder.MainActivity
import com.ottotech.workoutbuilder.WorkoutApp
import com.ottotech.workoutbuilder.domain.ThemeMode
import com.ottotech.workoutbuilder.execution.ExecutionService
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Walks the real app through its main screens and saves a picture of each, in light and dark,
 * to `app/build/screenshots/<theme>/<screen>.png`. Used for before/after design previews
 * (see `.claude/skills/design-preview`). Only runs with `gradlew testDebugUnitTest -Pscreenshots`.
 *
 * The screen size matches a Pixel 10 Pro (412 × 915 dp), drawn at 2× to keep files small.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xhdpi")
@OptIn(ExperimentalTestApi::class)
class ScreenshotTour(private val theme: ThemeMode) {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<WorkoutApp>()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Test
    fun welcome() {
        launch(welcomeSeen = false)
        waitForText("Skip")
        shoot("welcome")
    }

    @Test
    fun tour() {
        launch(welcomeSeen = true)
        waitForText(SAMPLE_WORKOUT)
        shoot("home")

        tap("BUILD A WORKOUT")
        waitForText("Ask the Coach")
        shoot("build-sheet")
        tap("Ask the Coach")
        waitForText("Add your API key first")
        shoot("coach-no-key")
        back()

        tap(SAMPLE_WORKOUT)
        waitForText("START")
        shoot("overview")
        tap("Edit")
        waitForText("SAVE")
        shoot("builder")
        back()
        back()

        tap("Settings")
        waitForText("How do I get a key?")
        shoot("settings")
        tap("How to use BLDR")
        shoot("help")
        back()
        tap("Privacy")
        shoot("privacy")
        back()
        back()

        tap("Library")
        waitForText("LIBRARY")
        shoot("library")
        tap("Chest")
        compose.waitForIdle()
        shoot("category-chest")
    }

    @Test
    fun workout() {
        launch(welcomeSeen = true)
        tap(SAMPLE_WORKOUT)
        tap("START")
        // The screen asks the workout service to start; Robolectric records that request but
        // doesn't run services, so start it by hand.
        val service = Robolectric.buildService(ExecutionService::class.java, shadowOf(app).nextStartedService)
        service.create().startCommand(0, 1)
        try {
            waitForText("Leg Swings")
            shoot("workout-get-ready")

            // Past the get-ready countdown into the first exercise. The service's clock ticks on
            // a real background thread, so give it real time to notice the jump.
            ShadowSystemClock.advanceBy(Duration.ofSeconds(15))
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("First up").fetchSemanticsNodes().isEmpty()
            }
            shoot("workout-timer")
        } finally {
            // The workout's state outlives the service, so end it or the next theme's run
            // finds this workout already going.
            ExecutionService.stop(app)
            service.withIntent(shadowOf(app).nextStartedService).startCommand(0, 2)
        }
    }

    private fun launch(welcomeSeen: Boolean) {
        app.prefs.welcomeSeen = welcomeSeen
        app.prefs.setThemeMode(theme)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitForText(text: String, timeoutMillis: Long = 30_000) {
        compose.waitUntil(timeoutMillis) { exists(text) }
        compose.waitForIdle()
    }

    private fun exists(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun tap(text: String) {
        // Give the screen a moment to load; if the item still isn't there, it's further down a
        // list that only composes what's on screen, so scroll the list to it.
        runCatching { waitForText(text, timeoutMillis = 5_000) }
        if (!exists(text)) {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true))
        }
        waitForText(text)
        compose.onAllNodesWithText(text, substring = true).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun back() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun shoot(screen: String) {
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/${theme.name.lowercase()}/$screen.png")
    }

    companion object {
        private const val SAMPLE_WORKOUT = "Court Speed"

        @JvmStatic
        @Parameters(name = "{0}")
        fun themes() = listOf(ThemeMode.LIGHT, ThemeMode.DARK)
    }
}

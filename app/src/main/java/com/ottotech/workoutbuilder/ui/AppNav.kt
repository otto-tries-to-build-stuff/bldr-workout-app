package com.ottotech.workoutbuilder.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.ui.components.AppScaffold
import com.ottotech.workoutbuilder.ui.components.Tab
import com.ottotech.workoutbuilder.ui.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val CATEGORY = "library/{muscle}"
    const val EXERCISE = "exercise/{exerciseId}"

    fun category(muscle: Muscle) = "library/${muscle.name}"
    fun exercise(exerciseId: String) = "exercise/$exerciseId"
}

/**
 * Switching bottom-nav tabs must not stack destinations — otherwise Back walks through
 * every tap the user made instead of leaving the app.
 */
private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            AppScaffold(tab = Tab.HOME, onSelectTab = { if (it == Tab.LIBRARY) nav.switchTab(Routes.LIBRARY) }) {
                HomeScreen(
                    // TODO(Plan 1D): route to the execution screen.
                    onStartWorkout = { },
                    // TODO(Plan 1C): raise the Build sheet. Wired in Task 5.
                    onBuild = { },
                )
            }
        }

        composable(Routes.LIBRARY) {
            AppScaffold(tab = Tab.LIBRARY, onSelectTab = { if (it == Tab.HOME) nav.switchTab(Routes.HOME) }) {
                Text("Library")
            }
        }

        composable(
            route = Routes.CATEGORY,
            arguments = listOf(navArgument("muscle") { type = NavType.StringType }),
        ) { entry ->
            val muscle = Muscle.valueOf(entry.arguments?.getString("muscle") ?: Muscle.CHEST.name)
            Text(muscle.label)
        }

        composable(
            route = Routes.EXERCISE,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) { entry ->
            Text(entry.arguments?.getString("exerciseId").orEmpty())
        }
    }
}

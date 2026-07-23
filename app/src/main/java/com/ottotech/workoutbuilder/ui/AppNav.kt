package com.ottotech.workoutbuilder.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.ui.components.AppScaffold
import com.ottotech.workoutbuilder.ui.components.Tab
import com.ottotech.workoutbuilder.ui.home.BuildSheet
import com.ottotech.workoutbuilder.ui.home.HomeScreen
import com.ottotech.workoutbuilder.ui.library.CategoryScreen
import com.ottotech.workoutbuilder.ui.library.ExerciseDetailScreen
import com.ottotech.workoutbuilder.ui.library.FiltersSheet
import com.ottotech.workoutbuilder.ui.library.LibraryScreen
import com.ottotech.workoutbuilder.ui.library.LibraryViewModel

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
            // The two Build destinations do not exist yet; the sheet is the seam they
            // plug into. Left as no-ops rather than placeholder screens.
            var showBuildSheet by rememberSaveable { mutableStateOf(false) }

            AppScaffold(tab = Tab.HOME, onSelectTab = { if (it == Tab.LIBRARY) nav.switchTab(Routes.LIBRARY) }) {
                HomeScreen(
                    // TODO(Plan 1D): route to the execution screen.
                    onStartWorkout = { },
                    onBuild = { showBuildSheet = true },
                )
            }

            if (showBuildSheet) {
                BuildSheet(
                    onDismiss = { showBuildSheet = false },
                    // TODO(Plan 1C): navigate to the Builder.
                    onBuildYourself = { showBuildSheet = false },
                    // TODO(Plan 2): navigate to the Coach.
                    onAskCoach = { showBuildSheet = false },
                )
            }
        }

        composable(Routes.LIBRARY) {
            val libraryVm: LibraryViewModel = viewModel(
                viewModelStoreOwner = nav.getBackStackEntry(Routes.LIBRARY),
                factory = LibraryViewModel.Factory,
            )
            var showFilters by rememberSaveable { mutableStateOf(false) }

            AppScaffold(tab = Tab.LIBRARY, onSelectTab = { if (it == Tab.HOME) nav.switchTab(Routes.HOME) }) {
                LibraryScreen(
                    viewModel = libraryVm,
                    onOpenCategory = { nav.navigate(Routes.category(it)) },
                    onOpenFilters = { showFilters = true },
                )
            }

            if (showFilters) {
                FiltersSheet(viewModel = libraryVm, onDismiss = { showFilters = false })
            }
        }

        composable(
            route = Routes.CATEGORY,
            arguments = listOf(navArgument("muscle") { type = NavType.StringType }),
        ) { entry ->
            val muscle = Muscle.valueOf(entry.arguments?.getString("muscle") ?: Muscle.CHEST.name)
            // Scoped to the Library entry so the filter set on the grid is still in force.
            val libraryVm: LibraryViewModel = viewModel(
                viewModelStoreOwner = nav.getBackStackEntry(Routes.LIBRARY),
                factory = LibraryViewModel.Factory,
            )
            CategoryScreen(
                muscle = muscle,
                viewModel = libraryVm,
                onBack = { nav.popBackStack() },
                onOpenExercise = { nav.navigate(Routes.exercise(it)) },
            )
        }

        composable(
            route = Routes.EXERCISE,
            arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
        ) { entry ->
            val libraryVm: LibraryViewModel = viewModel(
                viewModelStoreOwner = nav.getBackStackEntry(Routes.LIBRARY),
                factory = LibraryViewModel.Factory,
            )
            ExerciseDetailScreen(
                exerciseId = entry.arguments?.getString("exerciseId").orEmpty(),
                viewModel = libraryVm,
                onBack = { nav.popBackStack() },
            )
        }
    }
}

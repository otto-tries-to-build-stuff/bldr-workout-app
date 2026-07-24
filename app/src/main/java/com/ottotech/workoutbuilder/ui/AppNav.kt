package com.ottotech.workoutbuilder.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ottotech.workoutbuilder.ui.builder.BuilderScreen
import com.ottotech.workoutbuilder.ui.builder.BuilderViewModel
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
    const val BUILDER = "builder"
    const val PICK_LIBRARY = "pick"
    const val PICK_CATEGORY = "pick/{muscle}"

    fun category(muscle: Muscle) = "library/${muscle.name}"
    fun exercise(exerciseId: String) = "exercise/$exerciseId"
    fun pickCategory(muscle: Muscle) = "pick/${muscle.name}"
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
                    onBuildYourself = {
                        showBuildSheet = false
                        nav.navigate(Routes.BUILDER)
                    },
                    // TODO(Plan 2): navigate to the Coach.
                    onAskCoach = { showBuildSheet = false },
                )
            }
        }

        composable(Routes.LIBRARY) {
            // Scope to the current entry (this *is* the Library entry) rather than looking
            // it up by route. Looking it up crashes on the way out: a bottom-nav tab switch
            // pops Library while its exit transition is still composing this frame, and
            // getBackStackEntry("library") then throws because the entry is already gone.
            // The default scope is the same ViewModelStore the child screens share below.
            val libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
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

        composable(Routes.BUILDER) {
            val builderVm: BuilderViewModel = viewModel(factory = BuilderViewModel.Factory)
            BuilderScreen(
                viewModel = builderVm,
                onClose = { nav.popBackStack() },
                onAddExercise = { sectionId ->
                    builderVm.beginPick(sectionId)
                    nav.navigate(Routes.PICK_LIBRARY)
                },
                onSaved = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }

        composable(Routes.PICK_LIBRARY) {
            // A fresh, pick-scoped Library filter — independent of the Library tab's state.
            val pickVm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
            var showFilters by rememberSaveable { mutableStateOf(false) }

            LibraryScreen(
                viewModel = pickVm,
                onOpenCategory = { nav.navigate(Routes.pickCategory(it)) },
                onOpenFilters = { showFilters = true },
            )
            if (showFilters) {
                FiltersSheet(viewModel = pickVm, onDismiss = { showFilters = false })
            }
        }

        composable(
            route = Routes.PICK_CATEGORY,
            arguments = listOf(navArgument("muscle") { type = NavType.StringType }),
        ) { entry ->
            val muscle = Muscle.valueOf(entry.arguments?.getString("muscle") ?: Muscle.CHEST.name)
            // Resolve the shared owners once. Picking pops both PICK_LIBRARY and PICK_CATEGORY,
            // and this frame recomposes during the exit transition — re-calling
            // getBackStackEntry(PICK_LIBRARY) then would throw because the entry is already
            // gone. Remembering the owners reuses the cached ones and avoids the lookup.
            val pickOwner = remember { nav.getBackStackEntry(Routes.PICK_LIBRARY) }
            val builderOwner = remember { nav.getBackStackEntry(Routes.BUILDER) }
            val pickVm: LibraryViewModel = viewModel(
                viewModelStoreOwner = pickOwner,
                factory = LibraryViewModel.Factory,
            )
            val builderVm: BuilderViewModel = viewModel(
                viewModelStoreOwner = builderOwner,
                factory = BuilderViewModel.Factory,
            )
            CategoryScreen(
                muscle = muscle,
                viewModel = pickVm,
                onBack = { nav.popBackStack() },
                onOpenExercise = { exerciseId ->
                    builderVm.onExercisePicked(exerciseId)
                    nav.popBackStack(Routes.BUILDER, inclusive = false)
                },
                pickMode = true,
            )
        }
    }
}

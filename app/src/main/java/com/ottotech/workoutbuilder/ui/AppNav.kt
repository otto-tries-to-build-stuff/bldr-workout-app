package com.ottotech.workoutbuilder.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ottotech.workoutbuilder.domain.Muscle
import com.ottotech.workoutbuilder.ui.builder.BuilderScreen
import com.ottotech.workoutbuilder.ui.builder.BuilderViewModel
import com.ottotech.workoutbuilder.ui.coach.CoachScreen
import com.ottotech.workoutbuilder.ui.coach.CoachViewModel
import com.ottotech.workoutbuilder.ui.components.AppScaffold
import com.ottotech.workoutbuilder.ui.components.Tab
import com.ottotech.workoutbuilder.ui.execution.ExecutionScreen
import com.ottotech.workoutbuilder.ui.execution.ExecutionViewModel
import com.ottotech.workoutbuilder.ui.home.BuildSheet
import com.ottotech.workoutbuilder.ui.home.HomeScreen
import com.ottotech.workoutbuilder.ui.library.CategoryScreen
import com.ottotech.workoutbuilder.ui.library.ExerciseDetailScreen
import com.ottotech.workoutbuilder.ui.library.FiltersSheet
import com.ottotech.workoutbuilder.ui.library.LibraryScreen
import com.ottotech.workoutbuilder.ui.library.LibraryViewModel
import com.ottotech.workoutbuilder.ui.overview.OverviewScreen
import com.ottotech.workoutbuilder.ui.overview.OverviewViewModel
import com.ottotech.workoutbuilder.ui.settings.SettingsScreen
import com.ottotech.workoutbuilder.ui.settings.SettingsViewModel

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val CATEGORY = "library/{muscle}"
    const val EXERCISE = "exercise/{exerciseId}"
    /** New workout without the argument, edit of a saved one with it. */
    const val BUILDER = "builder?workoutId={workoutId}"
    const val PICK_LIBRARY = "pick"
    const val PICK_CATEGORY = "pick/{muscle}"
    const val OVERVIEW = "workout/{workoutId}"
    const val EXECUTION = "execution/{workoutId}"
    const val SETTINGS = "settings"
    const val COACH = "coach"

    fun category(muscle: Muscle) = "library/${muscle.name}"
    fun exercise(exerciseId: String) = "exercise/$exerciseId"
    fun pickCategory(muscle: Muscle) = "pick/${muscle.name}"
    fun overview(workoutId: String) = "workout/$workoutId"
    fun execution(workoutId: String) = "execution/$workoutId"
    fun builder(workoutId: String? = null) = if (workoutId == null) "builder" else "builder?workoutId=$workoutId"
}

/**
 * Back, ignored once this destination has started leaving. A second tap on "‹" during the exit
 * transition would otherwise pop the destination underneath too — and popping Home leaves an
 * empty back stack, i.e. a blank screen. Use this for every back action.
 */
@Composable
private fun NavHostController.popOnce(): () -> Unit = dropUnlessResumed { popBackStack() }

/** Forward navigation with the same guard, so a double tap cannot open a screen twice. */
@Composable
private fun NavHostController.navigateOnce(route: String): () -> Unit = dropUnlessResumed { navigate(route) }

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
            // The Build sheet forks to the two ways of making a workout: the Builder or the Coach.
            var showBuildSheet by rememberSaveable { mutableStateOf(false) }

            AppScaffold(tab = Tab.HOME, onSelectTab = { if (it == Tab.LIBRARY) nav.switchTab(Routes.LIBRARY) }) {
                HomeScreen(
                    onOpenWorkout = { workoutId -> nav.navigate(Routes.overview(workoutId)) },
                    onEditWorkout = { workoutId -> nav.navigate(Routes.builder(workoutId)) },
                    onOpenSettings = nav.navigateOnce(Routes.SETTINGS),
                    onBuild = { showBuildSheet = true },
                )
            }

            val openCoach = nav.navigateOnce(Routes.COACH)
            if (showBuildSheet) {
                BuildSheet(
                    onDismiss = { showBuildSheet = false },
                    onBuildYourself = {
                        showBuildSheet = false
                        nav.navigate(Routes.builder())
                    },
                    onAskCoach = {
                        showBuildSheet = false
                        openCoach()
                    },
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
                onBack = nav.popOnce(),
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
                onBack = nav.popOnce(),
            )
        }

        composable(
            route = Routes.BUILDER,
            arguments = listOf(
                navArgument("workoutId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            val builderVm: BuilderViewModel = viewModel(factory = BuilderViewModel.Factory)
            BuilderScreen(
                viewModel = builderVm,
                onClose = nav.popOnce(),
                onAddExercise = { sectionId ->
                    builderVm.beginPick(sectionId)
                    nav.navigate(Routes.PICK_LIBRARY)
                },
                // Back to wherever the edit began: Home, or the overview of the workout just changed.
                onSaved = nav.popOnce(),
            )
        }

        composable(
            route = Routes.OVERVIEW,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) { entry ->
            val workoutId = entry.arguments?.getString("workoutId").orEmpty()
            val overviewVm: OverviewViewModel = viewModel(factory = OverviewViewModel.Factory)
            OverviewScreen(
                viewModel = overviewVm,
                onBack = nav.popOnce(),
                onEdit = nav.navigateOnce(Routes.builder(workoutId)),
                onStart = nav.navigateOnce(Routes.execution(workoutId)),
            )
        }

        composable(Routes.COACH) {
            val coachVm: CoachViewModel = viewModel(factory = CoachViewModel.Factory)
            CoachScreen(
                viewModel = coachVm,
                onBack = nav.popOnce(),
                onOpenSettings = nav.navigateOnce(Routes.SETTINGS),
                onSaved = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }

        composable(Routes.SETTINGS) {
            val settingsVm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            SettingsScreen(viewModel = settingsVm, onBack = nav.popOnce())
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
                onBack = nav.popOnce(),
                onOpenExercise = { exerciseId ->
                    builderVm.onExercisePicked(exerciseId)
                    nav.popBackStack(Routes.BUILDER, inclusive = false)
                },
                pickMode = true,
            )
        }

        composable(
            route = Routes.EXECUTION,
            arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
        ) { entry ->
            val workoutId = entry.arguments?.getString("workoutId").orEmpty()
            val executionVm: ExecutionViewModel = viewModel(factory = ExecutionViewModel.Factory)

            // Android 13+ will not show the notification unless the user has granted
            // POST_NOTIFICATIONS, and without the notification the foreground service — and
            // with it the whole point of this phase — is on borrowed time. Ask once, at the
            // first workout start, which is the moment it is self-explanatory. Starting the
            // workout does not wait on the answer: a declined prompt should cost the
            // notification, not the workout.
            val askNotifications = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* granted or not, the workout runs */ }

            // Rotation (or any Activity recreation — no configChanges is declared) recreates
            // this whole composable and, with it, a fresh LaunchedEffect(workoutId). A plain
            // `remember` would not survive that and ACTION_START would refire — restarting an
            // already-finished workout the user never asked to repeat (begin()'s guard only
            // ignores a restart of an UNFINISHED session, since a deliberate restart of the
            // same workout from its overview must still work). rememberSaveable does survive: this is
            // the same backstack entry being recomposed, so its SavedStateHandle carries
            // `started` through. A genuinely fresh navigation here (including restarting this
            // same workout from its overview) always creates a brand-new backstack entry instead, with
            // its own fresh `false`.
            var started by rememberSaveable(workoutId) { mutableStateOf(false) }
            LaunchedEffect(workoutId) {
                if (started) return@LaunchedEffect
                started = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                executionVm.start(workoutId)   // once per entry, not per recomposition
            }
            ExecutionScreen(
                viewModel = executionVm,
                onExit = {
                    executionVm.stop()
                    // Back to the workout's overview, ready to go again or pick another.
                    nav.popBackStack(Routes.OVERVIEW, inclusive = false)
                },
            )
        }
    }
}

package ru.petproject.giribro.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.collect
import ru.petproject.giribro.AppViewModel
import ru.petproject.giribro.data.SetCaptureMode
import ru.petproject.giribro.data.WorkoutDraftJson
import ru.petproject.giribro.data.WorkoutDraftStore
import ru.petproject.giribro.timer.TimerPhase
import ru.petproject.giribro.timer.TimerPlanSource
import ru.petproject.giribro.timer.TimerService
import ru.petproject.giribro.vision.RepExercise
import ru.petproject.giribro.ui.components.OnboardingDialog
import ru.petproject.giribro.ui.screens.BodyScreen
import ru.petproject.giribro.ui.screens.CameraRepScreen
import ru.petproject.giribro.ui.screens.DiaryScreen
import ru.petproject.giribro.ui.screens.NewWorkoutScreen
import ru.petproject.giribro.ui.screens.NormsScreen
import ru.petproject.giribro.ui.screens.ProfileScreen
import ru.petproject.giribro.ui.screens.SettingsScreen
import ru.petproject.giribro.ui.screens.StatsScreen
import ru.petproject.giribro.ui.screens.TimerScreen
import ru.petproject.giribro.ui.screens.WorkoutCompletionDialog
import ru.petproject.giribro.ui.screens.asTodayWorkout
import ru.petproject.giribro.ui.screens.workoutShareText
import java.time.LocalDate

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val accent: Boolean = false
)

private val bottomItems = listOf(
    BottomItem("diary", "Дневник", Icons.Outlined.FitnessCenter),
    BottomItem("stats", "Метрики", Icons.Outlined.Insights),
    BottomItem("camera-rep", "Счётчик", Icons.Outlined.PhotoCamera, accent = true),
    BottomItem("timer", "Таймер", Icons.Outlined.Timer),
    BottomItem("profile", "Профиль", Icons.Outlined.Person)
)

private val profileRoutes = setOf("profile", "body", "norms", "settings")

private fun cameraRepRoute(
    exercise: RepExercise?,
    mode: SetCaptureMode = SetCaptureMode.NONE,
    durationSec: Int = 0
): String {
    val exerciseArg = exercise?.name.orEmpty()
    return "camera-rep?exercise=$exerciseArg&mode=${mode.name}&duration=$durationSec"
}

@Composable
fun AppRoot(
    vm: AppViewModel,
    shortcutRoute: String? = null,
    onShortcutConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: "diary"
    val baseRoute = route.substringBefore('?')
    val settings by vm.settings.collectAsStateWithLifecycle()
    val onboardingShown by vm.onboardingShown.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val timerState by TimerService.state.collectAsStateWithLifecycle()
    val timerFullScreen = baseRoute == "timer" &&
        (timerState.isActive || timerState.phase == TimerPhase.DONE)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val draftStore = remember(context.applicationContext) {
        WorkoutDraftStore(context.applicationContext)
    }

    // По завершении тренировки пользователь сам решает, что делать с результатом.
    // Для обычного интервального таймера диалог не показывается.
    val completionDraft = remember(timerState.workoutDraftJson) {
        timerState.workoutDraftJson.takeIf { it.isNotBlank() }
            ?.let(WorkoutDraftJson::decode)
    }
    var completionDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(timerState.phase) {
        if (timerState.phase != TimerPhase.DONE) completionDismissed = false
    }
    val showCompletionDialog = timerState.phase == TimerPhase.DONE &&
        timerState.plan?.source == TimerPlanSource.WORKOUT &&
        completionDraft != null && !completionDismissed

    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            snackbarHostState.showSnackbar(
                message = message.text,
                withDismissAction = true,
                duration = if (message.isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    LaunchedEffect(shortcutRoute) {
        val target = shortcutRoute ?: return@LaunchedEffect
        navController.navigate(target) {
            launchSingleTop = true
        }
        onShortcutConsumed()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (baseRoute != "new" && !timerFullScreen) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = if (item.route == "profile") {
                                baseRoute in profileRoutes
                            } else {
                                baseRoute == item.route
                            },
                            onClick = {
                                val target = if (item.route == "camera-rep") {
                                    cameraRepRoute(null)
                                } else {
                                    item.route
                                }
                                if (baseRoute != item.route) {
                                    navController.navigate(target) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (item.accent) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            item.icon,
                                            contentDescription = item.label,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = "diary"
            ) {
                composable("diary") {
                    DiaryScreen(
                        vm = vm,
                        onAddWorkout = { navController.navigate("new") },
                        onEditWorkout = { workout ->
                            navController.navigate("new?workoutId=${workout.id}")
                        },
                        onRepeatWorkout = { workout ->
                            navController.navigate("new?workoutId=${workout.id}&repeat=true")
                        }
                    )
                }
                composable("stats") { StatsScreen(vm) }
                composable("profile") {
                    ProfileScreen(
                        vm = vm,
                        onOpenBody = { navController.navigate("body") },
                        onOpenNorms = { navController.navigate("norms?tab=norms") },
                        onOpenCalendar = { navController.navigate("norms?tab=calendar") },
                        onOpenSettings = { navController.navigate("settings") }
                    )
                }
                composable("body") {
                    BodyScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "norms?tab={tab}",
                    arguments = listOf(
                        navArgument("tab") {
                            type = NavType.StringType
                            defaultValue = "norms"
                        }
                    )
                ) { entry ->
                    NormsScreen(
                        vm = vm,
                        initialTab = entry.arguments?.getString("tab") ?: "norms",
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("timer") {
                    TimerScreen(
                        onBack = {
                            navController.navigate("diary") {
                                launchSingleTop = true
                            }
                        },
                        onOpenCounter = { exercise ->
                            navController.navigate(cameraRepRoute(exercise))
                        },
                        onAutoOpenCamera = { exercise, mode, durationSec ->
                            navController.navigate(cameraRepRoute(exercise, mode, durationSec))
                        }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        vm = vm,
                        onBack = { navController.popBackStack() },
                        onOpenCameraCount = { navController.navigate(cameraRepRoute(null)) }
                    )
                }
                composable(
                    route = "camera-rep?exercise={exercise}&mode={mode}&duration={duration}",
                    arguments = listOf(
                        navArgument("exercise") {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument("mode") {
                            type = NavType.StringType
                            defaultValue = SetCaptureMode.NONE.name
                        },
                        navArgument("duration") {
                            type = NavType.IntType
                            defaultValue = 0
                        }
                    )
                ) { entry ->
                    val exercise = runCatching {
                        RepExercise.valueOf(entry.arguments?.getString("exercise").orEmpty())
                    }.getOrNull()
                    val mode = SetCaptureMode.fromName(entry.arguments?.getString("mode"))
                    CameraRepScreen(
                        onBack = { navController.popBackStack() },
                        initialExercise = exercise,
                        initialPreset = settings.fixationPreset,
                        initialCaptureMode = mode,
                        initialDurationSec = entry.arguments?.getInt("duration") ?: 0
                    )
                }
                composable(
                    route = "new?workoutId={workoutId}&repeat={repeat}",
                    arguments = listOf(
                        navArgument("workoutId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                        navArgument("repeat") {
                            type = NavType.BoolType
                            defaultValue = false
                        }
                    )
                ) { entry ->
                    val workoutId = entry.arguments?.getLong("workoutId") ?: -1L
                    val isRepeat = entry.arguments?.getBoolean("repeat") ?: false
                    val workouts by vm.workouts.collectAsStateWithLifecycle()
                    val source = workouts.firstOrNull { it.id == workoutId }
                    NewWorkoutScreen(
                        defaultWeight = settings.defaultWeight,
                        quickValues = settings.quickValues,
                        gppExercises = settings.gppExercises,
                        onRememberGppExercise = vm::addGppExercise,
                        initialWorkout = source,
                        repeat = isRepeat,
                        restoreDraft = workoutId < 0L,
                        saving = saving,
                        onSave = { workout, onSaved ->
                            vm.saveWorkout(workout) {
                                onSaved()
                                if (navController.currentDestination?.route
                                        ?.startsWith("new") == true
                                ) {
                                    navController.popBackStack()
                                }
                            }
                        },
                        onClose = { navController.popBackStack() },
                        onOpenCounter = { exercise ->
                            navController.navigate(cameraRepRoute(exercise))
                        },
                        onAutoOpenCamera = { exercise, mode, durationSec ->
                            navController.navigate(cameraRepRoute(exercise, mode, durationSec))
                        }
                    )
                }
            }

            if (showCompletionDialog && completionDraft != null) {
                val draft = completionDraft
                WorkoutCompletionDialog(
                    draft = draft,
                    onSave = {
                        vm.saveWorkout(draft.asTodayWorkout()) {
                            draftStore.clear()
                            completionDismissed = true
                            TimerService.stop(context)
                            navController.popBackStack("diary", false)
                            navController.navigate("stats") {
                                launchSingleTop = true
                            }
                        }
                    },
                    onShare = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, workoutShareText(draft))
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(sendIntent, "Поделиться тренировкой")
                            )
                        }
                        completionDismissed = true
                    },
                    onEdit = {
                        draftStore.save(
                            draft.copy(
                                date = LocalDate.now(),
                                editingId = null,
                                editingCreatedAt = null
                            )
                        )
                        completionDismissed = true
                        TimerService.stop(context)
                        navController.popBackStack("diary", false)
                        navController.navigate("new") {
                            launchSingleTop = true
                        }
                    },
                    onCancel = {
                        draftStore.clear()
                        completionDismissed = true
                        TimerService.stop(context)
                        navController.popBackStack("diary", false)
                    }
                )
            }

            if (route == "diary" && !onboardingShown && !showCompletionDialog) {
                OnboardingDialog(onDismiss = { vm.finishOnboarding() })
            }
        }
    }
}

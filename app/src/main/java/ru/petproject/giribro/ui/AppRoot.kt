package ru.petproject.giribro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.petproject.giribro.AppViewModel
import ru.petproject.giribro.timer.TimerPhase
import ru.petproject.giribro.timer.TimerService
import ru.petproject.giribro.ui.screens.DiaryScreen
import ru.petproject.giribro.ui.screens.NewWorkoutScreen
import ru.petproject.giribro.ui.screens.SettingsScreen
import ru.petproject.giribro.ui.screens.StatsScreen
import ru.petproject.giribro.ui.screens.TimerScreen

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val bottomItems = listOf(
    BottomItem("diary", "Дневник", Icons.Outlined.FitnessCenter),
    BottomItem("stats", "Статистика", Icons.Outlined.Insights),
    BottomItem("timer", "Таймер", Icons.Outlined.Timer),
    BottomItem("settings", "Ещё", Icons.Outlined.Tune)
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: "diary"
    val baseRoute = route.substringBefore('?')
    val settings by vm.settings.collectAsStateWithLifecycle()
    val timerState by TimerService.state.collectAsStateWithLifecycle()
    val timerFullScreen = baseRoute == "timer" &&
        (timerState.isActive || timerState.phase == TimerPhase.DONE)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (baseRoute != "new" && !timerFullScreen) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = {
                                if (route != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(item.icon, contentDescription = item.label)
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
                composable("timer") {
                    TimerScreen(
                        onBack = {
                            navController.navigate("diary") {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("settings") { SettingsScreen(vm) }
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
                        initialWorkout = source,
                        repeat = isRepeat,
                        onSave = { workout ->
                            vm.saveWorkout(workout)
                            navController.popBackStack()
                        },
                        onClose = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

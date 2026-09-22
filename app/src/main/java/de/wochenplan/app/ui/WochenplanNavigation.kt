package de.wochenplan.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.wochenplan.app.ui.event.EventEditScreen
import de.wochenplan.app.ui.event.EventEditViewModel
import de.wochenplan.app.ui.focus.FocusScreen
import de.wochenplan.app.ui.plan.PlanScreen
import de.wochenplan.app.ui.settings.SettingsScreen
import de.wochenplan.app.ui.tasks.TaskScreen
import de.wochenplan.app.ui.templates.TemplateScreen
import de.wochenplan.app.ui.week.WeekScreen
import java.time.LocalDate
import java.time.LocalTime

object Routes {
    const val WEEK = "week"
    const val TASKS = "tasks"
    const val FOCUS = "focus"
    const val SETTINGS = "settings"
    const val TEMPLATES = "templates"
    const val PLAN = "plan"
    const val EVENT = "event?href={href}&date={date}&time={time}"

    /** Baut das Ziel fuer einen neuen oder vorhandenen Termin. */
    fun event(href: String? = null, date: LocalDate? = null, time: LocalTime? = null): String = buildString {
        append("event?href=")
        append(Uri.encode(href.orEmpty()))
        append("&date=")
        append(Uri.encode(date?.toString().orEmpty()))
        append("&time=")
        append(Uri.encode(time?.toString().orEmpty()))
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.WEEK, "Woche", Icons.Filled.CalendarViewWeek),
    BottomDestination(Routes.TASKS, "Aufgaben", Icons.Filled.CheckCircle),
    BottomDestination(Routes.FOCUS, "Fokus", Icons.Filled.Timer),
    BottomDestination(Routes.SETTINGS, "Mehr", Icons.Filled.Settings),
)

@Composable
fun WochenplanNavigation(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomDestinations.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    for (destination in bottomDestinations) {
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateToTab(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WEEK,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.WEEK) {
                WeekScreen(
                    onCreateEvent = { date, time -> navController.navigate(Routes.event(date = date, time = time)) },
                    onEditEvent = { event -> navController.navigate(Routes.event(href = event.href)) },
                    onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                )
            }
            composable(Routes.TASKS) {
                TaskScreen(
                    onOpenFocus = { navController.navigateToTab(Routes.FOCUS) },
                    onOpenTemplates = { navController.navigate(Routes.TEMPLATES) },
                    onOpenPlanner = { navController.navigate(Routes.PLAN) },
                )
            }
            composable(Routes.TEMPLATES) {
                TemplateScreen(onClose = { navController.popBackStack() })
            }
            composable(Routes.PLAN) {
                PlanScreen(
                    onClose = { navController.popBackStack() },
                    onOpenSettings = {
                        navController.popBackStack()
                        navController.navigateToTab(Routes.SETTINGS)
                    },
                )
            }
            composable(Routes.FOCUS) {
                FocusScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                route = Routes.EVENT,
                arguments = listOf(
                    navArgument(EventEditViewModel.ARG_HREF) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(EventEditViewModel.ARG_DATE) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(EventEditViewModel.ARG_TIME) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                EventEditScreen(onClose = { navController.popBackStack() })
            }
        }
    }
}

/** Wechselt den Reiter, ohne den Rueckstapel wachsen zu lassen. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

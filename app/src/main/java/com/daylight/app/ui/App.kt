package com.daylight.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.daylight.app.data.*

@Composable
fun DaylightApp(vm: DaylightViewModel, incoming: String?, consumed: () -> Unit) {
    val data by vm.state.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    DaylightTheme(data.preferences.theme) {
        val nav = rememberNavController()
        val snack = remember { SnackbarHostState() }
        val entry by nav.currentBackStackEntryAsState()
        val tabs = listOf("home", "library", "forms", "schedule", "settings")
        val icons = listOf(Icons.Outlined.WbSunny, Icons.Outlined.Bookmarks, Icons.Outlined.Checklist, Icons.Outlined.Schedule, Icons.Outlined.Tune)
        val names = listOf("Home", "Library", "Forms", "Schedule", "Settings")
        LaunchedEffect(vm) { vm.events.collect { snack.showSnackbar(it) } }
        LaunchedEffect(incoming, data.preferences.onboarded, ready) {
            if (ready && data.preferences.onboarded && incoming != null) {
                if (Regex("(content|fill|response)/[0-9a-fA-F-]{36}").matches(incoming)) nav.navigate(incoming)
                consumed()
            }
        }
        Scaffold(snackbarHost = { SnackbarHost(snack) }, bottomBar = {
            if (ready && data.preferences.onboarded && entry?.destination?.route in tabs) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                tabs.forEachIndexed { index, tab -> NavigationBarItem(selected = entry?.destination?.route == tab, onClick = {
                    nav.navigate(tab) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                }, icon = { Icon(icons[index], null) }, label = { Text(names[index]) }) }
            }
        }) { padding ->
            Box(Modifier.padding(padding).imePadding()) {
                if (loadError) Page {
                    Heading("Let's try that again", "Daylight couldn't read your local data. Try again, or reopen the app.")
                    PrimaryButton("Retry") { vm.retryLoad() }
                }
                else if (!ready) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else if (!data.preferences.onboarded) Onboarding { vm.execute { vm.repository.dao.put(data.preferences.copy(onboarded = true)) } }
                else NavHost(nav, startDestination = "home") {
                    composable("home") { HomeScreen(data, vm, nav) }
                    composable("library") { LibraryScreen(data, vm, nav) }
                    composable("content/{id}") { ContentScreen(it.arguments?.getString("id") ?: "new", data, vm, nav) }
                    composable("forms") { FormsScreen(data, vm, nav) }
                    composable("form/{id}") { FormEditor(it.arguments?.getString("id") ?: "new", data, vm, nav) }
                    composable("fill/{id}") { ResponseScreen(it.arguments?.getString("id") ?: "", false, data, vm, nav) }
                    composable("response/{id}") { ResponseScreen(it.arguments?.getString("id") ?: "", true, data, vm, nav) }
                    composable("history/{id}") { HistoryScreen(it.arguments?.getString("id") ?: "", data, nav) }
                    composable("schedule") { ScheduleScreen(data, vm, nav) }
                    composable("reminder/{id}") { ScheduleEditor(it.arguments?.getString("id") ?: "new", data, vm, nav) }
                    composable("settings") { SettingsScreen(data, vm) }
                    composable("search") { SearchScreen(data, nav) }
                }
            }
        }
    }
}

@Composable
private fun Onboarding(onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Build your day.", "Stay inspired.", "Reflect on your progress.", "A space that's yours.")
    val body = listOf("Make room for what matters. Turn your intentions into a simple, personal daily checklist.", "Keep the words that move you close. Add your own quotes and verified spiritual reminders, then choose when to receive them.", "Meet your morning intentions again in the evening. Notice what you did, and decide what comes next.", "Your data stays on your device. No account, no ads, no tracking. Export a backup whenever you like.")
    val icons = listOf(Icons.Outlined.WbSunny, Icons.Outlined.AutoStories, Icons.Outlined.Spa, Icons.Outlined.Lock)
    Page {
        Spacer(Modifier.height(32.dp))
        Text("D A Y L I G H T", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))
        Panel(tonal = true) { Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { Icon(icons[page], null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary) } }
        Text(titles[page], style = MaterialTheme.typography.displaySmall)
        Text(body[page], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${page + 1} / 4", style = MaterialTheme.typography.labelSmall)
        PrimaryButton(if (page == 3) "Get started" else "Continue") { if (page == 3) onFinish() else page++ }
        if (page > 0) TextButton(onClick = { page-- }) { Text("Back") }
    }
}

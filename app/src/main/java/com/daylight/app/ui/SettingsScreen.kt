package com.daylight.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daylight.app.BuildConfig
import com.daylight.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(data: AppData, vm: DaylightViewModel) {
    val context = LocalContext.current
    val prefs = data.preferences
    var quietStart by rememberSaveable(prefs.quietStart) { mutableStateOf(timeLabel(prefs.quietStart)) }
    var quietEnd by rememberSaveable(prefs.quietEnd) { mutableStateOf(timeLabel(prefs.quietEnd)) }
    var pending by remember { mutableStateOf<Backup?>(null) }
    var rotationReset by remember { mutableStateOf(false) }
    var permissionVersion by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun save(p: Preferences) { vm.execute(reschedule = true) { vm.repository.dao.put(p) } }
    fun open(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { vm.message("This setting isn't available on your device.") } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> permissionVersion++; vm.message(if (allowed) "Notifications are ready." else "Notifications are off. You can enable them in Android settings.") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) vm.execute("Backup exported") {
        withContext(Dispatchers.IO) { val text = vm.container.backup.export(); requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Could not open this backup location." }.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
    } }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.execute {
        pending = withContext(Dispatchers.IO) {
            try {
                val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= BackupCodec.MAX_BYTES) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                    }
                    output.toByteArray()
                }
                require(bytes.size <= BackupCodec.MAX_BYTES) { "This backup is too large (maximum 10 MB)." }
                BackupCodec.decode(bytes.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw IllegalArgumentException("This file isn't a valid Daylight backup. Your current data hasn't changed.", e)
            }
        }
    } }
    Page {
        Heading("Make yourself at home", "Your space. Your pace. Your preferences.")
        Panel {
            Text("Appearance", style = MaterialTheme.typography.titleLarge)
            Choice("Theme", prefs.theme, listOf("System", "Light", "Dark")) { save(prefs.copy(theme = it)) }
        }
        Panel {
            Text("Notifications", style = MaterialTheme.typography.titleLarge)
            Text("Reminders need notification permission to appear. Precise timing is optional and uses Android's alarms access.", style = MaterialTheme.typography.bodyMedium)
            key(permissionVersion) { Text(if (vm.container.engine.notificationsAllowed) "Notifications are enabled" else "Notifications are currently off", color = MaterialTheme.colorScheme.primary) }
            PrimaryButton("Allow notifications") {
                if (Build.VERSION.SDK_INT >= 33 && !vm.container.engine.notificationsAllowed) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            TextButton(onClick = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Open Android notification settings") }
            if (Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = { open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) { Text("Precise reminder timing") }
            Toggle("Sound", prefs.sound, "Android channel settings take precedence.") { save(prefs.copy(sound = it)) }
            Toggle("Vibration", prefs.vibration) { save(prefs.copy(vibration = it)) }
            Text("Battery restrictions can delay reminders. Restarting your phone restores schedules; force-stopping the app requires opening it again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("App battery & permissions") }
        }
        Panel {
            Text("Room for quiet", style = MaterialTheme.typography.titleLarge)
            Toggle("Quiet hours", prefs.quietEnabled, "No reminders during your rest window.") { save(prefs.copy(quietEnabled = it)) }
            if (prefs.quietEnabled) {
                Field("From · HH:mm", quietStart, { quietStart = it }); Field("Until · HH:mm", quietEnd, { quietEnd = it })
                Text("Random reminders that no longer fit will be paused. Review reminders move to the end of quiet hours.", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = { val start = parseTime(quietStart); val end = parseTime(quietEnd); if (start == null || end == null || start == end) vm.message("Use two different times, such as 22:00 and 07:00.") else save(prefs.copy(quietStart = start, quietEnd = end)) }) { Text("Save quiet hours") }
            }
        }
        Panel {
            Text("Keep your words safe", style = MaterialTheme.typography.titleLarge)
            Text("Export a local JSON backup of your library, forms, answers, reflections, schedules, and settings. Backups are readable files; choose a location you trust.", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("Export backup") { export.launch("daylight_backup_${LocalDate.now()}.json") }
            OutlinedButton(onClick = { importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text("Import backup") }
            TextButton(onClick = { rotationReset = true }) { Text("Reset content rotation history") }
        }
        if (BuildConfig.DEBUG) Panel {
            Text("Development samples", style = MaterialTheme.typography.titleMedium)
            Text("Optional sample entries are clearly marked. No religious text is included.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { vm.execute("Sample entries added") {
                listOf("Small steps" to "Make one small promise to yourself. Keep it today.", "Begin again" to "There is room to begin again, right where you are.").forEachIndexed { index, (title, body) ->
                    val sample = Content(id = java.util.UUID.nameUUIDFromBytes("daylight-sample-$index".toByteArray()).toString(), title = "[Sample] $title", body = body, category = "Motivation", source = "Daylight development sample")
                    if (vm.repository.dao.content().none { it.id == sample.id }) vm.repository.dao.put(sample)
                }
            } }) { Text("Add sample motivation") }
        }
        Panel(tonal = true) { Icon(Icons.Outlined.Lock, null); Text("Private by design", style = MaterialTheme.typography.titleLarge); Text("No accounts. No analytics. No advertising. No Internet permission. Your content lives in a database on this device."); Text("Daylight ${BuildConfig.VERSION_NAME} · Made for a more intentional day.", style = MaterialTheme.typography.bodyMedium) }
    }
    pending?.let { backup -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("Add this backup?") }, text = { Text("${backup.content.size} library entries, ${backup.forms.size} forms, ${backup.responses.size} responses, and ${backup.schedules.size} reminders.\n\nExisting IDs and their associated history are skipped. Your settings stay unchanged. Imported reminders start paused, and imported responses won't send old review notifications. Nothing is overwritten.") }, confirmButton = { TextButton(onClick = { pending = null; vm.execute(reschedule = true) { val count = vm.container.backup.merge(backup); vm.message("Imported $count new records. Existing data was kept.") } }) { Text("Add to my data") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }) }
    if (rotationReset) AlertDialog(onDismissRequest = { rotationReset = false }, title = { Text("Start a fresh rotation?") }, text = { Text("All enabled content will be treated as unseen. Your entries and favorites are kept.") }, confirmButton = { TextButton(onClick = { rotationReset = false; vm.execute("Rotation history reset") { vm.repository.dao.resetRotation() } }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { rotationReset = false }) { Text("Cancel") } })
}

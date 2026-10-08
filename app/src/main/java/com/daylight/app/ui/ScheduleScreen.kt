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
import androidx.navigation.NavHostController
import com.daylight.app.data.*
import com.daylight.app.domain.SchedulePlanner
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.*

@Composable
fun ScheduleScreen(data: AppData, vm: DaylightViewModel, nav: NavHostController) {
    Page {
        Heading("Gentle nudges", "The right words, at your own rhythm.", action = { IconButton(onClick = { nav.navigate("reminder/new") }) { Icon(Icons.Outlined.Add, "Create reminder") } })
        if (!vm.container.engine.notificationsAllowed) Panel(tonal = true) { Text("Let a little inspiration in", style = MaterialTheme.typography.titleLarge); Text("Allow notifications in Settings so your reminders can reach you."); TextButton(onClick = { nav.navigate("settings") }) { Text("Notification settings") } }
        if (!vm.container.engine.exactAllowed) Text("Reminders use flexible timing. Enable precise reminders in Settings for closer delivery times.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (data.schedules.isEmpty()) EmptyState(Icons.Outlined.NotificationsNone, "Make space for a reminder", "Choose fixed times, a regular interval, or a few surprise moments during your day.", "Create reminder") { nav.navigate("reminder/new") }
        data.schedules.forEach { s -> Panel {
            Toggle(s.title, s.enabled, "${s.mode} · ${if (s.target == "Form") data.forms.find { it.id == s.formId }?.title ?: "Form" else s.category}") { enabled -> vm.execute(reschedule = true) {
                val updated = s.copy(enabled = enabled)
                if (enabled) repeat(8) { SchedulePlanner.dayTimes(updated, LocalDate.now().plusDays(it.toLong()), ZoneId.systemDefault(), data.preferences) }
                vm.repository.dao.put(updated)
            } }
            Text(when(s.mode) { "Fixed" -> s.fixedMinutes.joinToString(" · ") { timeLabel(it) }; "Interval" -> "Every ${s.intervalMinutes} minutes · ${timeLabel(s.startMinute)}–${timeLabel(s.endMinute)}"; else -> "${s.randomCount} moments · ${timeLabel(s.startMinute)}–${timeLabel(s.endMinute)}" }, style = MaterialTheme.typography.bodyMedium)
            if (s.target == "Content" && data.content.none { it.enabled && it.category == s.category }) Text("Add an enabled ${s.category.lowercase()} entry to receive this reminder.", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { nav.navigate("reminder/${s.id}") }) { Text("Edit reminder"); Icon(Icons.Outlined.ChevronRight, null) }
        } }
        SectionTitle("Coming up")
        val next = data.occurrences.filter { it.at > System.currentTimeMillis() }.take(12)
        if (next.isEmpty()) Text("Your next reminders will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        next.forEach { o -> ListItem(headlineContent = { Text(o.title) }, supportingContent = { Text(dateLabel(o.at)) }, leadingContent = { Icon(if (o.target == "Review") Icons.Outlined.DarkMode else Icons.Outlined.Schedule, null) }) }
        Text("Quiet hours are respected. When reminders collide, only one is kept. Missed reminders won't arrive in a burst.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ScheduleEditor(id: String, data: AppData, vm: DaylightViewModel, nav: NavHostController) {
    val original = data.schedules.find { it.id == id }
    if (id != "new" && original == null) { Page { Heading("Reminder unavailable", "This reminder may have been deleted.", { nav.popBackStack() }) }; return }
    var encoded by rememberSaveable(id) { mutableStateOf(Json.encodeToString(original ?: Schedule())) }
    val draft = remember(encoded) { Json.decodeFromString<Schedule>(encoded) }
    fun update(s: Schedule) { encoded = Json.encodeToString(s) }
    var fixed by rememberSaveable(id) { mutableStateOf(draft.fixedMinutes.joinToString(", ") { timeLabel(it) }) }
    var start by rememberSaveable(id) { mutableStateOf(timeLabel(draft.startMinute)) }
    var end by rememberSaveable(id) { mutableStateOf(timeLabel(draft.endMinute)) }
    var interval by rememberSaveable(id) { mutableStateOf(draft.intervalMinutes.toString()) }
    var count by rememberSaveable(id) { mutableStateOf(draft.randomCount.toString()) }
    var spacing by rememberSaveable(id) { mutableStateOf(draft.minimumSpacing.toString()) }
    var preview by remember { mutableStateOf<List<Occurrence>?>(null) }
    var previewDraft by remember { mutableStateOf<Schedule?>(null) }
    var delete by remember { mutableStateOf(false) }
    fun candidate(): Schedule {
        val times = if (draft.mode == "Fixed") fixed.split(",").map { requireNotNull(parseTime(it)) { "Use comma-separated times, for example 08:00, 13:00, 18:00." } } else draft.fixedMinutes
        return draft.copy(fixedMinutes = times, startMinute = if (draft.mode == "Fixed") draft.startMinute else requireNotNull(parseTime(start)) { "Use a start time such as 08:00." }, endMinute = if (draft.mode == "Fixed") draft.endMinute else requireNotNull(parseTime(end)) { "Use an end time such as 22:00." }, intervalMinutes = interval.toIntOrNull() ?: 0, randomCount = count.toIntOrNull() ?: 0, minimumSpacing = spacing.toIntOrNull() ?: 0).also { require(SchedulePlanner.validate(it) == null) { SchedulePlanner.validate(it)!! } }
    }
    Page {
        Heading(if (original == null) "Find your rhythm" else "Edit your reminder", "A reminder should fit your day.", { nav.popBackStack() })
        Field("Reminder name", draft.title, { update(draft.copy(title = it)) })
        Choice("Remind me to", if (draft.target == "Content") "Read from my library" else "Fill out a form", listOf("Read from my library", "Fill out a form")) { update(draft.copy(target = if (it == "Fill out a form") "Form" else "Content", formId = if (it == "Fill out a form") data.forms.firstOrNull()?.id else null)) }
        if (draft.target == "Content") Choice("Content category", draft.category, Category.entries.map { it.name }) { update(draft.copy(category = it)) }
        else {
            if (data.forms.isEmpty()) Text("Create a form before scheduling its morning reminder.", color = MaterialTheme.colorScheme.error)
            else Choice("Form", data.forms.find { it.id == draft.formId }?.let { "${it.title} · ${it.id.take(4)}" } ?: "Choose form", data.forms.map { "${it.title} · ${it.id.take(4)}" }) { label -> update(draft.copy(formId = data.forms.first { "${it.title} · ${it.id.take(4)}" == label }.id)) }
        }
        Choice("Timing", draft.mode, ScheduleMode.entries.map { it.name }) { update(draft.copy(mode = it)) }
        if (draft.mode == "Fixed") Field("Daily times · HH:mm, HH:mm", fixed, { fixed = it })
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Box(Modifier.weight(1f)) { Field("From · HH:mm", start, { start = it }) }; Box(Modifier.weight(1f)) { Field("Until · HH:mm", end, { end = it }) } }
            if (draft.mode == "Interval") Field("Repeat every · minutes", interval, { interval = it }, numeric = true)
            else { Field("Reminders per day", count, { count = it }, numeric = true); Field("Minimum spacing · minutes", spacing, { spacing = it }, numeric = true) }
        }
        if (data.preferences.quietEnabled) Text("Quiet hours · ${timeLabel(data.preferences.quietStart)}–${timeLabel(data.preferences.quietEnd)}", style = MaterialTheme.typography.bodyMedium)
        Toggle("Enabled", draft.enabled) { update(draft.copy(enabled = it)) }
        PrimaryButton("Preview times") {
            runCatching {
                val s = candidate()
                val planned = SchedulePlanner.plan(listOf(s.copy(enabled = true)), emptyList(), data.preferences, Instant.now(), ZoneId.systemDefault())
                require(planned.isNotEmpty()) { "No reminders fit outside your quiet hours. Adjust the times." }
                previewDraft = s; preview = planned
            }.onFailure { vm.message(it.message ?: "Please check your reminder settings.") }
        }
        if (original != null) TextButton(onClick = { delete = true }) { Text("Delete reminder", color = MaterialTheme.colorScheme.error) }
    }
    if (preview != null) AlertDialog(onDismissRequest = { preview = null }, title = { Text("Your next moments") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        preview!!.take(8).forEach { Text(dateLabel(it.at)) }
        Text("These times repeat or are recalculated daily. Collisions with other reminders are suppressed. Android may delay flexible reminders.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { val saving = previewDraft ?: return@TextButton; vm.execute("Reminder saved", reschedule = true) { vm.repository.dao.put(saving); preview = null; nav.popBackStack() } }) { Text("Save reminder") } }, dismissButton = { TextButton(onClick = { preview = null }) { Text("Adjust") } })
    if (delete) ConfirmDelete("Delete this reminder?", "Your library entries and form responses will be kept.", { delete = false }) { vm.execute(reschedule = true) { vm.repository.dao.deleteSchedule(id); nav.popBackStack() } }
}

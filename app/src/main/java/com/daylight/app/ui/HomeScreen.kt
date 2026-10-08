package com.daylight.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.daylight.app.data.*
import com.daylight.app.domain.FormLogic
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(data: AppData, vm: DaylightViewModel, nav: NavHostController) {
    val now = ZonedDateTime.now()
    val today = data.responses.filter { Instant.ofEpochMilli(it.createdAt).atZone(now.zone).toLocalDate() == now.toLocalDate() }
    val answers = data.answers.filter { a -> today.any { it.id == a.responseId } }
    val (done, total) = FormLogic.progress(answers)
    val content = data.content.filter { it.enabled }.let { items -> if (items.isEmpty()) null else items[(now.toLocalDate().toEpochDay() % items.size).toInt()] }
    val next = data.occurrences.firstOrNull { it.at > System.currentTimeMillis() }
    Page {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.WbSunny, null, tint = MaterialTheme.colorScheme.primary); Text("D A Y L I G H T", style = MaterialTheme.typography.labelSmall) }
            IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Outlined.Search, "Search everything") }
        }
        Heading("Good ${when(now.hour) { in 5..11 -> "morning"; in 12..17 -> "afternoon"; else -> "evening" }}.", now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")))
        Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF294F40), contentColor = Color(0xFFF5F3E7)) {
            Column(Modifier.fillMaxWidth().padding(26.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("A MOMENT OF INSPIRATION", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCFDCC8)); Icon(Icons.Outlined.Spa, null, tint = Color(0xFFE5CB91)) }
                Text(content?.body ?: "A little intention.\nA little progress.\nA day that feels like yours.", fontFamily = FontFamily.Serif, fontSize = 26.sp, lineHeight = 36.sp)
                if (content != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(content.source.ifBlank { "Your ${content.category.lowercase()} library" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCFDCC8))
                        IconButton(onClick = { vm.execute { vm.repository.dao.put(content.copy(favorite = !content.favorite)) } }) { Icon(if (content.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Toggle favorite") }
                        IconButton(onClick = { nav.navigate("content/${content.id}") }) { Icon(Icons.Outlined.ArrowForward, "Open content") }
                    }
                } else TextButton(onClick = { nav.navigate("content/new") }, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE5CB91))) { Text("Add your first inspiration"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.ArrowForward, null) }
            }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("TODAY'S PROGRESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)); Text(if (total == 0) "Start with one intention" else "$done of $total items complete", style = MaterialTheme.typography.titleMedium) }
                Text("${FormLogic.percent(answers)}%", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(8.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text(if (total == 0) "Your day doesn't need to be perfect. Just begin." else "Every small step counts. Keep making room for you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (next != null) Panel(tonal = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Outlined.NotificationsNone, null)
                Column(Modifier.weight(1f)) { Text("UP NEXT", style = MaterialTheme.typography.labelSmall); Text(next.title, style = MaterialTheme.typography.titleMedium); Text(dateLabel(next.at), style = MaterialTheme.typography.bodyMedium) }
                IconButton(onClick = { nav.navigate("schedule") }) { Icon(Icons.Outlined.ArrowForward, "See reminders") }
            }
        }
        Column {
            SectionTitle("Your daily rituals", "See all") { nav.navigate("forms") }
            if (data.forms.isEmpty()) EmptyState(Icons.Outlined.Checklist, "Give your day a little direction", "Create a checklist for your morning. Come back to it tonight.", "Create a form") { nav.navigate("form/new") }
            else data.forms.take(3).forEach { form ->
                val response = today.firstOrNull { it.formId == form.id }
                ListItem(headlineContent = { Text(form.title) }, supportingContent = { Text(if (response != null) "${FormLogic.percent(data.answers.filter { it.responseId == response.id })}% complete · tap to reflect" else form.reviewMinute?.let { "Evening review at ${timeLabel(it)}" } ?: "A moment for yourself") },
                    leadingContent = { Icon(if (response != null) Icons.Outlined.TaskAlt else Icons.Outlined.RadioButtonUnchecked, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { nav.navigate(if (response == null) "fill/${form.id}" else "response/${response.id}") })
            }
        }
        Column {
            SectionTitle("Make a little space")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Quote" to "content/new", "Form" to "form/new", "Reminder" to "reminder/new").forEach { (label, route) ->
                    OutlinedButton(onClick = { nav.navigate(route) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 14.dp), shape = RoundedCornerShape(16.dp)) { Text("+ $label", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        if (data.content.isNotEmpty()) Column { SectionTitle("Recently saved", "Library") { nav.navigate("library") }; data.content.take(2).forEach { c -> ListItem(headlineContent = { Text(c.title) }, supportingContent = { Text(c.category) }, leadingContent = { Icon(Icons.Outlined.BookmarkBorder, null) }, modifier = Modifier.clickable { nav.navigate("content/${c.id}") }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) } }
        Text("A calmer day starts with a little intention.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SearchScreen(data: AppData, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    fun matches(vararg text: String) = query.isNotBlank() && text.any { it.contains(query, ignoreCase = true) }
    Page {
        Heading("Find a little clarity", "Search your library, forms, and reflections.", { nav.popBackStack() })
        Field("Search everything", query, { query = it })
        val content = data.content.filter { matches(it.title, it.body, it.category, it.source, it.surah, it.translation, it.reference) }
        val forms = data.forms.filter { matches(it.title, it.description) }
        val reflections = data.reflections.filter { matches(it.text, it.tomorrow) }
        if (query.isBlank()) Text("Your words are here when you need them.")
        else if (content.isEmpty() && forms.isEmpty() && reflections.isEmpty()) EmptyState(Icons.Outlined.SearchOff, "No matches yet", "Try another word or a shorter phrase.")
        content.forEach { item -> ListItem(headlineContent = { Text(item.title) }, supportingContent = { Text(item.category) }, modifier = Modifier.clickable { nav.navigate("content/${item.id}") }) }
        forms.forEach { item -> ListItem(headlineContent = { Text(item.title) }, supportingContent = { Text("Form") }, modifier = Modifier.clickable { nav.navigate("fill/${item.id}") }) }
        reflections.forEach { item -> ListItem(headlineContent = { Text(item.text.ifBlank { item.tomorrow }) }, supportingContent = { Text("Reflection · ${dateLabel(item.updatedAt)}") }, modifier = Modifier.clickable { nav.navigate("response/${item.responseId}") }) }
    }
}

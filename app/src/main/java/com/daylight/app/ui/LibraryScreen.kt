package com.daylight.app.ui

import androidx.compose.foundation.*
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun LibraryScreen(data: AppData, vm: DaylightViewModel, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    val items = data.content.filter { (category == "All" || it.category == category) && (!favorites || it.favorite) && listOf(it.title, it.body, it.category, it.source, it.reference, it.translation, it.surah).any { value -> value.contains(query, true) } }
    Page {
        Heading("Words to return to", "Your personal collection of inspiration.", action = { IconButton(onClick = { nav.navigate("content/new") }) { Icon(Icons.Outlined.Add, "Add content") } })
        Field("Search your library", query, { query = it })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("All") + Category.entries.map { it.name }).forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it) }) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("${items.size} saved ${if (items.size == 1) "entry" else "entries"}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); FilterChip(favorites, { favorites = !favorites }, label = { Text("Favorites") }, leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(16.dp)) }) }
        if (items.isEmpty()) EmptyState(Icons.Outlined.AutoStories, if (query.isEmpty()) "A collection that feels like you" else "No matching entries", if (category in listOf("Quran", "Hadith")) "Add verified ${category.lowercase()} text yourself. Daylight never creates or changes religious text." else "Save words you want to carry with you. Your library is private and available offline.", "Add an entry") { nav.navigate("content/new") }
        items.forEach { c -> Panel {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(c.category.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary); if (!c.enabled) Text("Paused", style = MaterialTheme.typography.bodySmall); IconButton(onClick = { vm.execute { vm.repository.dao.put(c.copy(favorite = !c.favorite)) } }) { Icon(if (c.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Toggle favorite") } }
            Column(Modifier.fillMaxWidth().clickable { nav.navigate("content/${c.id}") }, verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(c.title, style = MaterialTheme.typography.titleLarge); Text(c.body, maxLines = 5, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis); if (c.source.isNotBlank()) Text(c.source, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
            TextButton(onClick = { nav.navigate("content/${c.id}") }) { Text("Read & edit"); Icon(Icons.Outlined.ChevronRight, null) }
        } }
    }
}

@Composable
fun ContentScreen(id: String, data: AppData, vm: DaylightViewModel, nav: NavHostController) {
    val original = data.content.find { it.id == id }
    if (id != "new" && original == null) { Page { Heading("Entry unavailable", "This entry may have been deleted.", { nav.popBackStack() }) }; return }
    var encoded by rememberSaveable(id) { mutableStateOf(Json.encodeToString(original ?: Content())) }
    val draft = remember(encoded) { Json.decodeFromString<Content>(encoded) }
    fun update(c: Content) { encoded = Json.encodeToString(c) }
    var delete by remember { mutableStateOf(false) }
    var editing by rememberSaveable(id) { mutableStateOf(id == "new") }
    Page {
        Heading(if (editing) if (id == "new") "Save something meaningful" else "Edit your entry" else draft.title, if (id == "new") "Words for the days ahead." else "${draft.category} · added ${dateLabel(draft.createdAt)}", { nav.popBackStack() })
        if (!editing) {
            Panel(tonal = true) { Text(draft.body, style = MaterialTheme.typography.headlineMedium); if (draft.translation.isNotBlank()) Text(draft.translation); if (draft.source.isNotBlank()) Text(draft.source); if (draft.surah.isNotBlank()) Text("${draft.surah} · ${draft.verse}"); if (draft.reference.isNotBlank()) Text(draft.reference) }
            PrimaryButton("Edit entry") { editing = true }
        } else {
            Choice("Category", draft.category, Category.entries.map { it.name }) { update(draft.copy(category = it)) }
            if (draft.category in listOf("Quran", "Hadith")) Panel(tonal = true) { Text("Your verified text, preserved exactly.", style = MaterialTheme.typography.titleMedium); Text("Please enter text from a source you trust. Daylight does not generate or correct religious content.") }
            Field("Title", draft.title, { update(draft.copy(title = it)) })
            Field(if (draft.category == "Quran") "Verse" else if (draft.category == "Hadith") "Hadith text" else "Content", draft.body, { update(draft.copy(body = it)) }, multiline = true)
            Field("Author / source", draft.source, { update(draft.copy(source = it)) })
            Field("Reference (optional)", draft.reference, { update(draft.copy(reference = it)) })
            if (draft.category == "Quran") {
                Field("Surah", draft.surah, { update(draft.copy(surah = it)) })
                Field("Verse number", draft.verse, { update(draft.copy(verse = it)) })
                Field("Translation (optional)", draft.translation, { update(draft.copy(translation = it)) }, multiline = true)
            }
            Toggle("Favorite", draft.favorite) { update(draft.copy(favorite = it)) }
            Toggle("Include in reminders", draft.enabled, "Paused entries remain in your library.") { update(draft.copy(enabled = it)) }
            PrimaryButton("Save entry") { vm.execute("Saved to your library") { vm.repository.saveContent(draft); nav.popBackStack() } }
        }
        if (original != null) TextButton(onClick = { delete = true }) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Delete entry") }
    }
    if (delete) ConfirmDelete("Delete this entry?", "This removes the entry from your library and future content rotation.", { delete = false }) { vm.execute { vm.repository.dao.deleteContent(id); nav.popBackStack() } }
}

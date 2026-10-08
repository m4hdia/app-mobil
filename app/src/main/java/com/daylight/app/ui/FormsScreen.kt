package com.daylight.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.daylight.app.data.*
import com.daylight.app.domain.FormLogic
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun FormsScreen(data: AppData, vm: DaylightViewModel, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf("") }
    Page {
        Heading("Rituals, made personal", "A little structure. A lot of possibility.", action = { IconButton(onClick = { nav.navigate("form/new") }) { Icon(Icons.Outlined.Add, "Create form") } })
        Panel(tonal = true) { Icon(Icons.Outlined.Spa, null); Text("Begin with intention.\nEnd with reflection.", style = MaterialTheme.typography.headlineMedium); Text("Fill a form in the morning. An evening reminder brings you back to the same answers.") }
        Field("Find a form", query, { query = it })
        if (data.forms.isEmpty()) {
            EmptyState(Icons.Outlined.Checklist, "Your first daily ritual", "Build your own form or start with a simple, editable goals checklist.", "Create a form") { nav.navigate("form/new") }
            OutlinedButton(onClick = { vm.execute("Your daily goals form is ready") {
                val form = PersonalForm(title = "Daily goals", description = "Make room for what matters today.")
                vm.repository.saveForm(form, listOf(Question(formId = form.id, title = "Today's intentions", options = listOf("Read 10 pages", "Move my body", "Work on my project", "Practice a language", "Make time for reflection"))))
            } }, modifier = Modifier.fillMaxWidth()) { Text("Use the daily goals starter") }
        }
        val forms = data.forms.filter { it.title.contains(query, true) || it.description.contains(query, true) }
        if (forms.isEmpty() && data.forms.isNotEmpty()) Text("No forms match your search.")
        forms.forEach { f -> Panel {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Checklist, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(f.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); IconButton(onClick = { nav.navigate("form/${f.id}") }) { Icon(Icons.Outlined.Edit, "Edit ${f.title}") } }
            if (f.description.isNotBlank()) Text(f.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${data.questions.count { it.formId == f.id }} questions · ${data.responses.count { it.formId == f.id }} entries", style = MaterialTheme.typography.bodyMedium)
            f.reviewMinute?.let { Text("Evening review · ${timeLabel(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilledTonalButton(onClick = { nav.navigate("fill/${f.id}") }) { Text("Fill out") }; TextButton(onClick = { nav.navigate("history/${f.id}") }) { Text("View history") } }
        } }
    }
}

@Composable
fun FormEditor(id: String, data: AppData, vm: DaylightViewModel, nav: NavHostController) {
    val original = data.forms.find { it.id == id }
    if (id != "new" && original == null) { Page { Heading("Form unavailable", "This form may have been deleted.", { nav.popBackStack() }) }; return }
    var formJson by rememberSaveable(id) { mutableStateOf(Json.encodeToString(original ?: PersonalForm())) }
    val form = remember(formJson) { Json.decodeFromString<PersonalForm>(formJson) }
    var questionJson by rememberSaveable(id) { mutableStateOf(Json.encodeToString(data.questions.filter { it.formId == id }.ifEmpty { listOf(Question(formId = form.id, title = "Today's goals", options = listOf("My first intention"))) })) }
    val questions = remember(questionJson) { Json.decodeFromString<List<Question>>(questionJson) }
    var review by rememberSaveable(id) { mutableStateOf(timeLabel(form.reviewMinute ?: 1200)) }
    var delete by remember { mutableStateOf(false) }
    fun update(q: Question) { questionJson = Json.encodeToString(questions.map { if (it.id == q.id) q else it }) }
    Page {
        Heading(if (id == "new") "Create your ritual" else "Shape your ritual", "A form that fits the way you think.", { nav.popBackStack() })
        Field("Form name", form.title, { formJson = Json.encodeToString(form.copy(title = it)) })
        Field("A little context (optional)", form.description, { formJson = Json.encodeToString(form.copy(description = it)) }, multiline = true)
        Panel {
            Toggle("Return to this in the evening", form.reviewMinute != null, "Each submission gets its own review reminder.") { formJson = Json.encodeToString(form.copy(reviewMinute = if (it) 1200 else null)) }
            if (form.reviewMinute != null) { Field("Review time · HH:mm", review, { review = it }); Text("If this time has passed, the review is tomorrow. Quiet hours may move it later.", style = MaterialTheme.typography.bodyMedium) }
        }
        SectionTitle("Questions · ${questions.size}")
        questions.forEachIndexed { index, q -> key(q.id) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("QUESTION ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    IconButton(enabled = index > 0, onClick = { val list = questions.toMutableList(); java.util.Collections.swap(list, index, index - 1); questionJson = Json.encodeToString(list) }) { Icon(Icons.Outlined.ArrowUpward, "Move question up") }
                    IconButton(enabled = index < questions.lastIndex, onClick = { val list = questions.toMutableList(); java.util.Collections.swap(list, index, index + 1); questionJson = Json.encodeToString(list) }) { Icon(Icons.Outlined.ArrowDownward, "Move question down") }
                    IconButton(onClick = { questionJson = Json.encodeToString(questions.filterNot { it.id == q.id }) }) { Icon(Icons.Outlined.DeleteOutline, "Delete question") }
                }
                Field("Question", q.title, { update(q.copy(title = it)) })
                Choice("Answer type", typeLabel(q.type), QuestionType.entries.map { typeLabel(it.name) }) { label -> update(q.copy(type = QuestionType.entries.first { typeLabel(it.name) == label }.name)) }
                if (q.type in listOf("Checkbox", "MultipleChoice")) Field("Choices · one per line", q.options.joinToString("\n"), { update(q.copy(options = it.split("\n"))) }, multiline = true)
                Toggle("Required", q.required, if (q.type == "Checkbox") "At least one choice must be checked." else "An answer is needed to save.") { update(q.copy(required = it)) }
            }
        } }
        OutlinedButton(onClick = { questionJson = Json.encodeToString(questions + Question(formId = form.id)) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Add question") }
        PrimaryButton("Save form") {
            val minute = if (form.reviewMinute == null) null else parseTime(review)
            if (form.reviewMinute != null && minute == null) vm.message("Use a review time such as 20:00.")
            else vm.execute("Your form is saved") { vm.repository.saveForm(form.copy(reviewMinute = minute), questions); nav.popBackStack() }
        }
        if (original != null) TextButton(onClick = { delete = true }) { Text("Delete form", color = MaterialTheme.colorScheme.error) }
    }
    if (delete) ConfirmDelete("Delete this form and its history?", "All submissions, reflections, and reminders attached to this form will be removed. Export a backup first if you want to keep them.", { delete = false }) { vm.execute(reschedule = true) { vm.repository.dao.deleteForm(id); nav.popBackStack() } }
}

fun typeLabel(type: String): String = when(type) { "MultipleChoice" -> "Multiple choice"; "ShortText" -> "Short text"; "LongText" -> "Long text"; "YesNo" -> "Yes / No"; else -> type }

@Composable
fun ResponseScreen(id: String, reviewing: Boolean, data: AppData, vm: DaylightViewModel, nav: NavHostController) {
    val response = if (reviewing) data.responses.find { it.id == id } else null
    val form = data.forms.find { it.id == if (reviewing) response?.formId else id }
    if (form == null || reviewing && response == null) { Page { Heading("This entry isn't here", "It may have been deleted. You can start a fresh form from your rituals.", { nav.popBackStack() }) }; return }
    val savedAnswers = if (reviewing) data.answers.filter { it.responseId == id } else data.questions.filter { it.formId == id }.map { Answer(responseId = "", questionId = it.id, position = it.position, title = it.title, type = it.type, required = it.required, options = it.options) }
    var encoded by rememberSaveable(id) { mutableStateOf(Json.encodeToString(savedAnswers)) }
    val answers = remember(encoded) { Json.decodeFromString<List<Answer>>(encoded) }
    val reflection = data.reflections.find { it.responseId == id }
    var thoughts by rememberSaveable(id) { mutableStateOf(reflection?.text ?: "") }
    var tomorrow by rememberSaveable(id) { mutableStateOf(reflection?.tomorrow ?: "") }
    var saving by remember { mutableStateOf(false) }
    fun update(answer: Answer) { encoded = Json.encodeToString(answers.map { if (it.id == answer.id) answer else it }) }
    Page {
        Heading(if (reviewing) "A moment to reflect" else form.title, if (response != null) "${response.formTitle} · ${dateLabel(response.createdAt)}" else form.description.ifBlank { "What would make today feel meaningful?" }, { nav.popBackStack() })
        if (reviewing) Panel(tonal = true) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Your progress", style = MaterialTheme.typography.titleMedium); Text("${FormLogic.percent(answers)}%", style = MaterialTheme.typography.headlineMedium) }
            LinearProgressIndicator(progress = { FormLogic.percent(answers) / 100f }, modifier = Modifier.fillMaxWidth())
            Text("Notice what moved forward. Be kind about what didn't.")
        } else if (form.reviewMinute != null) Text("We'll invite you back at ${timeLabel(form.reviewMinute)} to review your answers.", color = MaterialTheme.colorScheme.primary)
        answers.sortedBy { it.position }.forEach { a -> Panel {
            Text(a.title + if (a.required) " *" else "", style = MaterialTheme.typography.titleMedium)
            when(a.type) {
                "Checkbox" -> a.options.forEach { option ->
                    val checked = option in a.selected
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox) { update(a.copy(selected = if (it) a.selected + option else a.selected - option)) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, null); Spacer(Modifier.width(12.dp)); Text(option, Modifier.weight(1f))
                    }
                }
                "MultipleChoice", "YesNo" -> (if (a.type == "YesNo") listOf("Yes", "No") else a.options).forEach { option ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(a.value == option, role = Role.RadioButton, onClick = { update(a.copy(value = option)) }), verticalAlignment = Alignment.CenterVertically) { RadioButton(a.value == option, null); Spacer(Modifier.width(12.dp)); Text(option, Modifier.weight(1f)) }
                }
                "Rating" -> {
                    Text("1 · low     5 · high", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { (1..5).forEach { number -> FilterChip(selected = a.value == "$number", onClick = { update(a.copy(value = "$number")) }, label = { Text("$number") }) } }
                }
                else -> Field("Your answer", a.value, { update(a.copy(value = it)) }, multiline = a.type == "LongText", numeric = a.type == "Number")
            }
        } }
        if (reviewing) {
            Field("What did you notice today?", thoughts, { thoughts = it }, multiline = true)
            Field("Tomorrow I want to…", tomorrow, { tomorrow = it }, multiline = true)
        }
        Text("* Required · Progress tracks checked items when a checklist is present.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton(if (reviewing) "Save reflection" else "Save my intentions", enabled = !saving) {
            val error = FormLogic.error(answers)
            if (error != null) vm.message(error)
            else { saving = true; vm.execute(if (reviewing) "Reflection saved. A fresh start awaits." else "Your intentions are saved.", reschedule = true) {
                try {
                    if (response != null) { vm.repository.review(response, answers, thoughts, tomorrow); nav.popBackStack() }
                    else { val newId = vm.repository.submit(form, answers); nav.navigate("response/$newId") { popUpTo("fill/{id}") { inclusive = true } } }
                } finally { saving = false }
            } }
        }
    }
}

@Composable
fun HistoryScreen(id: String, data: AppData, nav: NavHostController) {
    val form = data.forms.find { it.id == id }
    Page {
        Heading(form?.title ?: "Your history", "A record of showing up for yourself.", { nav.popBackStack() })
        val responses = data.responses.filter { it.formId == id }
        if (responses.isEmpty()) EmptyState(Icons.Outlined.History, "Your story starts here", "Fill out this form to save your first entry.", "Fill out") { nav.navigate("fill/$id") }
        responses.forEach { response -> Panel(modifier = Modifier.clickable { nav.navigate("response/${response.id}") }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(dateLabel(response.createdAt), style = MaterialTheme.typography.titleMedium); Text("${FormLogic.percent(data.answers.filter { it.responseId == response.id })}% complete"); if (data.reflections.any { it.responseId == response.id }) Text("Reflection saved", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }; Icon(Icons.Outlined.ChevronRight, "Review entry") }
        } }
    }
}

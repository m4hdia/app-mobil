package com.daylight.app.data

import androidx.room.withTransaction
import com.daylight.app.domain.FormLogic
import com.daylight.app.domain.SchedulePlanner
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Backup(
    val version: Int = 1, val exportedAt: Long = System.currentTimeMillis(),
    val content: List<Content>, val forms: List<PersonalForm>, val questions: List<Question>,
    val responses: List<FormResponse>, val answers: List<Answer>, val reflections: List<Reflection>,
    val schedules: List<Schedule>, val preferences: Preferences,
)

object BackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    fun encode(backup: Backup): String = json.encodeToString(backup)
    fun decode(text: String): Backup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds the 10 MB limit." }
        return json.decodeFromString<Backup>(text).also(::validate)
    }
    fun validate(b: Backup) {
        require(b.version == 1) { "This backup version is not supported." }
        fun ids(values: List<String>) {
            require(values.size <= 20_000 && values.all { runCatching { java.util.UUID.fromString(it) }.isSuccess } && values.distinct().size == values.size) { "Invalid or duplicate record identifiers." }
        }
        ids(b.content.map { it.id }); ids(b.forms.map { it.id }); ids(b.questions.map { it.id })
        ids(b.responses.map { it.id }); ids(b.answers.map { it.id }); ids(b.reflections.map { it.id }); ids(b.schedules.map { it.id })
        val forms = b.forms.map { it.id }.toSet()
        val responses = b.responses.map { it.id }.toSet()
        require(b.content.all { it.title.isNotBlank() && it.body.isNotBlank() && it.category in Category.entries.map { c -> c.name } }) { "Invalid content in backup." }
        require(b.forms.all { it.title.isNotBlank() && (it.reviewMinute == null || it.reviewMinute in 0..1439) })
        require(b.questions.all { it.formId in forms && it.title.isNotBlank() && it.type in QuestionType.entries.map { t -> t.name } && it.options.size <= 100 && it.options.distinct().size == it.options.size && it.options.none(String::isBlank) && (it.type !in listOf("Checkbox", "MultipleChoice") || it.options.isNotEmpty()) }) { "Invalid form questions." }
        require(b.responses.all { it.formId in forms && it.createdAt > 0 && (it.reviewAt == null || it.reviewAt > 0) }) { "A response is missing its form." }
        require(b.answers.all { it.responseId in responses && it.type in QuestionType.entries.map { t -> t.name } && it.options.distinct().size == it.options.size && FormLogic.error(listOf(it)) == null }) { "Invalid answers in backup." }
        require(b.reflections.all { it.responseId in responses } && b.reflections.map { it.responseId }.distinct().size == b.reflections.size) { "Invalid reflections." }
        require(b.schedules.all { SchedulePlanner.validate(it) == null && (it.formId == null || it.formId in forms) }) { "Invalid reminders in backup." }
        require(b.preferences.theme in listOf("System", "Light", "Dark") && b.preferences.quietStart in 0..1439 && b.preferences.quietEnd in 0..1439 && b.preferences.id == 1) { "Invalid settings." }
    }
}

class BackupRepository(private val db: DaylightDatabase) {
    private val dao = db.dao()
    suspend fun export(): String = db.withTransaction {
        BackupCodec.encode(Backup(content = dao.content(), forms = dao.forms(), questions = dao.questions(), responses = dao.responses(), answers = dao.answers(), reflections = dao.reflections(), schedules = dao.schedules(), preferences = dao.preferences() ?: Preferences()))
    }
    // Additive merge only. Existing IDs and settings are preserved, never silently overwritten.
    // Imported schedules remain paused until the user deliberately enables each one.
    suspend fun merge(b: Backup): Int = db.withTransaction {
        BackupCodec.validate(b)
        val contentIds = dao.content().map { it.id }.toSet()
        val forms = dao.forms().map { it.id }.toSet()
        val responseIds = dao.responses().map { it.id }.toSet()
        val scheduleIds = dao.schedules().map { it.id }.toSet()
        val newForms = b.forms.filter { it.id !in forms }
        val newFormIds = newForms.map { it.id }.toSet()
        val newResponses = b.responses.filter { it.id !in responseIds && it.formId in newFormIds }
        val newResponseIds = newResponses.map { it.id }.toSet()
        val existingQuestions = dao.questions().map { it.id }.toSet()
        val existingAnswers = dao.answers().map { it.id }.toSet()
        val existingReflections = dao.reflections().map { it.id }.toSet()
        require(b.questions.none { it.formId in newFormIds && it.id in existingQuestions } &&
            b.answers.none { it.responseId in newResponseIds && it.id in existingAnswers } &&
            b.reflections.none { it.responseId in newResponseIds && it.id in existingReflections }) { "This backup has conflicting record identifiers. Nothing was imported." }
        val newContent = b.content.filter { it.id !in contentIds }
        newContent.forEach { dao.put(it) }
        newForms.forEach { dao.put(it) }
        dao.putQuestions(b.questions.filter { it.formId in newFormIds })
        newResponses.forEach { dao.put(it.copy(reviewAt = null)) }
        dao.putAnswers(b.answers.filter { it.responseId in newResponseIds })
        b.reflections.filter { it.responseId in newResponseIds }.forEach { dao.put(it) }
        val newSchedules = b.schedules.filter { it.id !in scheduleIds && (it.formId == null || it.formId in newFormIds) }
        newSchedules.forEach { dao.put(it.copy(enabled = false)) }
        newContent.size + newForms.size + newResponses.size + newSchedules.size
    }
}

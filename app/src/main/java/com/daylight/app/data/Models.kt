package com.daylight.app.data

import androidx.room.*
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
enum class Category { Quotes, Quran, Hadith, Motivation, Reminders, Personal }
enum class QuestionType { Checkbox, MultipleChoice, ShortText, LongText, Number, YesNo, Rating }
enum class ScheduleMode { Fixed, Interval, Random }
enum class ScheduleTarget { Content, Form }

@Serializable
@Entity(tableName = "content", indices = [Index("category"), Index("lastShown")])
data class Content(
    @PrimaryKey val id: String = newId(), val title: String = "", val body: String = "",
    val category: String = Category.Quotes.name, val source: String = "", val reference: String = "",
    val surah: String = "", val verse: String = "", val translation: String = "",
    val favorite: Boolean = false, val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(), val lastShown: Long = 0, val readAt: Long = 0,
)

@Serializable
@Entity(tableName = "forms")
data class PersonalForm(
    @PrimaryKey val id: String = newId(), val title: String = "", val description: String = "",
    val reviewMinute: Int? = 1200, val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "questions", foreignKeys = [ForeignKey(entity = PersonalForm::class, parentColumns = ["id"], childColumns = ["formId"], onDelete = ForeignKey.CASCADE)], indices = [Index("formId")])
data class Question(
    @PrimaryKey val id: String = newId(), val formId: String, val position: Int = 0,
    val title: String = "", val type: String = QuestionType.Checkbox.name,
    val required: Boolean = false, val options: List<String> = emptyList(),
)

@Serializable
@Entity(tableName = "responses", foreignKeys = [ForeignKey(entity = PersonalForm::class, parentColumns = ["id"], childColumns = ["formId"], onDelete = ForeignKey.CASCADE)], indices = [Index("formId"), Index("createdAt")])
data class FormResponse(
    @PrimaryKey val id: String = newId(), val formId: String, val formTitle: String,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val reviewAt: Long? = null,
)

// Answers deliberately snapshot question wording and options: editing a template never rewrites history.
@Serializable
@Entity(tableName = "answers", foreignKeys = [ForeignKey(entity = FormResponse::class, parentColumns = ["id"], childColumns = ["responseId"], onDelete = ForeignKey.CASCADE)], indices = [Index("responseId")])
data class Answer(
    @PrimaryKey val id: String = newId(), val responseId: String, val questionId: String,
    val position: Int, val title: String, val type: String, val required: Boolean = false,
    val options: List<String> = emptyList(), val value: String = "", val selected: List<String> = emptyList(),
)

@Serializable
@Entity(tableName = "reflections", foreignKeys = [ForeignKey(entity = FormResponse::class, parentColumns = ["id"], childColumns = ["responseId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["responseId"], unique = true)])
data class Reflection(
    @PrimaryKey val id: String = newId(), val responseId: String, val text: String = "",
    val tomorrow: String = "", val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "schedules", foreignKeys = [ForeignKey(entity = PersonalForm::class, parentColumns = ["id"], childColumns = ["formId"], onDelete = ForeignKey.CASCADE)], indices = [Index("formId")])
data class Schedule(
    @PrimaryKey val id: String = newId(), val title: String = "", val mode: String = ScheduleMode.Fixed.name,
    val target: String = ScheduleTarget.Content.name, val category: String = Category.Motivation.name,
    val formId: String? = null, val fixedMinutes: List<Int> = listOf(480),
    val startMinute: Int = 480, val endMinute: Int = 1320, val intervalMinutes: Int = 120,
    val randomCount: Int = 3, val minimumSpacing: Int = 120, val enabled: Boolean = true,
)

@Serializable
@Entity(tableName = "preferences")
data class Preferences(
    @PrimaryKey val id: Int = 1, val theme: String = "System", val onboarded: Boolean = false,
    val quietEnabled: Boolean = true, val quietStart: Int = 1320, val quietEnd: Int = 420,
    val sound: Boolean = true, val vibration: Boolean = true,
)

@Entity(tableName = "occurrences", indices = [Index("at"), Index("state")])
data class Occurrence(
    @PrimaryKey val id: String, val ownerId: String, val at: Long,
    val target: String, val route: String = "", val title: String, val category: String = "",
    val state: String = "pending",
)

data class AppData(
    val content: List<Content> = emptyList(), val forms: List<PersonalForm> = emptyList(),
    val questions: List<Question> = emptyList(), val responses: List<FormResponse> = emptyList(),
    val answers: List<Answer> = emptyList(), val reflections: List<Reflection> = emptyList(),
    val schedules: List<Schedule> = emptyList(), val preferences: Preferences = Preferences(),
    val occurrences: List<Occurrence> = emptyList(),
)

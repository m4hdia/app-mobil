package com.daylight.app.data

import androidx.room.withTransaction
import com.daylight.app.domain.FormLogic
import com.daylight.app.domain.SchedulePlanner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.ZonedDateTime

class Repository(val db: DaylightDatabase) {
    val dao = db.dao()
    // Editors initialize saveable drafts from this state. Never expose a form/response
    // before its questions/answers, or combine records from different transactions.
    val data = db.invalidationTracker.createFlow(
        "content", "forms", "questions", "responses", "answers", "reflections",
        "schedules", "preferences", "occurrences",
    ).map {
        db.withTransaction {
            AppData(
                content = dao.content().sortedByDescending { it.createdAt },
                forms = dao.forms().sortedByDescending { it.createdAt },
                questions = dao.questions(), responses = dao.responses(), answers = dao.answers(),
                reflections = dao.reflections().sortedByDescending { it.updatedAt },
                schedules = dao.schedules(), preferences = dao.preferences() ?: Preferences(),
                occurrences = dao.occurrences().filter { it.state == "pending" },
            )
        }
    }.distinctUntilChanged()

    suspend fun saveContent(c: Content) {
        require(c.title.isNotBlank() && c.body.isNotBlank()) { "Add a title and some content first." }
        require(c.category in Category.entries.map { it.name }) { "Choose a valid category." }
        dao.put(c)
    }
    suspend fun saveForm(f: PersonalForm, questions: List<Question>) = db.withTransaction {
        require(f.title.isNotBlank() && questions.isNotEmpty()) { "Add a title and at least one question." }
        require(questions.size <= 100 && questions.all { it.options.size <= 100 }) { "Use up to 100 questions and 100 choices per question." }
        require(f.reviewMinute == null || f.reviewMinute in 0..1439) { "Use a valid review time." }
        require(questions.all { it.title.isNotBlank() }) { "Give each question a title." }
        require(questions.all { it.type in QuestionType.entries.map { type -> type.name } }) { "Choose a valid question type." }
        require(questions.map { it.id }.distinct().size == questions.size) { "Each question must have a unique identifier." }
        val otherQuestionIds = dao.questions().filter { it.formId != f.id }.map { it.id }.toSet()
        require(questions.none { it.id in otherQuestionIds }) { "A question belongs to another form." }
        require(questions.all { it.type !in listOf("Checkbox", "MultipleChoice") || (it.options.isNotEmpty() && it.options.none(String::isBlank) && it.options.distinct().size == it.options.size) }) { "Add unique, non-empty choices to every checkbox or multiple choice question." }
        dao.put(f)
        dao.deleteQuestions(f.id)
        dao.putQuestions(questions.mapIndexed { index, q -> q.copy(formId = f.id, position = index) })
    }
    suspend fun submit(form: PersonalForm, answers: List<Answer>): String = db.withTransaction {
        require(answers.isNotEmpty()) { "This form has no questions." }
        require(FormLogic.error(answers) == null) { FormLogic.error(answers)!! }
        val response = FormResponse(formId = form.id, formTitle = form.title, reviewAt = SchedulePlanner.reviewTime(form.reviewMinute, ZonedDateTime.now()))
        dao.put(response)
        dao.putAnswers(answers.map { it.copy(id = newId(), responseId = response.id) })
        response.id
    }
    suspend fun review(response: FormResponse, answers: List<Answer>, text: String, tomorrow: String) = db.withTransaction {
        val stored = dao.responses().find { it.id == response.id }
        require(stored != null) { "This entry has been deleted." }
        val savedAnswers = dao.answers().filter { it.responseId == response.id }.associateBy { it.id }
        require(answers.size == savedAnswers.size && answers.map { it.id }.toSet() == savedAnswers.keys) { "This entry's answers have changed. Reopen it and try again." }
        require(answers.all { answer ->
            val saved = savedAnswers.getValue(answer.id)
            answer.copy(value = saved.value, selected = saved.selected) == saved
        }) { "Only answer values can be changed during a review." }
        require(FormLogic.error(answers) == null) { FormLogic.error(answers)!! }
        dao.put(stored.copy(updatedAt = System.currentTimeMillis()))
        dao.putAnswers(answers)
        val old = dao.reflections().find { it.responseId == response.id }
        dao.put(Reflection(id = old?.id ?: newId(), responseId = response.id, text = text, tomorrow = tomorrow))
    }
}

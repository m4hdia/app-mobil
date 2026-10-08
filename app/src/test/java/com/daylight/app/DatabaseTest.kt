package com.daylight.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.daylight.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DatabaseTest {
    private lateinit var db: DaylightDatabase
    private lateinit var repo: Repository
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DaylightDatabase::class.java).allowMainThreadQueries().build(); repo = Repository(db) }
    @After fun close() { db.close() }
    @Test fun contentCreateUpdateDelete() = runBlocking {
        val c = Content(title = "My words", body = "Exact text\nمرحبا", category = "Personal")
        repo.saveContent(c); assertEquals(c, db.dao().content().single())
        repo.saveContent(c.copy(favorite = true)); assertTrue(db.dao().content().single().favorite)
        db.dao().deleteContent(c.id); assertTrue(db.dao().content().isEmpty())
    }
    @Test fun responseAndReflectionPersistAndSurviveTemplateEdit() = runBlocking {
        val form = PersonalForm(title = "Goals")
        val q = Question(formId = form.id, title = "Original question", options = listOf("Read", "Build"))
        repo.saveForm(form, listOf(q))
        val a = Answer(responseId = "", questionId = q.id, position = 0, title = q.title, type = q.type, options = q.options, selected = listOf("Read"))
        val id = repo.submit(form, listOf(a))
        repo.saveForm(form, listOf(q.copy(title = "Edited", options = listOf("Sleep"))))
        assertEquals("Original question", db.dao().answers().single().title)
        assertEquals(listOf("Read"), db.dao().answers().single().selected)
        repo.review(db.dao().responses().single(), db.dao().answers(), "A good day", "Read again")
        assertEquals(id, db.dao().reflections().single().responseId)
        db.dao().deleteForm(form.id)
        assertTrue(db.dao().responses().isEmpty()); assertTrue(db.dao().answers().isEmpty()); assertTrue(db.dao().reflections().isEmpty())
    }
    @Test fun backupRoundTripAndDuplicateImportAreSafe() = runBlocking {
        val c = Content(title = "Keep me", body = "Unchanged")
        repo.saveContent(c)
        val service = BackupRepository(db)
        val encoded = service.export()
        val b = BackupCodec.decode(encoded)
        assertEquals(c, b.content.single())
        assertEquals(0, service.merge(b))
        val changed = b.copy(content = listOf(c.copy(body = "Do not overwrite"), Content(title = "New", body = "New content")))
        assertEquals(1, service.merge(changed))
        assertEquals("Unchanged", db.dao().content().first { it.id == c.id }.body)
    }
    @Test fun invalidImportLeavesDatabaseUntouched() = runBlocking {
        repo.saveContent(Content(title = "Safe", body = "My entry"))
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("{\"version\":999}") }
        assertEquals(1, db.dao().content().size)
    }
    @Test fun occurrencesClaimOnlyOnceAndRemainClaimedAfterRescheduling() = runBlocking {
        val event = Occurrence("alarm", "schedule", 100L, "Content", title = "Reminder")
        db.dao().insertOccurrences(listOf(event))
        assertEquals(1, db.dao().claim("alarm", "delivered"))
        assertEquals(0, db.dao().claim("alarm", "delivered"))
        db.dao().clearPending(); db.dao().insertOccurrences(listOf(event))
        assertEquals("delivered", db.dao().occurrences().single().state)
    }
    @Test fun fileDatabasePreservesContentAcrossCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "daylight-persistence-${newId()}.db"
        val content = Content(title = "Persist me", body = "Still here after recreation")
        try {
            val first = Room.databaseBuilder(context, DaylightDatabase::class.java, name).build()
            try { first.dao().put(content) } finally { first.close() }
            val reopened = Room.databaseBuilder(context, DaylightDatabase::class.java, name).build()
            try { assertEquals(content, reopened.dao().content().single()) } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun observedFormsAlwaysIncludeMatchingQuestions() = runBlocking {
        val states = Channel<AppData>(Channel.UNLIMITED)
        val collector = launch { repo.data.collect { states.send(it) } }
        try {
            withTimeout(10_000) {
                assertTrue(states.receive().forms.isEmpty())
                val form = PersonalForm(title = "Version 0")
                val question = Question(formId = form.id, title = form.title, options = listOf("Read"))
                repeat(4) { version ->
                    val title = "Version $version"
                    repo.saveForm(form.copy(title = title), listOf(question.copy(title = title)))
                    do {
                        val snapshot = states.receive()
                        assertEquals(snapshot.forms.single().title, snapshot.questions.single().title)
                    } while (snapshot.forms.single().title != title)
                }
                val responseId = repo.submit(form, listOf(Answer(responseId = "", questionId = question.id,
                    position = 0, title = question.title, type = question.type, options = question.options)))
                do {
                    val snapshot = states.receive()
                    assertEquals(snapshot.responses.map { it.id }.toSet(), snapshot.answers.map { it.responseId }.toSet())
                } while (snapshot.responses.none { it.id == responseId })
                db.dao().deleteForm(form.id)
                do {
                    val snapshot = states.receive()
                    assertEquals(snapshot.forms.isEmpty(), snapshot.questions.isEmpty())
                    assertEquals(snapshot.forms.isEmpty(), snapshot.responses.isEmpty())
                    assertEquals(snapshot.responses.isEmpty(), snapshot.answers.isEmpty())
                } while (snapshot.forms.isNotEmpty())
            }
        } finally { collector.cancel(); states.close() }
    }

    @Test fun incompleteOrRewrittenReviewLeavesSavedEntryUntouched() = runBlocking {
        val form = PersonalForm(title = "Goals")
        val question = Question(formId = form.id, title = "My goals", options = listOf("Read"))
        repo.saveForm(form, listOf(question))
        repo.submit(form, listOf(Answer(responseId = "", questionId = question.id, position = 0,
            title = question.title, type = question.type, options = question.options)))
        val response = db.dao().responses().single()
        val answers = db.dao().answers()
        suspend fun rejected(changed: List<Answer>) {
            try { repo.review(response, changed, "Should not save", ""); fail("Review must be rejected") }
            catch (_: IllegalArgumentException) { }
            assertEquals(answers, db.dao().answers())
            assertEquals(response, db.dao().responses().single())
            assertTrue(db.dao().reflections().isEmpty())
        }
        rejected(emptyList())
        rejected(listOf(answers.single().copy(title = "Rewritten history")))
        rejected(listOf(answers.single().copy(responseId = newId())))
        rejected(answers + answers)
        repo.review(response, listOf(answers.single().copy(selected = listOf("Read"))), "Done", "Continue")
        assertEquals(listOf("Read"), db.dao().answers().single().selected)
        assertEquals("Done", db.dao().reflections().single().text)
    }

    @Test fun deletedResponseCannotBeRecreatedByStaleReview() = runBlocking {
        val form = PersonalForm(title = "Goals")
        val question = Question(formId = form.id, title = "My goals", options = listOf("Read"))
        repo.saveForm(form, listOf(question))
        repo.submit(form, listOf(Answer(responseId = "", questionId = question.id, position = 0,
            title = question.title, type = question.type, options = question.options)))
        val response = db.dao().responses().single()
        val answers = db.dao().answers()
        db.dao().deleteForm(form.id)
        repo.saveForm(form, listOf(question))
        try { repo.review(response, answers, "Stale draft", ""); fail("Deleted entry must stay deleted") }
        catch (_: IllegalArgumentException) { }
        assertTrue(db.dao().responses().isEmpty())
        assertTrue(db.dao().answers().isEmpty())
        assertTrue(db.dao().reflections().isEmpty())
    }

    @Test fun savingFormCannotStealAnotherFormsQuestion() = runBlocking {
        val first = PersonalForm(title = "First")
        val second = PersonalForm(title = "Second")
        val question = Question(formId = first.id, title = "Keep me", options = listOf("Read"))
        repo.saveForm(first, listOf(question))
        try { repo.saveForm(second, listOf(question)); fail("Question ownership must be preserved") }
        catch (_: IllegalArgumentException) { }
        assertEquals(listOf(first), db.dao().forms())
        assertEquals(listOf(question), db.dao().questions())
    }
}

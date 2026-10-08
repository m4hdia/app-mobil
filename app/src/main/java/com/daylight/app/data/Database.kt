package com.daylight.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter fun strings(value: List<String>): String = Json.encodeToString(value)
    @TypeConverter fun toStrings(value: String): List<String> = Json.decodeFromString(value)
    @TypeConverter fun ints(value: List<Int>): String = Json.encodeToString(value)
    @TypeConverter fun toInts(value: String): List<Int> = Json.decodeFromString(value)
}

@Dao
interface DaylightDao {
    @Query("SELECT * FROM content ORDER BY createdAt DESC") fun contentFlow(): Flow<List<Content>>
    @Query("SELECT * FROM forms ORDER BY createdAt DESC") fun formsFlow(): Flow<List<PersonalForm>>
    @Query("SELECT * FROM questions ORDER BY position") fun questionsFlow(): Flow<List<Question>>
    @Query("SELECT * FROM responses ORDER BY createdAt DESC") fun responsesFlow(): Flow<List<FormResponse>>
    @Query("SELECT * FROM answers ORDER BY position") fun answersFlow(): Flow<List<Answer>>
    @Query("SELECT * FROM reflections ORDER BY updatedAt DESC") fun reflectionsFlow(): Flow<List<Reflection>>
    @Query("SELECT * FROM schedules") fun schedulesFlow(): Flow<List<Schedule>>
    @Query("SELECT * FROM preferences WHERE id=1") fun preferencesFlow(): Flow<Preferences?>
    @Query("SELECT * FROM occurrences WHERE state='pending' ORDER BY at") fun occurrencesFlow(): Flow<List<Occurrence>>
    @Query("SELECT * FROM content") suspend fun content(): List<Content>
    @Query("SELECT * FROM forms") suspend fun forms(): List<PersonalForm>
    @Query("SELECT * FROM questions ORDER BY position") suspend fun questions(): List<Question>
    @Query("SELECT * FROM responses ORDER BY createdAt DESC") suspend fun responses(): List<FormResponse>
    @Query("SELECT * FROM answers ORDER BY position") suspend fun answers(): List<Answer>
    @Query("SELECT * FROM reflections") suspend fun reflections(): List<Reflection>
    @Query("SELECT * FROM schedules") suspend fun schedules(): List<Schedule>
    @Query("SELECT * FROM preferences WHERE id=1") suspend fun preferences(): Preferences?
    @Query("SELECT * FROM occurrences ORDER BY at") suspend fun occurrences(): List<Occurrence>
    @Upsert suspend fun put(value: Content)
    @Upsert suspend fun put(value: PersonalForm)
    @Upsert suspend fun putQuestions(value: List<Question>)
    @Upsert suspend fun put(value: FormResponse)
    @Upsert suspend fun putAnswers(value: List<Answer>)
    @Upsert suspend fun put(value: Reflection)
    @Upsert suspend fun put(value: Schedule)
    @Upsert suspend fun put(value: Preferences)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOccurrences(value: List<Occurrence>)
    @Query("DELETE FROM content WHERE id=:id") suspend fun deleteContent(id: String)
    @Query("DELETE FROM forms WHERE id=:id") suspend fun deleteForm(id: String)
    @Query("DELETE FROM questions WHERE formId=:id") suspend fun deleteQuestions(id: String)
    @Query("DELETE FROM schedules WHERE id=:id") suspend fun deleteSchedule(id: String)
    @Query("UPDATE content SET favorite=1 WHERE id=:id") suspend fun favorite(id: String)
    @Query("UPDATE content SET readAt=:now WHERE id=:id") suspend fun read(id: String, now: Long)
    @Query("UPDATE content SET lastShown=:now WHERE id=:id") suspend fun shown(id: String, now: Long)
    @Query("UPDATE content SET lastShown=0, readAt=0") suspend fun resetRotation()
    @Query("DELETE FROM occurrences WHERE state='pending'") suspend fun clearPending()
    @Query("DELETE FROM occurrences WHERE at < :before") suspend fun pruneOccurrences(before: Long)
    @Query("UPDATE occurrences SET state=:state WHERE id=:id AND state='pending'") suspend fun claim(id: String, state: String): Int
    @Query("UPDATE occurrences SET state=:state WHERE id=:id") suspend fun markOccurrence(id: String, state: String)
}

@Database(entities = [Content::class, PersonalForm::class, Question::class, FormResponse::class, Answer::class, Reflection::class, Schedule::class, Preferences::class, Occurrence::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class DaylightDatabase : RoomDatabase() {
    abstract fun dao(): DaylightDao
    companion object {
        fun create(context: Context): DaylightDatabase = Room.databaseBuilder(context, DaylightDatabase::class.java, "daylight.db").build()
    }
}

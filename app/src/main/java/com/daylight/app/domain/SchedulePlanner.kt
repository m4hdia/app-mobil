package com.daylight.app.domain

import com.daylight.app.data.*
import java.time.*
import kotlin.random.Random

object SchedulePlanner {
    fun quiet(minute: Int, prefs: Preferences): Boolean = prefs.quietEnabled && when {
        prefs.quietStart == prefs.quietEnd -> false
        prefs.quietStart < prefs.quietEnd -> minute >= prefs.quietStart && minute < prefs.quietEnd
        else -> minute >= prefs.quietStart || minute < prefs.quietEnd
    }

    fun validate(s: Schedule): String? = when {
        s.title.isBlank() -> "Give this reminder a name."
        s.mode !in ScheduleMode.entries.map { it.name } -> "Choose a schedule type."
        s.target !in ScheduleTarget.entries.map { it.name } -> "Choose a reminder type."
        s.target == "Form" && s.formId == null -> "Choose a form first."
        s.target == "Content" && s.category !in Category.entries.map { it.name } -> "Choose a category."
        s.fixedMinutes.any { it !in 0..1439 } || s.fixedMinutes.distinct().size != s.fixedMinutes.size -> "Use distinct times between 00:00 and 23:59."
        s.mode == "Fixed" && s.fixedMinutes.size !in 1..24 -> "Choose between 1 and 24 daily times."
        s.startMinute !in 0..1439 || s.endMinute !in 1..1439 || s.startMinute >= s.endMinute -> "The time window must begin and end within the same day."
        s.intervalMinutes !in 15..1440 -> "Intervals must be between 15 and 1,440 minutes."
        s.minimumSpacing !in 15..1440 || s.randomCount !in 1..24 -> "Use 1–24 reminders with at least 15 minutes between them."
        else -> null
    }

    fun dayTimes(s: Schedule, day: LocalDate, zone: ZoneId, prefs: Preferences): List<Instant> {
        require(validate(s) == null) { validate(s)!! }
        fun instant(min: Int) = day.atStartOfDay().plusMinutes(min.toLong()).atZone(zone).toInstant()
        val minutes = when (s.mode) {
            "Fixed" -> s.fixedMinutes
            "Interval" -> (s.startMinute..s.endMinute step s.intervalMinutes).toList()
            else -> (s.startMinute..s.endMinute).toList()
        }
        val candidates = minutes.filterNot { quiet(it, prefs) }.map(::instant).distinct().sorted().filterNot {
            val local = it.atZone(zone)
            quiet(local.hour * 60 + local.minute, prefs)
        }
        if (s.mode != "Random") return candidates
        val spacing = Duration.ofMinutes(s.minimumSpacing.toLong())
        // Compute how many spaced picks fit in each suffix. Random choices are restricted to
        // feasible suffixes, so quiet-hour gaps cannot accidentally underfill the day.
        val next = IntArray(candidates.size)
        val capacity = IntArray(candidates.size + 1)
        for (i in candidates.indices.reversed()) {
            var j = i + 1
            while (j < candidates.size && candidates[j] < candidates[i].plus(spacing)) j++
            next[i] = j
            capacity[i] = 1 + capacity[j]
        }
        require(capacity[0] >= s.randomCount) { "This window cannot fit ${s.randomCount} reminders with your spacing and quiet hours." }
        val rng = Random(s.id.hashCode().toLong() xor day.toEpochDay())
        val result = mutableListOf<Instant>()
        var start = 0
        repeat(s.randomCount) { selected ->
            val remaining = s.randomCount - selected - 1
            val feasible = (start until candidates.size).filter { capacity[next[it]] >= remaining }
            val chosen = feasible[rng.nextInt(feasible.size)]
            result += candidates[chosen]
            start = next[chosen]
        }
        return result
    }

    fun plan(schedules: List<Schedule>, responses: List<FormResponse>, prefs: Preferences, now: Instant, zone: ZoneId): List<Occurrence> {
        val start = now.atZone(zone).toLocalDate()
        val slots = mutableListOf<Occurrence>()
        schedules.filter { it.enabled }.forEach { s ->
            repeat(8) { offset ->
                val day = start.plusDays(offset.toLong())
                dayTimes(s, day, zone, prefs).filter { it > now }.forEach { at ->
                    slots += Occurrence("${s.id}:$day:${at.atZone(zone).toLocalTime()}", s.id, at.toEpochMilli(), s.target,
                        s.formId?.let { "fill/$it" } ?: "", s.title, s.category)
                }
            }
        }
        responses.filter { it.reviewAt != null }.forEach { r ->
            var at = Instant.ofEpochMilli(r.reviewAt!!).atZone(zone)
            while (quiet(at.hour * 60 + at.minute, prefs)) at = at.plusMinutes(1)
            if (at.toInstant() > now) slots += Occurrence("review:${r.id}", r.id, at.toInstant().toEpochMilli(), "Review", "response/${r.id}", "Evening reflection · ${r.formTitle}")
        }
        // Reviews take priority at identical times. Suppress collisions instead of nudging
        // random reminders outside their window or breaking minimum spacing.
        val ordered = slots.sortedWith(compareBy<Occurrence> { it.at }.thenBy { if (it.target == "Review") 0 else 1 }.thenBy { it.id })
        val result = mutableListOf<Occurrence>()
        ordered.forEach { if (result.isEmpty() || it.at - result.last().at >= 60_000) result += it }
        return result
    }

    fun reviewTime(minute: Int?, now: ZonedDateTime): Long? = minute?.let {
        var review = now.toLocalDate().atTime(it / 60, it % 60).atZone(now.zone)
        if (!review.isAfter(now)) review = review.plusDays(1)
        review.toInstant().toEpochMilli()
    }
}

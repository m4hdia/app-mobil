package com.daylight.app.domain

import com.daylight.app.data.*

object FormLogic {
    fun error(answers: List<Answer>): String? {
        answers.forEach { a ->
            if (a.type !in QuestionType.entries.map { it.name }) return "Choose a valid answer type."
            if (a.selected.distinct().size != a.selected.size) return "A choice was selected more than once."
            val answered = if (a.type == QuestionType.Checkbox.name) a.selected.isNotEmpty() else a.value.isNotBlank()
            if (a.required && !answered) return "Please answer: ${a.title}"
            if (a.type == QuestionType.Number.name && a.value.isNotEmpty() && (a.value.toDoubleOrNull()?.isFinite() != true)) return "Enter a valid number for ${a.title}."
            if (a.type == QuestionType.Rating.name && a.value.isNotEmpty() && a.value.toIntOrNull() !in 1..5) return "Choose a rating from 1 to 5."
            if (a.selected.any { it !in a.options }) return "A selected option is no longer available."
            if (a.type == QuestionType.MultipleChoice.name && a.value.isNotEmpty() && a.value !in a.options) return "Choose an available option."
            if (a.type == QuestionType.YesNo.name && a.value.isNotEmpty() && a.value !in listOf("Yes", "No")) return "Choose Yes or No."
        }
        return null
    }
    fun progress(answers: List<Answer>): Pair<Int, Int> {
        val checkboxes = answers.filter { it.type == QuestionType.Checkbox.name }
        return if (checkboxes.isNotEmpty()) checkboxes.sumOf { a -> a.selected.count { it in a.options } } to checkboxes.sumOf { it.options.size }
        else answers.count { it.value.isNotBlank() } to answers.size
    }
    fun percent(answers: List<Answer>): Int = progress(answers).let { (done, total) -> if (total == 0) 0 else done * 100 / total }
    fun summary(answers: List<Answer>): String = answers.joinToString("\n\n") { a ->
        if (a.type == QuestionType.Checkbox.name) a.title + "\n" + a.options.joinToString("\n") { "${if (it in a.selected) "☑" else "☐"} $it" }
        else "${a.title}: ${a.value.ifBlank { "—" }}"
    }
}

package com.tonapps.onboading.screens.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay

// One "choose the right word" question: the 1-based position of the word in the recovery
// phrase and the options to pick from, one of which is the correct word.
data class BackupCheckQuestion(
    val wordNumber: Int,
    val options: List<String>,
    val correctWord: String,
)

// Input for the backup-check quiz: the recovery phrase the questions are built from.
class BackupCheckData(
    val words: List<String>,
    val questionCount: Int = 3,
    val optionCount: Int = 3,
)

sealed interface BackupCheckEvent {
    // Every picked word matched the recovery phrase.
    data object Done : BackupCheckEvent

    // At least one picked word did not match. Emitted on every failed attempt.
    data object Error : BackupCheckEvent
}

class BackupCheckFeature(
    data: BackupCheckData,
) : AsyncViewModel() {

    private val relay = MviRelay<BackupCheckEvent>()
    val events = relay.events

    val questions: List<BackupCheckQuestion> = buildBackupCheckQuestions(
        words = data.words,
        questionCount = data.questionCount,
        optionCount = data.optionCount,
    )

    val indexes = questions.map { it.wordNumber }
        .toTypedArray()

    // Question index -> selected option index. Snapshot state, observed directly by the UI.
    val selections = mutableStateMapOf<Int, Int>()

    // Error mode after a failed check: the whole screen turns red, any new pick resets it.
    var hasError by mutableStateOf(false)
        private set

    val continueEnabled: Boolean
        get() = selections.size == questions.size && !hasError

    fun select(index: Int, option: Int) {
        selections[index] = option
        hasError = false
    }

    fun onDone() {
        val hasWrongWord = questions.indices.any { index ->
            val selected = selections[index] ?: return@any true
            questions[index].options[selected] != questions[index].correctWord
        }

        if (hasWrongWord) {
            hasError = true
            relay.emit(BackupCheckEvent.Error)
        } else {
            relay.emit(BackupCheckEvent.Done)
        }
    }

    private fun buildBackupCheckQuestions(
        words: List<String>,
        questionCount: Int = 3,
        optionCount: Int = 3,
    ): List<BackupCheckQuestion> {
        val seenWords = mutableSetOf<String>()
        val positions = words.indices
            .shuffled()
            .filter { seenWords.add(words[it]) }
            .take(questionCount.coerceAtMost(words.size))
            .sorted()

        return positions.map { index ->
            val correctWord = words[index]
            // Distinct wrong options, none equal to the answer, so every chip in the row is unique.
            val distractors = words
                .distinct()
                .filter { it != correctWord }
                .shuffled()
                .take(optionCount - 1)

            BackupCheckQuestion(
                wordNumber = index + 1,
                options = (distractors + correctWord).shuffled(),
                correctWord = correctWord,
            )
        }
    }
}

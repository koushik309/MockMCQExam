package com.koushik.mockmcq.domain

data class ScoredQuestion(
    val selectedLabel: String?,
    val correctLabel: String?,
)

data class ExamResult(
    val total: Int,
    val attempted: Int,
    val correct: Int,
    val incorrect: Int,
    val unanswered: Int,
    val scoredQuestions: Int,
    val missingKeys: Int,
    val score: Double,
    val maximumScore: Double,
    val accuracyPercent: Double,
    val attemptPercent: Double,
    val negativeMarksLost: Double,
)

object ResultCalculator {
    fun calculate(
        questions: List<ScoredQuestion>,
        correctMarks: Double,
        incorrectMarks: Double,
        negativeMarkingEnabled: Boolean,
    ): ExamResult {
        val attempted = questions.count { it.selectedLabel != null }
        val correct = questions.count {
            it.selectedLabel != null && it.correctLabel != null && it.selectedLabel == it.correctLabel
        }
        val incorrect = questions.count {
            it.selectedLabel != null && it.correctLabel != null && it.selectedLabel != it.correctLabel
        }
        val scoredQuestions = questions.count { it.correctLabel != null }
        val scoredAttempts = correct + incorrect
        val penalty = if (negativeMarkingEnabled) incorrect * incorrectMarks else 0.0
        return ExamResult(
            total = questions.size,
            attempted = attempted,
            correct = correct,
            incorrect = incorrect,
            unanswered = questions.size - attempted,
            scoredQuestions = scoredQuestions,
            missingKeys = questions.size - scoredQuestions,
            score = correct * correctMarks + penalty,
            maximumScore = scoredQuestions * correctMarks,
            accuracyPercent = if (scoredAttempts == 0) 0.0 else correct * 100.0 / scoredAttempts,
            attemptPercent = if (questions.isEmpty()) 0.0 else attempted * 100.0 / questions.size,
            negativeMarksLost = -penalty,
        )
    }
}

object AnswerKeyTextParser {
    private val entry = Regex("""(?m)^\s*(?:Q\s*)?(\d+)\s*[-:.)=]\s*([A-Ea-e])\s*${'$'}""")

    fun parse(text: String): Map<Int, String> = entry.findAll(text).associate {
        it.groupValues[1].toInt() to it.groupValues[2].uppercase()
    }
}

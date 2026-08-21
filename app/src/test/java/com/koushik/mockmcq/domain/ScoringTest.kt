package com.koushik.mockmcq.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {
    @Test
    fun calculatesScoreAccuracyAndNegativeMarks() {
        val questions = listOf(
            ScoredQuestion("A", "A"),
            ScoredQuestion("B", "C"),
            ScoredQuestion(null, "D"),
            ScoredQuestion("D", "D"),
        )
        val result = ResultCalculator.calculate(questions, 2.0, -0.66, true)
        assertEquals(3, result.attempted)
        assertEquals(2, result.correct)
        assertEquals(1, result.incorrect)
        assertEquals(3.34, result.score, 0.0001)
        assertEquals(66.666, result.accuracyPercent, 0.01)
        assertEquals(0.66, result.negativeMarksLost, 0.0001)
    }

    @Test
    fun parsesCommonAnswerKeyText() {
        val parsed = AnswerKeyTextParser.parse("1-C\nQ2: b\n3)D\ninvalid")
        assertEquals(mapOf(1 to "C", 2 to "B", 3 to "D"), parsed)
    }
}

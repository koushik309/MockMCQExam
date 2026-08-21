package com.koushik.mockmcq.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfAnswerKeyParserTest {
    private val parser = PdfAnswerKeyParser()

    @Test
    fun parsesTaggedAnswerVariantsWithoutCrossingQuestionBoundaries() {
        val input = """
            #Sample-MCQ1
            Question one
            Answer – B
            Explanation

            #Sample-MCQ2
            Question two has no explicit key
            Explanation only

            #Sample-MCQ3
            Question three
            Answwr - D

            #Sample-MCQ4
            Question four
            Answer A      More explanation
        """.trimIndent()

        val result = parser.parsePages(listOf(input))

        assertEquals(mapOf(1 to "B", 3 to "D", 4 to "A"), result.answers)
        assertEquals(listOf(2), result.missingQuestionNumbers)
    }
}

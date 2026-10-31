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

    @Test
    fun parsesPublisherMarkersWithSpacesAndIrregularPunctuation() {
        val input = """
            #LXMT- CH 20 to 25 & 28 to 31-MCQ1
            Answer - B

            LXMT- CH 20 to 25 & 28 to 31-MCQ26
            Answer: C

            #LXMT- CH 20 to 25 & 28 to 31-MCQ73?
            Answer D
        """.trimIndent()

        val result = parser.parsePages(listOf(input))

        assertEquals(mapOf(1 to "B", 26 to "C", 73 to "D"), result.answers)
    }
}

package com.koushik.mockmcq.data.pdf

import com.koushik.mockmcq.data.db.ParsingConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfQuestionParserTest {
    private val parser = PdfQuestionParser()

    @Test
    fun parsesMultilineQuestionAndFourOptions() {
        val input = """
            12. Which Article of the Constitution
            provides the Right to Constitutional
            Remedies?

            (a) Article 14
            (b) Article 19
            (c) Article 21
            (d) Article 32
        """.trimIndent()

        val result = parser.parsePages(listOf(input)).single()

        assertEquals(12, result.number)
        assertEquals("Which Article of the Constitution provides the Right to Constitutional Remedies?", result.text)
        assertEquals(listOf("A", "B", "C", "D"), result.options.map { it.label })
        assertEquals("Article 32", result.options.last().text)
        assertEquals(ParsingConfidence.HIGH, result.confidence)
    }

    @Test
    fun doesNotTreatNumberedStatementsAsQuestions() {
        val input = """
            Q.1 Consider the following statements:
            1. Statement one
            2. Statement two
            3. Statement three
            Which statements are correct?

            A) 1 and 2 only
            B) 2 and 3 only
            C) 1 and 3 only
            D) 1, 2 and 3

            Q2. A second question?
            A. One
            B. Two
            C. Three
            D. Four
        """.trimIndent()

        val result = parser.parsePages(listOf(input))

        assertEquals(2, result.size)
        assertEquals(listOf(1, 2), result.map { it.number })
        assertTrue(result.first().text.contains("1. Statement one"))
    }

    @Test
    fun supportsQuestionContinuationAcrossPages() {
        val pages = listOf(
            """
                1. A long question?
                (a) First
                (b) Second
            """.trimIndent(),
            """
                (c) Third
                (d) Fourth

                2. Next question?
                (a) A
                (b) B
                (c) C
                (d) D
            """.trimIndent(),
        )

        val result = parser.parsePages(pages)

        assertEquals(2, result.size)
        assertEquals(4, result.first().options.size)
        assertEquals(1, result.first().pageNumber)
        assertEquals(2, result.last().pageNumber)
    }

    @Test
    fun assignsLowConfidenceWhenOptionsAreMissing() {
        val result = parser.parsePages(listOf("5) Question with a missing option?\n(a) Only one"))
        assertEquals(ParsingConfidence.LOW, result.single().confidence)
    }

    @Test
    fun removesRepeatedPageMargins() {
        val pages = (1..3).map { page ->
            """
                MOCK TEST SERIES
                ${page}. Question $page?
                (a) A
                (b) B
                (c) C
                (d) D
                Page footer
            """.trimIndent()
        }
        val result = parser.parsePages(pages)
        assertEquals(3, result.size)
        assertTrue(result.none { it.text.contains("MOCK TEST SERIES") || it.options.last().text.contains("Page footer") })
    }
}

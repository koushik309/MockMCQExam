package com.koushik.mockmcq.data.pdf

data class ParsedAnswerKey(
    val answers: Map<Int, String>,
    val detectedQuestionNumbers: Set<Int>,
) {
    val missingQuestionNumbers: List<Int>
        get() = (detectedQuestionNumbers - answers.keys).sorted()
}

class PdfAnswerKeyParser {
    private val answerPattern = Regex(
        """(?im)^(?:Answer|Answwr)\s*(?:[-–—:]\s*)?([A-F])\b""",
    )

    fun parsePages(pages: List<String>): ParsedAnswerKey {
        val text = pages.joinToString("\n")
        val markers = McqBoundaryPatterns.markerSections.findAll(text).toList()
        val answers = linkedMapOf<Int, String>()
        val detected = linkedSetOf<Int>()
        markers.forEachIndexed { index, marker ->
            val number = marker.groupValues[1].toInt()
            detected += number
            val sectionEnd = markers.getOrNull(index + 1)?.range?.first ?: text.length
            val section = text.substring(marker.range.last + 1, sectionEnd)
            answerPattern.find(section)?.let { answers[number] = it.groupValues[1].uppercase() }
        }
        return ParsedAnswerKey(answers, detected)
    }
}

package com.koushik.mockmcq.data.pdf

import com.koushik.mockmcq.data.db.ParsingConfidence

data class ParsedOption(val label: String, val text: String)

data class ParsedQuestion(
    val number: Int,
    val text: String,
    val options: List<ParsedOption>,
    val pageNumber: Int,
    val confidence: String,
)

class PdfQuestionParser {
    private val questionPattern = Regex(
        """^(?:(?:Q(?:uestion)?(?:\s+No\.?)?\s*\.?\s*)?)(\d{1,4})\s*[.)\-:]?\s+(.+)${'$'}""",
        RegexOption.IGNORE_CASE,
    )
    private val explicitQuestionPattern = Regex(
        """^(?:Q(?:uestion)?(?:\s+No\.?)?\s*\.?\s*)\d+""",
        RegexOption.IGNORE_CASE,
    )
    private val optionPattern = Regex(
        """^\s*[\[(]?([A-Ea-e])[\]).:\-]\s*(.*)${'$'}""",
    )

    fun parsePages(rawPages: List<String>): List<ParsedQuestion> {
        val pages = removeRepeatedMargins(rawPages)
        val parsed = mutableListOf<ParsedQuestion>()
        var builder: QuestionBuilder? = null
        var previousWasBlank = true

        pages.forEachIndexed { pageIndex, page ->
            previousWasBlank = true
            page.lineSequence().forEach { rawLine ->
                val line = normalizeLine(rawLine)
                if (line.isBlank()) {
                    previousWasBlank = true
                    return@forEach
                }

                val optionMatch = optionPattern.matchEntire(line)
                if (builder != null && optionMatch != null) {
                    builder!!.startOption(
                        optionMatch.groupValues[1].uppercase(),
                        optionMatch.groupValues[2].trim(),
                    )
                    previousWasBlank = false
                    return@forEach
                }

                val questionMatch = questionPattern.matchEntire(line)
                val candidateNumber = questionMatch?.groupValues?.get(1)?.toIntOrNull()
                val isPlausibleBoundary = questionMatch != null && (
                    builder == null ||
                        explicitQuestionPattern.containsMatchIn(line) ||
                        (builder!!.options.size >= 2 && previousWasBlank &&
                            candidateNumber == builder!!.number + 1)
                    )

                if (isPlausibleBoundary) {
                    builder?.build()?.let(parsed::add)
                    builder = QuestionBuilder(
                        number = candidateNumber!!,
                        firstLine = questionMatch!!.groupValues[2].trim(),
                        pageNumber = pageIndex + 1,
                    )
                } else {
                    builder?.append(line)
                }
                previousWasBlank = false
            }
        }
        builder?.build()?.let(parsed::add)

        return parsed
            .filter { it.text.isNotBlank() }
            .distinctBy { it.number }
            .sortedBy { it.number }
    }

    private fun normalizeLine(line: String): String = line
        .replace('\u00A0', ' ')
        .replace(Regex("[ \\t]+"), " ")
        .trim()

    private fun removeRepeatedMargins(rawPages: List<String>): List<String> {
        if (rawPages.size < 3) return rawPages
        val candidates = rawPages.flatMap { page ->
            val lines = page.lineSequence().map(::normalizeLine).filter(String::isNotBlank).toList()
            (lines.take(2) + lines.takeLast(2)).distinct()
        }
        val threshold = (rawPages.size * 0.6).toInt().coerceAtLeast(2)
        val repeated = candidates.groupingBy { it }.eachCount()
            .filterValues { it >= threshold }
            .keys
            .filter { it.length > 2 }
            .toSet()
        return rawPages.map { page ->
            page.lineSequence().filterNot { normalizeLine(it) in repeated }.joinToString("\n")
        }
    }

    private class QuestionBuilder(
        val number: Int,
        firstLine: String,
        val pageNumber: Int,
    ) {
        private val questionLines = mutableListOf(firstLine)
        private val optionBuilders = mutableListOf<OptionBuilder>()
        val options: List<OptionBuilder> get() = optionBuilders

        fun startOption(label: String, text: String) {
            optionBuilders += OptionBuilder(label, mutableListOf(text).filter(String::isNotBlank).toMutableList())
        }

        fun append(line: String) {
            if (optionBuilders.isEmpty()) questionLines += line else optionBuilders.last().lines += line
        }

        fun build(): ParsedQuestion {
            val parsedOptions = optionBuilders.map {
                ParsedOption(it.label, it.lines.joinToString(" ").trim())
            }
            val labels = parsedOptions.map { it.label }
            val sequential = labels == ('A'..'E').take(labels.size).map(Char::toString)
            val confidence = when {
                questionLines.joinToString(" ").isBlank() || parsedOptions.size < 2 -> ParsingConfidence.LOW
                parsedOptions.size >= 4 && sequential && parsedOptions.none { it.text.isBlank() } -> ParsingConfidence.HIGH
                else -> ParsingConfidence.MEDIUM
            }
            return ParsedQuestion(
                number = number,
                text = questionLines.joinToString(" ").trim(),
                options = parsedOptions,
                pageNumber = pageNumber,
                confidence = confidence,
            )
        }
    }

    private data class OptionBuilder(val label: String, val lines: MutableList<String>)
}

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
        """^(?:(?:Q(?:uestion)?(?:\s+No\.?)?\s*[.:#\-]?\s*)?)(\d{1,4})\s*[.)\-:]?\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    private val explicitQuestionPattern = Regex(
        """^(?:Q(?:uestion)?(?:\s+No\.?)?\s*[.:#\-]?\s*)\d+""",
        RegexOption.IGNORE_CASE,
    )
    private val optionsHeadingPattern = Regex(
        """^(?:Options?|Choices?|Answer\s+Choices?)\s*[,.:]?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val punctuatedOptionMarker = Regex("""(?<!\S)[\[(]?([A-Fa-f])[\]).:]([ \t]*)""")
    private val unpunctuatedLeadingMarker = Regex("""^\s*([A-Fa-f])\s+""")
    private val leadingPunctuatedOptionPattern = Regex(
        """^\s*[\[(]?([A-Fa-f])[\]).:]\s*(.*)$""",
    )
    private val leadingBareOptionPattern = Regex("""^\s*([A-Fa-f])\s+(.*)$""")

    fun parsePages(rawPages: List<String>): List<ParsedQuestion> {
        val pages = removeRepeatedMargins(rawPages)
        val usesTaggedMcqMarkers = pages.any { page ->
            page.lineSequence().any { McqBoundaryPatterns.matches(normalizeLine(it)) }
        }
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

                McqBoundaryPatterns.match(line)?.let { marker ->
                    builder?.build()?.let(parsed::add)
                    builder = QuestionBuilder(
                        number = marker.number,
                        firstLine = marker.questionText,
                        pageNumber = pageIndex + 1,
                    )
                    previousWasBlank = false
                    return@forEach
                }

                if (builder != null && optionsHeadingPattern.matches(line)) {
                    builder!!.optionMode = true
                    previousWasBlank = false
                    return@forEach
                }

                val optionSegments = builder?.let { findOptionSegments(rawLine, it.optionMode) }.orEmpty()
                if (builder != null && optionSegments.isNotEmpty()) {
                    builder!!.optionMode = true
                    optionSegments.forEach { builder!!.startOption(it.label, it.text) }
                    previousWasBlank = false
                    return@forEach
                }

                val questionMatch = questionPattern.matchEntire(line)
                val candidateNumber = questionMatch?.groupValues?.get(1)?.toIntOrNull()
                val isExplicitBoundary = questionMatch != null && explicitQuestionPattern.containsMatchIn(line)
                val isSequentialBoundary = questionMatch != null && builder != null &&
                    builder!!.options.size >= 2 && previousWasBlank && candidateNumber == builder!!.number + 1
                val isPlausibleBoundary = questionMatch != null && (
                    (!usesTaggedMcqMarkers && builder == null) || isExplicitBoundary || isSequentialBoundary
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

    private fun findOptionSegments(rawLine: String, optionMode: Boolean): List<ParsedOption> {
        val cleanRaw = rawLine.replace('\u00A0', ' ').trimEnd()
        val markers = punctuatedOptionMarker.findAll(cleanRaw).map { match ->
            OptionMarker(
                label = match.groupValues[1].uppercase().single(),
                start = match.range.first,
                contentStart = match.range.last + 1,
                hasFollowingSpace = match.groupValues[2].isNotEmpty(),
            )
        }.toMutableList()
        unpunctuatedLeadingMarker.find(cleanRaw)?.let { match ->
            if (markers.none { it.start == match.range.first }) {
                markers += OptionMarker(
                    label = match.groupValues[1].uppercase().single(),
                    start = match.range.first,
                    contentStart = match.range.last + 1,
                    hasFollowingSpace = true,
                )
            }
        }
        markers.sortBy(OptionMarker::start)
        if (markers.isNotEmpty() && cleanRaw.substring(0, markers.first().start).isBlank()) {
            val selected = mutableListOf(markers.first())
            var expected = markers.first().label + 1
            while (expected <= 'F') {
                val candidates = markers.filter { it.label == expected && it.start > selected.last().start }
                if (candidates.isEmpty()) break
                selected += candidates.filter(OptionMarker::hasFollowingSpace).lastOrNull() ?: candidates.last()
                expected++
            }
            return selected.mapIndexed { index, marker ->
                val end = selected.getOrNull(index + 1)?.start ?: cleanRaw.length
                ParsedOption(
                    label = marker.label.toString(),
                    text = normalizeLine(cleanRaw.substring(marker.contentStart, end)),
                )
            }
        }
        val normalized = normalizeLine(cleanRaw)
        val leading = leadingPunctuatedOptionPattern.matchEntire(normalized)
            ?: (if (optionMode) leadingBareOptionPattern.matchEntire(normalized) else null)
            ?: return emptyList()
        return listOf(ParsedOption(leading.groupValues[1].uppercase(), leading.groupValues[2].trim()))
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
        private val questionLines = mutableListOf(firstLine).filter(String::isNotBlank).toMutableList()
        private val optionBuilders = mutableListOf<OptionBuilder>()
        val options: List<OptionBuilder> get() = optionBuilders
        var optionMode: Boolean = false

        fun startOption(label: String, text: String) {
            val existing = optionBuilders.indexOfFirst { it.label == label }
            if (existing >= 0) {
                optionBuilders[existing].lines += text
            } else {
                optionBuilders += OptionBuilder(label, mutableListOf(text).filter(String::isNotBlank).toMutableList())
            }
        }

        fun append(line: String) {
            if (optionMode && optionBuilders.isNotEmpty()) optionBuilders.last().lines += line
            else questionLines += line
        }

        fun build(): ParsedQuestion {
            val parsedOptions = optionBuilders.map {
                ParsedOption(it.label, it.lines.joinToString(" ").trim())
            }
            val labels = parsedOptions.map { it.label }
            val sequential = labels == ('A'..'F').take(labels.size).map(Char::toString)
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
    private data class OptionMarker(
        val label: Char,
        val start: Int,
        val contentStart: Int,
        val hasFollowingSpace: Boolean,
    )
}

internal data class McqBoundary(val number: Int, val questionText: String = "")

internal object McqBoundaryPatterns {
    private const val PREFIX = """(?:#[^\r\n]*?|[\p{L}\p{N}][^\r\n]*[\p{Pd}_])?\s*"""
    private const val NUMBER = """MCQ\s*[-#:]?\s*(\d{1,4})"""
    private val markerOnly = Regex(
        """^$PREFIX$NUMBER\s*[?.:]?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val markerWithQuestion = Regex(
        """^$PREFIX$NUMBER\s*(?:[.):\-]\s*|\s+)(.+)$""",
        RegexOption.IGNORE_CASE,
    )
    val markerSections = Regex(
        """(?m)^$PREFIX$NUMBER\s*[?.:]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun matches(line: String): Boolean = markerOnly.matches(line) || markerWithQuestion.matches(line)

    fun match(line: String): McqBoundary? {
        markerOnly.matchEntire(line)?.let {
            return McqBoundary(number = it.groupValues[1].toInt())
        }
        markerWithQuestion.matchEntire(line)?.let {
            return McqBoundary(
                number = it.groupValues[1].toInt(),
                questionText = it.groupValues[2].trim(),
            )
        }
        return null
    }
}

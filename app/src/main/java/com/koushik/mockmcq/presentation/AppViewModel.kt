package com.koushik.mockmcq.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.koushik.mockmcq.data.db.ExamStatus
import com.koushik.mockmcq.data.db.ExamSummaryDb
import com.koushik.mockmcq.data.pdf.PdfAnswerKeyParser
import com.koushik.mockmcq.data.pdf.PdfExtractionResult
import com.koushik.mockmcq.data.pdf.PdfQuestionParser
import com.koushik.mockmcq.data.pdf.PdfTextExtractor
import com.koushik.mockmcq.data.repository.ExamRepository
import com.koushik.mockmcq.data.repository.ExamSession
import com.koushik.mockmcq.domain.AnswerKeyTextParser
import com.koushik.mockmcq.domain.ExamResult
import com.koushik.mockmcq.domain.ResultCalculator
import com.koushik.mockmcq.domain.ScoredQuestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen { HOME, PARSING_REVIEW, EXAM, RESULT, REVIEW }

data class ImportUiState(
    val isImporting: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val error: String? = null,
)

data class AnswerKeyImportUiState(
    val isImporting: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val importedCount: Int = 0,
    val totalQuestions: Int = 0,
    val missingQuestionNumbers: List<Int> = emptyList(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(private val repository: ExamRepository) : ViewModel() {
    val exams: StateFlow<List<ExamSummaryDb>> = repository.observeExamSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedExamId = MutableStateFlow<String?>(null)
    val session: StateFlow<ExamSession?> = selectedExamId.flatMapLatest { examId ->
        if (examId == null) flowOf(null) else repository.observeSession(examId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val screen = MutableStateFlow(AppScreen.HOME)
    val importState = MutableStateFlow(ImportUiState())
    val answerKeyImportState = MutableStateFlow(AnswerKeyImportUiState())
    val actionError = MutableStateFlow<String?>(null)

    fun importPdf(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch {
            importState.value = ImportUiState(isImporting = true)
            when (val extraction = PdfTextExtractor(context.applicationContext).extract(uri) { page, total ->
                importState.update { it.copy(currentPage = page, totalPages = total) }
            }) {
                is PdfExtractionResult.Failure -> {
                    importState.value = ImportUiState(error = extraction.message)
                }
                is PdfExtractionResult.Success -> {
                    val questions = PdfQuestionParser().parsePages(extraction.pages)
                    if (questions.isEmpty()) {
                        importState.value = ImportUiState(
                            error = "No MCQ questions were detected. Please try a text-based PDF using numbered questions and lettered options.",
                        )
                        return@launch
                    }
                    val examId = repository.createExam(
                        name = displayName.substringBeforeLast('.').ifBlank { "Imported Mock Test" },
                        pdfName = displayName,
                        pdfUri = uri.toString(),
                        parsedQuestions = questions,
                    )
                    selectedExamId.value = examId
                    importState.value = ImportUiState()
                    screen.value = AppScreen.PARSING_REVIEW
                }
            }
        }
    }

    fun openExam(examId: String, start: Boolean = true) {
        if (selectedExamId.value != examId) {
            answerKeyImportState.value = AnswerKeyImportUiState()
        }
        selectedExamId.value = examId
        launchSafely {
            val summary = exams.value.firstOrNull { it.exam.examId == examId }
            if (!repository.hasQuestions(examId)) {
                selectedExamId.value = null
                screen.value = AppScreen.HOME
                actionError.value = EMPTY_EXAM_MESSAGE
                return@launchSafely
            }
            if (summary?.exam?.status == ExamStatus.SUBMITTED) {
                screen.value = AppScreen.RESULT
            } else if (start) {
                if (!repository.startOrResume(examId)) {
                    actionError.value = EMPTY_EXAM_MESSAGE
                    return@launchSafely
                }
                screen.value = AppScreen.EXAM
            } else {
                screen.value = AppScreen.PARSING_REVIEW
            }
        }
    }

    fun startSelectedExam() {
        val id = selectedExamId.value ?: return
        launchSafely {
            if (!repository.startOrResume(id)) {
                actionError.value = EMPTY_EXAM_MESSAGE
                return@launchSafely
            }
            session.value?.questions?.getOrNull(session.value?.exam?.currentQuestionIndex ?: 0)?.let {
                repository.markVisited(id, it.question.questionId)
            }
            screen.value = AppScreen.EXAM
        }
    }

    fun goHome() {
        screen.value = AppScreen.HOME
        selectedExamId.value = null
        answerKeyImportState.value = AnswerKeyImportUiState()
    }

    fun navigateTo(index: Int) {
        val current = session.value ?: return
        if (index !in current.questions.indices) return
        launchSafely {
            repository.setCurrentQuestion(current.exam.examId, index)
            repository.markVisited(current.exam.examId, current.questions[index].question.questionId)
        }
    }

    fun selectOption(optionId: String) {
        val current = session.value ?: return
        val question = current.questions.getOrNull(current.exam.currentQuestionIndex) ?: return
        launchSafely {
            repository.selectAnswer(current.exam.examId, question.question.questionId, optionId)
        }
    }

    fun clearResponse() {
        val current = session.value ?: return
        val question = current.questions.getOrNull(current.exam.currentQuestionIndex) ?: return
        launchSafely { repository.clearAnswer(current.exam.examId, question.question.questionId) }
    }

    fun toggleMarked() {
        val current = session.value ?: return
        val question = current.questions.getOrNull(current.exam.currentQuestionIndex) ?: return
        launchSafely { repository.toggleMarked(current.exam.examId, question.question.questionId) }
    }

    fun submit() {
        val id = session.value?.exam?.examId ?: return
        launchSafely {
            repository.submit(id)
            screen.value = AppScreen.RESULT
        }
    }

    fun submitIfExpired(now: Long) {
        val current = session.value ?: return
        if (current.exam.status == ExamStatus.IN_PROGRESS && current.exam.endAt?.let { it <= now } == true) {
            submit()
        }
    }

    fun importAnswerKey(text: String): Int {
        val current = session.value ?: return 0
        val parsed = AnswerKeyTextParser.parse(text)
        if (parsed.isNotEmpty()) {
            viewModelScope.launch { repository.importAnswerKey(current.exam.examId, parsed) }
        }
        return parsed.size
    }

    fun importAnswerKeyPdf(context: Context, uri: Uri) {
        val current = session.value ?: return
        viewModelScope.launch {
            answerKeyImportState.value = AnswerKeyImportUiState(isImporting = true)
            when (val extraction = PdfTextExtractor(context.applicationContext).extract(uri) { page, total ->
                answerKeyImportState.update { it.copy(currentPage = page, totalPages = total) }
            }) {
                is PdfExtractionResult.Failure -> {
                    answerKeyImportState.value = AnswerKeyImportUiState(error = extraction.message)
                }
                is PdfExtractionResult.Success -> {
                    val parsed = PdfAnswerKeyParser().parsePages(extraction.pages)
                    if (parsed.detectedQuestionNumbers.isEmpty()) {
                        answerKeyImportState.value = AnswerKeyImportUiState(
                            error = "No supported tagged MCQ answer sections were detected in this PDF.",
                        )
                        return@launch
                    }
                    val questionNumbers = current.questions.map { it.question.questionNumber }.toSet()
                    val matchingAnswers = parsed.answers.filterKeys { it in questionNumbers }
                    if (matchingAnswers.isEmpty()) {
                        answerKeyImportState.value = AnswerKeyImportUiState(
                            error = "This answer PDF does not match the current test.",
                        )
                        return@launch
                    }
                    repository.importAnswerKey(current.exam.examId, matchingAnswers)
                    answerKeyImportState.value = AnswerKeyImportUiState(
                        importedCount = matchingAnswers.size,
                        totalQuestions = questionNumbers.size,
                        missingQuestionNumbers = (questionNumbers - matchingAnswers.keys).sorted(),
                    )
                }
            }
        }
    }

    fun showReview() { screen.value = AppScreen.REVIEW }
    fun showResult() { screen.value = AppScreen.RESULT }
    fun clearImportError() { importState.update { it.copy(error = null) } }
    fun clearAnswerKeyImportError() { answerKeyImportState.update { it.copy(error = null) } }
    fun clearActionError() { actionError.value = null }

    private fun launchSafely(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                actionError.value = "The operation failed: ${error.message ?: error::class.java.simpleName}"
            }
        }
    }

    fun calculateResult(current: ExamSession): ExamResult {
        val scored = current.questions.map { question ->
            val answer = current.answers[question.question.questionId]
            val selectedLabel = question.options.firstOrNull { it.optionId == answer?.selectedOptionId }?.optionLabel
            val correctLabel = current.answerKeys[question.question.questionId]?.correctOptionLabel
            ScoredQuestion(selectedLabel, correctLabel)
        }
        return ResultCalculator.calculate(
            questions = scored,
            correctMarks = current.exam.correctMarks,
            incorrectMarks = current.exam.incorrectMarks,
            negativeMarkingEnabled = current.exam.negativeMarkingEnabled,
        )
    }

    companion object {
        private const val EMPTY_EXAM_MESSAGE =
            "No stored questions were found for this test. The previous app version may have damaged it. Import the question PDF again to create a fresh test."

        fun factory(repository: ExamRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(repository) as T
            }
    }
}

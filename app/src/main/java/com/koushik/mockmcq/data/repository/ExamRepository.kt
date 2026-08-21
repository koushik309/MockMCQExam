package com.koushik.mockmcq.data.repository

import androidx.room.withTransaction
import com.koushik.mockmcq.data.db.AnswerEntity
import com.koushik.mockmcq.data.db.AnswerKeyEntity
import com.koushik.mockmcq.data.db.AppDatabase
import com.koushik.mockmcq.data.db.ExamEntity
import com.koushik.mockmcq.data.db.ExamStatus
import com.koushik.mockmcq.data.db.ExamSummaryDb
import com.koushik.mockmcq.data.db.OptionEntity
import com.koushik.mockmcq.data.db.QuestionEntity
import com.koushik.mockmcq.data.db.QuestionWithOptions
import com.koushik.mockmcq.data.pdf.ParsedQuestion
import com.koushik.mockmcq.domain.ExamClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ExamSession(
    val exam: ExamEntity,
    val questions: List<QuestionWithOptions>,
    val answers: Map<String, AnswerEntity>,
    val answerKeys: Map<String, AnswerKeyEntity>,
)

class ExamRepository(private val database: AppDatabase) {
    private val dao = database.examDao()
    private val answerMutex = Mutex()
    private val examStateMutex = Mutex()

    fun observeExamSummaries(): Flow<List<ExamSummaryDb>> = dao.observeExamSummaries()

    fun observeSession(examId: String): Flow<ExamSession?> = combine(
        dao.observeExam(examId),
        dao.observeQuestions(examId),
        dao.observeAnswers(examId),
        dao.observeAnswerKeys(examId),
    ) { exam, questions, answers, keys ->
        exam?.let {
            ExamSession(
                exam = it,
                questions = questions,
                answers = answers.associateBy(AnswerEntity::questionId),
                answerKeys = keys.associateBy(AnswerKeyEntity::questionId),
            )
        }
    }

    suspend fun createExam(
        name: String,
        pdfName: String,
        pdfUri: String,
        parsedQuestions: List<ParsedQuestion>,
    ): String = database.withTransaction {
        val examId = UUID.randomUUID().toString()
        val exam = ExamEntity(
            examId = examId,
            examName = name,
            pdfName = pdfName,
            pdfUri = pdfUri,
            importedAt = System.currentTimeMillis(),
            totalQuestions = parsedQuestions.size,
        )
        dao.insertExam(exam)
        val questionEntities = parsedQuestions.map { parsed ->
            QuestionEntity(
                questionId = "$examId-q-${parsed.number}",
                examId = examId,
                questionNumber = parsed.number,
                questionText = parsed.text,
                pageNumber = parsed.pageNumber,
                parsingConfidence = parsed.confidence,
            )
        }
        dao.insertQuestions(questionEntities)
        val questionIdsByNumber = questionEntities.associate { it.questionNumber to it.questionId }
        val options = parsedQuestions.flatMap { parsed ->
            parsed.options.mapIndexed { index, option ->
                val questionId = checkNotNull(questionIdsByNumber[parsed.number])
                OptionEntity(
                    optionId = "$questionId-${option.label}",
                    questionId = questionId,
                    optionLabel = option.label,
                    optionText = option.text,
                    displayOrder = index,
                )
            }
        }
        dao.insertOptions(options)
        examId
    }

    suspend fun hasQuestions(examId: String): Boolean = dao.countQuestions(examId) > 0

    suspend fun startOrResume(examId: String): Boolean = examStateMutex.withLock {
        if (!hasQuestions(examId)) return@withLock false
        val exam = dao.getExam(examId) ?: return@withLock false
        if (exam.status == ExamStatus.SUBMITTED) return@withLock true
        if (exam.status == ExamStatus.NOT_STARTED || exam.status == ExamStatus.PAUSED || exam.endAt == null) {
            val now = System.currentTimeMillis()
            val remaining = exam.remainingTimeMillis
                ?: ExamClock.initialRemainingMillis(exam.durationMinutes)
            dao.updateExamStartState(
                examId = examId,
                status = ExamStatus.IN_PROGRESS,
                startedAt = now,
                endAt = ExamClock.resumedEndAt(remaining, now),
                remainingTimeMillis = remaining,
            )
        }
        true
    }

    suspend fun pauseExam(examId: String, pausedAt: Long = System.currentTimeMillis()) = examStateMutex.withLock {
        val exam = dao.getExam(examId) ?: return@withLock
        if (exam.status != ExamStatus.IN_PROGRESS) return@withLock
        val remaining = ExamClock.remainingMillis(exam.endAt, pausedAt)
        dao.updateExamPauseState(
            examId = examId,
            status = ExamStatus.PAUSED,
            remainingTimeMillis = remaining,
        )
    }

    suspend fun checkpointExamTime(examId: String, checkpointAt: Long = System.currentTimeMillis()) =
        examStateMutex.withLock {
            dao.checkpointExamTime(examId, checkpointAt, ExamStatus.IN_PROGRESS)
        }

    suspend fun recoverInterruptedExams(recoveredAt: Long = System.currentTimeMillis()) = examStateMutex.withLock {
        dao.getExamsByStatus(ExamStatus.IN_PROGRESS).forEach { exam ->
            dao.updateExamPauseState(
                examId = exam.examId,
                status = ExamStatus.PAUSED,
                remainingTimeMillis = ExamClock.recoveredRemainingMillis(
                    checkpointMillis = exam.remainingTimeMillis,
                    endAt = exam.endAt,
                    recoveredAt = recoveredAt,
                ),
            )
        }
    }

    suspend fun setCurrentQuestion(examId: String, index: Int) {
        val exam = dao.getExam(examId) ?: return
        dao.updateCurrentQuestion(examId, index.coerceIn(0, (exam.totalQuestions - 1).coerceAtLeast(0)))
    }

    suspend fun selectAnswer(examId: String, questionId: String, optionId: String) =
        answerMutex.withLock {
            val now = System.currentTimeMillis()
            val current = dao.getAnswer(examId, questionId)
            dao.upsertAnswer(
                (current ?: AnswerEntity(examId, questionId, lastUpdatedAt = now)).copy(
                    selectedOptionId = optionId,
                    isVisited = true,
                    answeredAt = now,
                    lastUpdatedAt = now,
                ),
            )
        }

    suspend fun clearAnswer(examId: String, questionId: String) = answerMutex.withLock {
        val now = System.currentTimeMillis()
        val current = dao.getAnswer(examId, questionId)
            ?: AnswerEntity(examId, questionId, lastUpdatedAt = now)
        dao.upsertAnswer(current.copy(selectedOptionId = null, answeredAt = null, isVisited = true, lastUpdatedAt = now))
    }

    suspend fun markVisited(examId: String, questionId: String) = answerMutex.withLock {
        val now = System.currentTimeMillis()
        val current = dao.getAnswer(examId, questionId)
            ?: AnswerEntity(examId, questionId, lastUpdatedAt = now)
        if (!current.isVisited) dao.upsertAnswer(current.copy(isVisited = true, lastUpdatedAt = now))
    }

    suspend fun toggleMarked(examId: String, questionId: String) = answerMutex.withLock {
        val now = System.currentTimeMillis()
        val current = dao.getAnswer(examId, questionId)
            ?: AnswerEntity(examId, questionId, lastUpdatedAt = now)
        dao.upsertAnswer(
            current.copy(
                isMarkedForReview = !current.isMarkedForReview,
                isVisited = true,
                lastUpdatedAt = now,
            ),
        )
    }

    suspend fun submit(examId: String) {
        val exam = dao.getExam(examId) ?: return
        dao.updateSubmissionState(examId, ExamStatus.SUBMITTED, System.currentTimeMillis())
    }

    suspend fun importAnswerKey(examId: String, keyByNumber: Map<Int, String>) {
        val questions = dao.getQuestions(examId)
        val keys = questions.mapNotNull { question ->
            keyByNumber[question.question.questionNumber]?.let { label ->
                AnswerKeyEntity(examId, question.question.questionId, label)
            }
        }
        dao.upsertAnswerKeys(keys)
    }
}

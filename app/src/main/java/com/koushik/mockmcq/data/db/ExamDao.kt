package com.koushik.mockmcq.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Query(
        """
        SELECT e.*,
               COUNT(CASE WHEN a.selectedOptionId IS NOT NULL THEN 1 END) AS attemptedCount,
               COUNT(CASE WHEN a.isMarkedForReview = 1 THEN 1 END) AS markedCount
        FROM exams e
        LEFT JOIN answers a ON e.examId = a.examId
        GROUP BY e.examId
        ORDER BY e.importedAt DESC
        """,
    )
    fun observeExamSummaries(): Flow<List<ExamSummaryDb>>

    @Query("SELECT * FROM exams WHERE examId = :examId LIMIT 1")
    fun observeExam(examId: String): Flow<ExamEntity?>

    @Query("SELECT * FROM exams WHERE examId = :examId LIMIT 1")
    suspend fun getExam(examId: String): ExamEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExam(exam: ExamEntity)

    @Query("SELECT COUNT(*) FROM questions WHERE examId = :examId")
    suspend fun countQuestions(examId: String): Int

    @Query(
        """
        UPDATE exams
        SET status = :status, startedAt = :startedAt, endAt = :endAt
        WHERE examId = :examId
        """,
    )
    suspend fun updateExamStartState(
        examId: String,
        status: String,
        startedAt: Long,
        endAt: Long?,
    )

    @Query("UPDATE exams SET currentQuestionIndex = :index WHERE examId = :examId")
    suspend fun updateCurrentQuestion(examId: String, index: Int)

    @Query(
        """
        UPDATE exams
        SET status = :status, submittedAt = :submittedAt
        WHERE examId = :examId
        """,
    )
    suspend fun updateSubmissionState(examId: String, status: String, submittedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOptions(options: List<OptionEntity>)

    @Transaction
    @Query("SELECT * FROM questions WHERE examId = :examId ORDER BY questionNumber")
    fun observeQuestions(examId: String): Flow<List<QuestionWithOptions>>

    @Transaction
    @Query("SELECT * FROM questions WHERE examId = :examId ORDER BY questionNumber")
    suspend fun getQuestions(examId: String): List<QuestionWithOptions>

    @Query("SELECT * FROM answers WHERE examId = :examId")
    fun observeAnswers(examId: String): Flow<List<AnswerEntity>>

    @Query("SELECT * FROM answers WHERE examId = :examId AND questionId = :questionId LIMIT 1")
    suspend fun getAnswer(examId: String, questionId: String): AnswerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswer(answer: AnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnswerKeys(keys: List<AnswerKeyEntity>)

    @Query("SELECT * FROM answer_keys WHERE examId = :examId")
    fun observeAnswerKeys(examId: String): Flow<List<AnswerKeyEntity>>
}

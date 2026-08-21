package com.koushik.mockmcq.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

object ExamStatus {
    const val NOT_STARTED = "NOT_STARTED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val PAUSED = "PAUSED"
    const val SUBMITTED = "SUBMITTED"
}

object ParsingConfidence {
    const val HIGH = "HIGH"
    const val MEDIUM = "MEDIUM"
    const val LOW = "LOW"
}

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey val examId: String,
    val examName: String,
    val pdfName: String,
    val pdfUri: String,
    val importedAt: Long,
    val totalQuestions: Int,
    val currentQuestionIndex: Int = 0,
    val status: String = ExamStatus.NOT_STARTED,
    val startedAt: Long? = null,
    val endAt: Long? = null,
    val remainingTimeMillis: Long? = null,
    val submittedAt: Long? = null,
    val durationMinutes: Int? = 180,
    val correctMarks: Double = 2.0,
    val incorrectMarks: Double = -0.66,
    val negativeMarkingEnabled: Boolean = true,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["examId"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("examId"), Index(value = ["examId", "questionNumber"], unique = true)],
)
data class QuestionEntity(
    @PrimaryKey val questionId: String,
    val examId: String,
    val questionNumber: Int,
    val questionText: String,
    val questionType: String = "MCQ",
    val pageNumber: Int,
    val parsingConfidence: String,
)

@Entity(
    tableName = "options",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["questionId"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("questionId"), Index(value = ["questionId", "displayOrder"], unique = true)],
)
data class OptionEntity(
    @PrimaryKey val optionId: String,
    val questionId: String,
    val optionLabel: String,
    val optionText: String,
    val displayOrder: Int,
)

@Entity(
    tableName = "answers",
    primaryKeys = ["examId", "questionId"],
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["examId"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["questionId"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("examId"), Index("questionId")],
)
data class AnswerEntity(
    val examId: String,
    val questionId: String,
    val selectedOptionId: String? = null,
    val isVisited: Boolean = false,
    val isMarkedForReview: Boolean = false,
    val answeredAt: Long? = null,
    val lastUpdatedAt: Long,
)

@Entity(
    tableName = "answer_keys",
    primaryKeys = ["examId", "questionId"],
    foreignKeys = [
        ForeignKey(
            entity = ExamEntity::class,
            parentColumns = ["examId"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["questionId"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("examId"), Index("questionId")],
)
data class AnswerKeyEntity(
    val examId: String,
    val questionId: String,
    val correctOptionLabel: String,
    val explanation: String? = null,
)

data class QuestionWithOptions(
    @Embedded val question: QuestionEntity,
    @Relation(parentColumn = "questionId", entityColumn = "questionId")
    val options: List<OptionEntity>,
)

data class ExamSummaryDb(
    @Embedded val exam: ExamEntity,
    val attemptedCount: Int,
    val markedCount: Int,
)

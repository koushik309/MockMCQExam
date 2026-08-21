package com.koushik.mockmcq.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExamEntity::class,
        QuestionEntity::class,
        OptionEntity::class,
        AnswerEntity::class,
        AnswerKeyEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun examDao(): ExamDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exams ADD COLUMN remainingTimeMillis INTEGER")
                database.execSQL(
                    """
                    UPDATE exams
                    SET remainingTimeMillis = CASE
                        WHEN endAt IS NOT NULL THEN MAX(
                            0,
                            endAt - (CAST(strftime('%s', 'now') AS INTEGER) * 1000)
                        )
                        WHEN durationMinutes IS NOT NULL THEN durationMinutes * 60000
                        ELSE NULL
                    END
                    """.trimIndent(),
                )
                database.execSQL(
                    "UPDATE exams SET status = 'PAUSED', endAt = NULL WHERE status = 'IN_PROGRESS'",
                )
            }
        }
    }
}

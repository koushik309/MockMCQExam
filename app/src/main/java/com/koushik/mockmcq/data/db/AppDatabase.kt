package com.koushik.mockmcq.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExamEntity::class,
        QuestionEntity::class,
        OptionEntity::class,
        AnswerEntity::class,
        AnswerKeyEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun examDao(): ExamDao
}

package com.koushik.mockmcq

import android.app.Application
import androidx.room.Room
import com.koushik.mockmcq.data.db.AppDatabase
import com.koushik.mockmcq.data.repository.ExamRepository

class MockMcqApplication : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "mock-mcq-exam.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    val repository: ExamRepository by lazy { ExamRepository(database) }
}

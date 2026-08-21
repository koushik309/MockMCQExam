package com.koushik.mockmcq

import android.app.Application
import androidx.room.Room
import com.koushik.mockmcq.data.db.AppDatabase
import com.koushik.mockmcq.data.repository.ExamRepository

class MockMcqApplication : Application() {
    private val diagnostics by lazy { getSharedPreferences("diagnostics", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                diagnostics.edit()
                    .putString("last_crash", error.stackTraceToString().take(12_000))
                    .commit()
            }
            previousHandler?.uncaughtException(thread, error)
        }
    }

    fun consumeLastCrashReport(): String? {
        val report = diagnostics.getString("last_crash", null)
        if (report != null) diagnostics.edit().remove("last_crash").commit()
        return report
    }

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "mock-mcq-exam.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }
    val repository: ExamRepository by lazy { ExamRepository(database) }
}

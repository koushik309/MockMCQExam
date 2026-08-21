package com.koushik.mockmcq.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamClockTest {
    @Test
    fun pausesAndResumesFromExactRemainingTime() {
        val remaining = ExamClock.remainingMillis(endAt = 1_600_000L, now = 1_000_000L)

        assertEquals(600_000L, remaining)
        assertEquals(2_600_000L, ExamClock.resumedEndAt(remaining, now = 2_000_000L))
    }

    @Test
    fun neverStoresNegativeRemainingTime() {
        assertEquals(0L, ExamClock.remainingMillis(endAt = 500L, now = 1_000L))
    }

    @Test
    fun supportsUntimedExams() {
        assertNull(ExamClock.initialRemainingMillis(null))
        assertNull(ExamClock.resumedEndAt(null, now = 1_000L))
    }

    @Test
    fun interruptedExamUsesLastCheckpointInsteadOfCountingOfflineTime() {
        assertEquals(
            600_000L,
            ExamClock.recoveredRemainingMillis(
                checkpointMillis = 600_000L,
                endAt = 1_600_000L,
                recoveredAt = 5_000_000L,
            ),
        )
    }

    @Test
    fun interruptedLegacyExamFallsBackToItsDeadline() {
        assertEquals(
            400_000L,
            ExamClock.recoveredRemainingMillis(
                checkpointMillis = null,
                endAt = 1_400_000L,
                recoveredAt = 1_000_000L,
            ),
        )
    }
}

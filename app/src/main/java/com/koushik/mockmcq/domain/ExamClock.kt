package com.koushik.mockmcq.domain

object ExamClock {
    fun initialRemainingMillis(durationMinutes: Int?): Long? =
        durationMinutes?.coerceAtLeast(0)?.times(60_000L)

    fun remainingMillis(endAt: Long?, now: Long): Long? =
        endAt?.minus(now)?.coerceAtLeast(0L)

    fun resumedEndAt(remainingMillis: Long?, now: Long): Long? =
        remainingMillis?.coerceAtLeast(0L)?.let(now::plus)
}

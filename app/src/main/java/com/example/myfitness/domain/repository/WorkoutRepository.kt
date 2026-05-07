package com.example.myfitness.domain.repository

import com.example.myfitness.domain.model.DailyCheckIn
import java.time.LocalDate

interface WorkoutRepository {
    suspend fun getSessions(): List<DailyCheckIn>

    suspend fun saveSession(checkIn: DailyCheckIn)

    suspend fun getSessionByDate(date: LocalDate): DailyCheckIn?

    suspend fun importFromMarkdown(content: String, date: LocalDate)
}

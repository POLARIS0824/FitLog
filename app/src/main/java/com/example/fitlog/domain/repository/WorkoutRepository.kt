package com.example.fitlog.domain.repository

import com.example.fitlog.domain.model.WorkOut
import java.time.LocalDate

interface WorkoutRepository {
    suspend fun getSessions(): List<WorkOut>

    suspend fun saveSession(workOut: WorkOut)

    suspend fun getSessionByDate(date: LocalDate): WorkOut?

    suspend fun importFromMarkdown(content: String, date: LocalDate, sourceFileName: String? = null)
}

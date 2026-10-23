package com.example.fitlog.data.record

import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.ReviewedSession
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.source.SourceKey
import java.time.LocalDate

interface TrainingRecords {
    fun observeRecords(): kotlinx.coroutines.flow.Flow<List<TrainingRecordDetails>>
    suspend fun read(id: String): TrainingRecordDetails?
    suspend fun saveNative(id: String, date: LocalDate, sessions: List<ReviewedSession>, kind: RecordKind)
    suspend fun delete(id: String)
    suspend fun deleteConfirmation(key: SourceKey)
}

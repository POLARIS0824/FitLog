@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.fitlog.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.data.record.TrainingRecords
import com.example.fitlog.model.record.ConfirmedSession
import com.example.fitlog.model.record.ConfirmedSessionRecord
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.record.ReviewedSession
import com.example.fitlog.model.record.TrainingRecord
import com.example.fitlog.model.record.TrainingRecordDetails
import com.example.fitlog.model.source.SourceKey
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class MainDispatcherRule : TestWatcher() {
    val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    fun <T : ViewModel> own(vm: T): T = vm.also { models.put(UUID.randomUUID().toString(), it) }
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { models.clear(); Dispatchers.resetMain() }
}

internal class FakeRecords : TrainingRecords {
    val rows = MutableStateFlow<List<TrainingRecordDetails>>(emptyList())
    var readsFail = false
    var writesFail = false
    var saveGate: CompletableDeferred<Unit>? = null
    val savedNotes = mutableListOf<String?>()
    var readers = 0
    override fun observeRecords() = flow {
        readers++
        try { emitAll(rows) } finally { readers-- }
    }
    override suspend fun read(id: String): TrainingRecordDetails? {
        if (readsFail) error("storage unavailable")
        return rows.value.firstOrNull { it.diary.id == id }
    }
    override suspend fun saveNative(id: String, date: LocalDate, sessions: List<ReviewedSession>, kind: RecordKind) {
        if (writesFail) error("storage unavailable")
        saveGate?.await()
        savedNotes += sessions.single().notes
        val row = TrainingRecordDetails(TrainingRecord(id, null, null, date.toString(), null, null, null,
            1, false, RecordOrigin.NATIVE, kind), sessions.mapIndexed { index, session ->
            ConfirmedSessionRecord(ConfirmedSession("session-$index", id, index, session.notes, null), emptyList())
        })
        rows.value = rows.value.filterNot { it.diary.id == id } + row
    }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.diary.id == id } }
    override suspend fun deleteConfirmation(key: SourceKey) = Unit
}

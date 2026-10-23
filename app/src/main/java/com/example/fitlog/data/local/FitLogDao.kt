package com.example.fitlog.data.local

import androidx.room.*
import com.example.fitlog.data.analysis.ParseRunRow
import com.example.fitlog.data.record.ConfirmedExerciseRow
import com.example.fitlog.data.record.ConfirmedSessionRow
import com.example.fitlog.data.record.ConfirmedSetRow
import com.example.fitlog.data.record.TrainingRecordGraph
import com.example.fitlog.data.record.TrainingRecordRow
import kotlinx.coroutines.flow.Flow

@Dao
internal interface FitLogDao {
    @Query("""SELECT * FROM parse_run AS p WHERE p.vaultId = :vaultId AND
        (p.rowid = (SELECT r.rowid FROM parse_run AS r WHERE r.vaultId = p.vaultId AND r.relPath = p.relPath
            ORDER BY r.startedAt DESC, r.rowid DESC LIMIT 1)
        OR p.rowid = (SELECT r.rowid FROM parse_run AS r WHERE r.vaultId = p.vaultId AND r.relPath = p.relPath
            AND r.status = 'SUCCEEDED' ORDER BY r.startedAt DESC, r.rowid DESC LIMIT 1))
        ORDER BY p.startedAt, p.rowid""")
    fun observeVaultHeads(vaultId: String): Flow<List<ParseRunRow>>
    @Query("SELECT * FROM confirmed_diary WHERE vaultId = :vaultId")
    fun observeConfirmedRows(vaultId: String): Flow<List<TrainingRecordRow>>
    @Query("SELECT id FROM parse_run WHERE vaultId = :vaultId AND relPath = :relPath AND status = 'SUCCEEDED' ORDER BY startedAt DESC, rowid DESC LIMIT 1")
    suspend fun latestCandidateId(vaultId: String, relPath: String): String?
    @Insert suspend fun insertRun(row: ParseRunRow)
    @Query("SELECT * FROM parse_run WHERE id = :id") suspend fun run(id: String): ParseRunRow?
    @Query("SELECT * FROM parse_run WHERE vaultId = :vaultId AND relPath = :relPath ORDER BY startedAt, rowid")
    suspend fun runs(vaultId: String, relPath: String): List<ParseRunRow>
    @Query("SELECT * FROM parse_run WHERE vaultId = :vaultId AND relPath = :relPath ORDER BY startedAt, rowid")
    fun observeRuns(vaultId: String, relPath: String): Flow<List<ParseRunRow>>

    @Transaction
    @Query("SELECT * FROM confirmed_diary WHERE vaultId = :vaultId AND relPath = :relPath")
    suspend fun confirmed(vaultId: String, relPath: String): TrainingRecordGraph?
    @Transaction
    @Query("SELECT * FROM confirmed_diary WHERE vaultId = :vaultId AND relPath = :relPath")
    fun observeConfirmed(vaultId: String, relPath: String): Flow<TrainingRecordGraph?>
    @Transaction
    @Query("SELECT * FROM confirmed_diary ORDER BY date DESC, confirmedAt DESC")
    fun observeRecords(): Flow<List<TrainingRecordGraph>>
    @Transaction
    @Query("SELECT * FROM confirmed_diary WHERE id = :id")
    suspend fun record(id: String): TrainingRecordGraph?
    @Query("DELETE FROM confirmed_diary WHERE id = :id") suspend fun deleteRecord(id: String)
    @Insert suspend fun insertDiary(row: TrainingRecordRow)
    @Update suspend fun updateDiary(row: TrainingRecordRow): Int
    @Insert suspend fun insertSession(row: ConfirmedSessionRow)
    @Insert suspend fun insertExercise(row: ConfirmedExerciseRow)
    @Insert suspend fun insertSets(rows: List<ConfirmedSetRow>)
    @Query("DELETE FROM confirmed_session WHERE diaryId = :diaryId") suspend fun deleteSessions(diaryId: String)
}

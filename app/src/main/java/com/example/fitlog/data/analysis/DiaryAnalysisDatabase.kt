package com.example.fitlog.data.analysis

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

// Terminal parse attempts and current user confirmations are stored independently.

@Serializable
enum class RecordOrigin { NATIVE, MARKDOWN }
@Serializable
enum class RecordKind { TRAINING, REST }

enum class ParseRunStatus { SUCCEEDED, FAILED }

@Entity(tableName = "parse_run", indices = [Index(value = ["vaultId", "relPath", "contentHash", "hashVersion", "extractorVersion", "status"])])
@Serializable
data class ParseRunRow(
    @PrimaryKey val id: String,
    val vaultId: String,
    val relPath: String,
    val contentHash: String,
    val hashVersion: Int,
    val extractorVersion: String,
    val status: ParseRunStatus,
    val startedAt: Long,
    val finishedAt: Long,
    val failureCode: String? = null,
    val rawModelJson: String? = null,
    val candidateJson: String? = null,
) {
    fun parseKey() = DiaryParseKey(SourceKey(vaultId, relPath), contentHash, hashVersion, extractorVersion)
}

@Entity(tableName = "confirmed_diary",
    indices = [Index(value = ["vaultId", "relPath"], unique = true), Index("parseRunId"), Index(value = ["vaultId", "date"])])
@Serializable
data class TrainingRecord(
    @PrimaryKey val id: String,
    val vaultId: String?,
    val relPath: String?,
    val date: String,
    val contentHash: String?,
    val hashVersion: Int?,
    val parseRunId: String?,
    val confirmedAt: Long,
    val acceptedPartialResult: Boolean,
    val origin: RecordOrigin = RecordOrigin.MARKDOWN,
    val kind: RecordKind = RecordKind.TRAINING,
) {
    init {
        require(java.util.UUID.fromString(id).toString() == id)
        if (origin == RecordOrigin.NATIVE) {
            require(vaultId == null && relPath == null && contentHash == null && hashVersion == null && parseRunId == null)
        } else {
            SourceKey(requireNotNull(vaultId), requireNotNull(relPath))
            DiaryContentVersion(requireNotNull(contentHash), requireNotNull(hashVersion))
        }
    }
}

@Entity(tableName = "confirmed_session", indices = [Index(value = ["diaryId", "position"], unique = true)],
    foreignKeys = [ForeignKey(entity = TrainingRecord::class, parentColumns = ["id"], childColumns = ["diaryId"], onDelete = ForeignKey.CASCADE)])
@Serializable
data class ConfirmedSessionRow(@PrimaryKey val id: String, val diaryId: String, val position: Int,
    val notes: String?, val sourcePath: String?)

@Entity(tableName = "confirmed_exercise", indices = [Index(value = ["sessionId", "position"], unique = true)],
    foreignKeys = [ForeignKey(entity = ConfirmedSessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
@Serializable
data class ConfirmedExerciseRow(
    @PrimaryKey val id: String, val sessionId: String, val position: Int,
    val rawName: String, val notes: String?, val sourcePath: String?,
    @Embedded(prefix = "evidence_") val evidence: EvidenceQuote?,
)

@Entity(tableName = "confirmed_set", indices = [Index(value = ["exerciseId", "position"], unique = true)],
    foreignKeys = [ForeignKey(entity = ConfirmedExerciseRow::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)])
@Serializable
data class ConfirmedSetRow(
    @PrimaryKey val id: String, val exerciseId: String, val position: Int,
    val weight: Double?, val unit: WeightUnit?, val basis: WeightBasis?, val reps: Int?, val weightKg: Double?,
    val groupIndex: Int?, val setInGroup: Int?, val userEdited: Boolean,
)

@Serializable
data class ConfirmedExerciseRecord(@Embedded val exercise: ConfirmedExerciseRow,
    @Relation(parentColumn = "id", entityColumn = "exerciseId") val sets: List<ConfirmedSetRow>)

@Serializable
data class ConfirmedSessionRecord(@Embedded val session: ConfirmedSessionRow,
    @Relation(parentColumn = "id", entityColumn = "sessionId", entity = ConfirmedExerciseRow::class)
    val exercises: List<ConfirmedExerciseRecord>)

@Serializable
data class TrainingRecordDetails(@Embedded val diary: TrainingRecord,
    @Relation(parentColumn = "id", entityColumn = "diaryId", entity = ConfirmedSessionRow::class)
    val sessions: List<ConfirmedSessionRecord>) {
    internal fun ordered() = copy(sessions = sessions.sortedBy { it.session.position }.map { session ->
        session.copy(exercises = session.exercises.sortedBy { it.exercise.position }.map { exercise ->
            exercise.copy(sets = exercise.sets.sortedBy { it.position })
        })
    })
}

class AnalysisConverters {
    @TypeConverter fun origin(value: String): RecordOrigin = RecordOrigin.valueOf(value)
    @TypeConverter fun origin(value: RecordOrigin): String = value.name
    @TypeConverter fun kind(value: String): RecordKind = RecordKind.valueOf(value)
    @TypeConverter fun kind(value: RecordKind): String = value.name
    @TypeConverter fun status(value: String): ParseRunStatus = ParseRunStatus.valueOf(value)
    @TypeConverter fun status(value: ParseRunStatus): String = value.name
    @TypeConverter fun unit(value: String?): WeightUnit? = value?.let(WeightUnit::valueOf)
    @TypeConverter fun unit(value: WeightUnit?): String? = value?.name
    @TypeConverter fun basis(value: String?): WeightBasis? = value?.let(WeightBasis::valueOf)
    @TypeConverter fun basis(value: WeightBasis?): String? = value?.name
}

@Dao
interface DiaryAnalysisDao {
    @Query("""SELECT * FROM parse_run AS p WHERE p.vaultId = :vaultId AND
        (p.rowid = (SELECT r.rowid FROM parse_run AS r WHERE r.vaultId = p.vaultId AND r.relPath = p.relPath
            ORDER BY r.startedAt DESC, r.rowid DESC LIMIT 1)
        OR p.rowid = (SELECT r.rowid FROM parse_run AS r WHERE r.vaultId = p.vaultId AND r.relPath = p.relPath
            AND r.status = 'SUCCEEDED' ORDER BY r.startedAt DESC, r.rowid DESC LIMIT 1))
        ORDER BY p.startedAt, p.rowid""")
    fun observeVaultHeads(vaultId: String): Flow<List<ParseRunRow>>
    @Query("SELECT * FROM confirmed_diary WHERE vaultId = :vaultId")
    fun observeConfirmedRows(vaultId: String): Flow<List<TrainingRecord>>
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
    suspend fun confirmed(vaultId: String, relPath: String): TrainingRecordDetails?
    @Transaction
    @Query("SELECT * FROM confirmed_diary WHERE vaultId = :vaultId AND relPath = :relPath")
    fun observeConfirmed(vaultId: String, relPath: String): Flow<TrainingRecordDetails?>
    @Transaction
    @Query("SELECT * FROM confirmed_diary ORDER BY date DESC, confirmedAt DESC")
    fun observeRecords(): Flow<List<TrainingRecordDetails>>
    @Transaction
    @Query("SELECT * FROM confirmed_diary WHERE id = :id")
    suspend fun record(id: String): TrainingRecordDetails?
    @Query("DELETE FROM confirmed_diary WHERE id = :id") suspend fun deleteRecord(id: String)
    @Insert suspend fun insertDiary(row: TrainingRecord)
    @Update suspend fun updateDiary(row: TrainingRecord): Int
    @Insert suspend fun insertSession(row: ConfirmedSessionRow)
    @Insert suspend fun insertExercise(row: ConfirmedExerciseRow)
    @Insert suspend fun insertSets(rows: List<ConfirmedSetRow>)
    @Query("DELETE FROM confirmed_session WHERE diaryId = :diaryId") suspend fun deleteSessions(diaryId: String)
}

@Database(entities = [ParseRunRow::class, TrainingRecord::class, ConfirmedSessionRow::class,
    ConfirmedExerciseRow::class, ConfirmedSetRow::class], version = 3, exportSchema = true)
@TypeConverters(AnalysisConverters::class)
abstract class DiaryAnalysisDatabase : RoomDatabase() {
    abstract fun analysis(): DiaryAnalysisDao
    companion object {
        fun open(context: Context): DiaryAnalysisDatabase =
            Room.databaseBuilder(context.applicationContext, DiaryAnalysisDatabase::class.java, "training-records.db").build()
    }
}

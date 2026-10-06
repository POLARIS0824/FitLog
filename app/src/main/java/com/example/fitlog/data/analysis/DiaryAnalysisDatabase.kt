package com.example.fitlog.data.analysis

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

// Terminal parse attempts and current user confirmations are stored independently.

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
    indices = [Index(value = ["vaultId", "relPath"], unique = true), Index("parseRunId"), Index(value = ["vaultId", "date"])],
    foreignKeys = [ForeignKey(entity = ParseRunRow::class, parentColumns = ["id"], childColumns = ["parseRunId"], onDelete = ForeignKey.RESTRICT)])
@Serializable
data class ConfirmedDiaryRow(
    @PrimaryKey val id: String,
    val vaultId: String,
    val relPath: String,
    val date: String,
    val contentHash: String,
    val hashVersion: Int,
    val parseRunId: String,
    val confirmedAt: Long,
    val acceptedPartialResult: Boolean,
)

@Entity(tableName = "confirmed_session", indices = [Index(value = ["diaryId", "position"], unique = true)],
    foreignKeys = [ForeignKey(entity = ConfirmedDiaryRow::class, parentColumns = ["id"], childColumns = ["diaryId"], onDelete = ForeignKey.CASCADE)])
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
data class ConfirmedDiaryRecord(@Embedded val diary: ConfirmedDiaryRow,
    @Relation(parentColumn = "id", entityColumn = "diaryId", entity = ConfirmedSessionRow::class)
    val sessions: List<ConfirmedSessionRecord>) {
    internal fun ordered() = copy(sessions = sessions.sortedBy { it.session.position }.map { session ->
        session.copy(exercises = session.exercises.sortedBy { it.exercise.position }.map { exercise ->
            exercise.copy(sets = exercise.sets.sortedBy { it.position })
        })
    })
}

class AnalysisConverters {
    @TypeConverter fun status(value: String): ParseRunStatus = ParseRunStatus.valueOf(value)
    @TypeConverter fun status(value: ParseRunStatus): String = value.name
    @TypeConverter fun unit(value: String?): WeightUnit? = value?.let(WeightUnit::valueOf)
    @TypeConverter fun unit(value: WeightUnit?): String? = value?.name
    @TypeConverter fun basis(value: String?): WeightBasis? = value?.let(WeightBasis::valueOf)
    @TypeConverter fun basis(value: WeightBasis?): String? = value?.name
}

@Dao
interface DiaryAnalysisDao {
    @Insert suspend fun insertRun(row: ParseRunRow)
    @Query("SELECT * FROM parse_run WHERE id = :id") suspend fun run(id: String): ParseRunRow?
    @Query("SELECT * FROM parse_run WHERE vaultId = :vaultId AND relPath = :relPath ORDER BY startedAt, rowid")
    suspend fun runs(vaultId: String, relPath: String): List<ParseRunRow>
    @Query("SELECT * FROM parse_run WHERE vaultId = :vaultId AND relPath = :relPath ORDER BY startedAt, rowid")
    fun observeRuns(vaultId: String, relPath: String): Flow<List<ParseRunRow>>

    @Transaction
    @Query("SELECT * FROM confirmed_diary WHERE vaultId = :vaultId AND relPath = :relPath")
    suspend fun confirmed(vaultId: String, relPath: String): ConfirmedDiaryRecord?
    @Transaction
    @Query("SELECT * FROM confirmed_diary WHERE vaultId = :vaultId AND relPath = :relPath")
    fun observeConfirmed(vaultId: String, relPath: String): Flow<ConfirmedDiaryRecord?>
    @Insert suspend fun insertDiary(row: ConfirmedDiaryRow)
    @Update suspend fun updateDiary(row: ConfirmedDiaryRow): Int
    @Insert suspend fun insertSession(row: ConfirmedSessionRow)
    @Insert suspend fun insertExercise(row: ConfirmedExerciseRow)
    @Insert suspend fun insertSets(rows: List<ConfirmedSetRow>)
    @Query("DELETE FROM confirmed_session WHERE diaryId = :diaryId") suspend fun deleteSessions(diaryId: String)
}

@Database(entities = [ParseRunRow::class, ConfirmedDiaryRow::class, ConfirmedSessionRow::class,
    ConfirmedExerciseRow::class, ConfirmedSetRow::class], version = 2, exportSchema = true)
@TypeConverters(AnalysisConverters::class)
abstract class DiaryAnalysisDatabase : RoomDatabase() {
    abstract fun analysis(): DiaryAnalysisDao
    companion object {
        @Volatile private var instance: DiaryAnalysisDatabase? = null
        fun get(context: Context): DiaryAnalysisDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, DiaryAnalysisDatabase::class.java,
                "diary-analysis.db").build().also { instance = it }
        }
    }
}

package com.example.fitlog.data.record

import androidx.room.*
import com.example.fitlog.model.analysis.DiaryContentVersion
import com.example.fitlog.model.analysis.EvidenceQuote
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit
import com.example.fitlog.model.source.SourceKey

@Entity(tableName = "confirmed_diary",
    indices = [Index(value = ["vaultId", "relPath"], unique = true), Index("parseRunId"), Index(value = ["vaultId", "date"])])
internal data class TrainingRecordRow(
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
    foreignKeys = [ForeignKey(entity = TrainingRecordRow::class, parentColumns = ["id"], childColumns = ["diaryId"], onDelete = ForeignKey.CASCADE)])
internal data class ConfirmedSessionRow(@PrimaryKey val id: String, val diaryId: String, val position: Int,
    val notes: String?, val sourcePath: String?)

@Entity(tableName = "confirmed_exercise", indices = [Index(value = ["sessionId", "position"], unique = true)],
    foreignKeys = [ForeignKey(entity = ConfirmedSessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)])
internal data class ConfirmedExerciseRow(
    @PrimaryKey val id: String, val sessionId: String, val position: Int,
    val rawName: String, val notes: String?, val sourcePath: String?,
    @Embedded(prefix = "evidence_") val evidence: EvidenceQuote?,
)

@Entity(tableName = "confirmed_set", indices = [Index(value = ["exerciseId", "position"], unique = true)],
    foreignKeys = [ForeignKey(entity = ConfirmedExerciseRow::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)])
internal data class ConfirmedSetRow(
    @PrimaryKey val id: String, val exerciseId: String, val position: Int,
    val weight: Double?, val unit: WeightUnit?, val basis: WeightBasis?, val reps: Int?, val weightKg: Double?,
    val groupIndex: Int?, val setInGroup: Int?, val userEdited: Boolean,
)

internal data class ConfirmedExerciseGraph(@Embedded val exercise: ConfirmedExerciseRow,
    @Relation(parentColumn = "id", entityColumn = "exerciseId") val sets: List<ConfirmedSetRow>)

internal data class ConfirmedSessionGraph(@Embedded val session: ConfirmedSessionRow,
    @Relation(parentColumn = "id", entityColumn = "sessionId", entity = ConfirmedExerciseRow::class)
    val exercises: List<ConfirmedExerciseGraph>)

internal data class TrainingRecordGraph(@Embedded val diary: TrainingRecordRow,
    @Relation(parentColumn = "id", entityColumn = "diaryId", entity = ConfirmedSessionRow::class)
    val sessions: List<ConfirmedSessionGraph>)

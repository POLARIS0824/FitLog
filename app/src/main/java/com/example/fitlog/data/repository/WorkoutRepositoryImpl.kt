package com.example.fitlog.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.fitlog.data.file.MarkdownParser
import com.example.fitlog.data.local.dao.ExerciseLogDao
import com.example.fitlog.data.local.dao.SetLogDao
import com.example.fitlog.data.local.dao.WorkoutDao
import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.domain.model.WorkOut
import com.example.fitlog.domain.model.ExerciseLog
import com.example.fitlog.domain.model.SetLog
import com.example.fitlog.domain.repository.WorkoutRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * [WorkoutRepository] 的 Room 实现。
 * 使用关系型表结构（workouts / exercise_logs / set_logs）存储训练记录。
 */
class WorkoutRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseLogDao: ExerciseLogDao,
    private val setLogDao: SetLogDao,
) : WorkoutRepository {

    /**
     * 获取所有训练记录，按日期降序排列。
     *
     * @return [WorkOut] 列表
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getSessions(): List<WorkOut> {
        return workoutDao.getAll().map { it.toDomain() }
    }

    /**
     * 保存一条完整的训练记录（含动作和组）。
     *
     * @param checkIn 待保存的训练记录
     */
    override suspend fun saveSession(checkIn: WorkOut) {
        val workoutEntity = WorkoutEntity(
            id = checkIn.id,
            userId = checkIn.userId,
            date = checkIn.date.toString(),
            feelings = checkIn.feelings,
            sourceFileName = checkIn.sourceFileName,
            rawContent = "",
        )
        val workoutId = if (checkIn.id == 0L) {
            val id = workoutDao.insert(workoutEntity)
            if (id == -1L) throw IllegalStateException("Failed to insert workout")
            id
        } else {
            workoutDao.update(workoutEntity)
            checkIn.id
        }

        // 记录旧子记录 ID（更新模式下需要后续清理）
        val oldExerciseIds = if (checkIn.id != 0L) {
            exerciseLogDao.getByWorkoutId(workoutId).map { it.id }
        } else {
            emptyList()
        }

        val newExerciseIds = mutableListOf<Long>()
        checkIn.exercises.forEachIndexed { exerciseIndex, entry ->
            val exerciseEntity = ExerciseLogEntity(
                workoutId = workoutId,
                name = entry.name,
                sortOrder = exerciseIndex,
            )
            val exerciseId = exerciseLogDao.insert(exerciseEntity)
            if (exerciseId == -1L) {
                throw IllegalStateException("Failed to insert exercise log: ${entry.name}")
            }
            newExerciseIds.add(exerciseId)

            entry.sets.forEachIndexed { setIndex, set ->
                setLogDao.insert(
                    SetLogEntity(
                        exerciseLogId = exerciseId,
                        setNumber = setIndex + 1,
                        weightKg = set.weightKg,
                        reps = set.reps,
                    ),
                )
            }
        }

        // 全部插入成功后，删除旧子记录
        val exercisesToDelete = if (checkIn.id != 0L) {
            exerciseLogDao.getByWorkoutId(workoutId).filter { it.id in oldExerciseIds && it.id !in newExerciseIds }
        } else {
            emptyList()
        }
        exercisesToDelete.forEach { exercise ->
            val sets = setLogDao.getByExerciseLogId(exercise.id)
            sets.forEach { setLogDao.delete(it) }
            exerciseLogDao.delete(exercise)
        }
    }

    /**
     * 根据日期查询训练记录。
     *
     * @param date 训练日期
     * @return 匹配的训练记录列表（同一天可能有多条）
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getSessionByDate(date: LocalDate): WorkOut? {
        return workoutDao.getByDate(date.toString()).firstOrNull()?.toDomain()
    }

    /**
     * 从外部 Markdown 文本导入训练记录。
     * 当前仅保存原始文本到 [WorkoutEntity]，结构化解析（AI 解析器）待后续接入。
     *
     * @param content Markdown 格式的训练日志
     * @param date 训练日期
     */
    override suspend fun importFromMarkdown(content: String, date: LocalDate, sourceFileName: String?) {
        val preprocessed = MarkdownParser.preprocess(content)
        val entity = WorkoutEntity(
            id = 0L,
            userId = 0L,
            date = date.toString(),
            feelings = null,
            sourceFileName = sourceFileName,
            rawContent = preprocessed,
        )
        workoutDao.insert(entity)
    }

    /**
     * 将 [WorkoutEntity] 及其关联子表转换为 domain/model [WorkOut]。
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun WorkoutEntity.toDomain(): WorkOut {
        val exercises = exerciseLogDao.getByWorkoutId(id).map { exercise ->
            val sets = setLogDao.getByExerciseLogId(exercise.id).map { set ->
                SetLog(weightKg = set.weightKg, reps = set.reps)
            }
            ExerciseLog(name = exercise.name, sets = sets)
        }
        return WorkOut(
            id = id,
            userId = userId,
            date = LocalDate.parse(date),
            feelings = feelings,
            exercises = exercises,
            sourceFileName = sourceFileName,
        )
    }
}

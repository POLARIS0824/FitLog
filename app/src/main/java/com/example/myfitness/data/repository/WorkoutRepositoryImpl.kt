package com.example.myfitness.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.myfitness.data.file.MarkdownParser
import com.example.myfitness.data.local.dao.ExerciseLogDao
import com.example.myfitness.data.local.dao.SetLogDao
import com.example.myfitness.data.local.dao.WorkoutDao
import com.example.myfitness.data.local.entity.ExerciseLogEntity
import com.example.myfitness.data.local.entity.SetLogEntity
import com.example.myfitness.data.local.entity.WorkoutEntity
import com.example.myfitness.domain.model.DailyCheckIn
import com.example.myfitness.domain.model.ExerciseLog
import com.example.myfitness.domain.model.SetLog
import com.example.myfitness.domain.repository.WorkoutRepository
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
     * @return [DailyCheckIn] 列表
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getSessions(): List<DailyCheckIn> {
        return workoutDao.getAll().map { it.toDomain() }
    }

    /**
     * 保存一条完整的训练记录（含动作和组）。
     *
     * @param checkIn 待保存的训练记录
     */
    override suspend fun saveSession(checkIn: DailyCheckIn) {
        val workoutEntity = WorkoutEntity(
            id = checkIn.id,
            date = checkIn.date.toString(),
            sourceFileName = checkIn.sourceFileName,
            rawContent = "",
        )
        val workoutId = if (checkIn.id == 0L) {
            workoutDao.insert(workoutEntity)
        } else {
            workoutDao.update(workoutEntity)
            checkIn.id
        }

        if (workoutId == -1L) return

        // 先删除旧的子记录，再插入新的（简单替换策略）
        val existingExercises = exerciseLogDao.getByWorkoutId(workoutId)
        existingExercises.forEach { exercise ->
            val sets = setLogDao.getByExerciseLogId(exercise.id)
            sets.forEach { setLogDao.delete(it) }
            exerciseLogDao.delete(exercise)
        }

        checkIn.exercises.forEachIndexed { exerciseIndex, entry ->
            val exerciseEntity = ExerciseLogEntity(
                workoutId = workoutId,
                name = entry.name,
                sortOrder = exerciseIndex,
            )
            val exerciseId = exerciseLogDao.insert(exerciseEntity)
            if (exerciseId == -1L) return@forEachIndexed

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
    }

    /**
     * 根据日期查询训练记录。
     *
     * @param date 训练日期
     * @return 匹配的训练记录列表（同一天可能有多条）
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getSessionByDate(date: LocalDate): DailyCheckIn? {
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
            date = date.toString(),
            sourceFileName = sourceFileName,
            rawContent = preprocessed,
        )
        workoutDao.insert(entity)
    }

    /**
     * 将 [WorkoutEntity] 及其关联子表转换为 domain/model [DailyCheckIn]。
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun WorkoutEntity.toDomain(): DailyCheckIn {
        val exercises = exerciseLogDao.getByWorkoutId(id).map { exercise ->
            val sets = setLogDao.getByExerciseLogId(exercise.id).map { set ->
                SetLog(weightKg = set.weightKg, reps = set.reps)
            }
            ExerciseLog(name = exercise.name, sets = sets)
        }
        return DailyCheckIn(
            id = id,
            date = LocalDate.parse(date),
            exercises = exercises,
            sourceFileName = sourceFileName,
        )
    }
}

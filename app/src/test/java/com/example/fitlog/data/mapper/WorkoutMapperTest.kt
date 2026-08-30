package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.local.relation.ExerciseLogWithSets
import com.example.fitlog.data.local.relation.WorkoutWithExerciseLogs
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
import com.example.fitlog.model.SetType
import com.example.fitlog.model.Workout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * [WorkoutMapper] 的单元测试。
 * 验证训练日志 3 层结构（Workout -> ExerciseLog -> SetLog）在 Entity/Relation 与领域模型间的映射。
 */
class WorkoutMapperTest {

    private val date = LocalDate.of(2026, 5, 20)

    /**
     * 测试级联关系 [WorkoutWithExerciseLogs] 转领域模型：3 层结构完整保留且顺序一致。
     */
    @Test
    fun testRelationToModel_fullHierarchyPreserved() {
        val relation = WorkoutWithExerciseLogs(
            workout = WorkoutEntity(
                id = 10L,
                userId = 0L,
                date = date,
                feelings = null,
                sourceFileName = "2026-05-20.md",
                rawContent = null,
            ),
            exerciseLogs = listOf(
                ExerciseLogWithSets(
                    exerciseLog = ExerciseLogEntity(
                        id = 100L,
                        workoutId = 10L,
                        exerciseKey = "barbell-bench-press",
                        name = "杠铃卧推",
                        sortOrder = 1,
                    ),
                    sets = listOf(
                        SetLogEntity(id = 1L, exerciseLogId = 100L, setNumber = 1, weightKg = 80f, reps = 10),
                        SetLogEntity(id = 2L, exerciseLogId = 100L, setNumber = 2, weightKg = 85f, reps = 8),
                    ),
                ),
                ExerciseLogWithSets(
                    exerciseLog = ExerciseLogEntity(
                        id = 101L,
                        workoutId = 10L,
                        exerciseKey = null,
                        name = "徒手深蹲",
                        sortOrder = 2,
                    ),
                    sets = emptyList(),
                ),
            ),
        )

        val model = relation.toModel()

        assertEquals(10L, model.id)
        assertEquals(2, model.exercises.size)

        val first = model.exercises[0]
        assertEquals("杠铃卧推", first.name)
        assertEquals("barbell-bench-press", first.exerciseKey)
        assertEquals(2, first.sets.size)
        assertEquals(SetLog(weightKg = 80f, reps = 10), first.sets[0])
        assertEquals(SetLog(weightKg = 85f, reps = 8), first.sets[1])

        val second = model.exercises[1]
        assertEquals("徒手深蹲", second.name)
        assertNull(second.exerciseKey)
        assertTrue(second.sets.isEmpty())
    }

    /**
     * 测试乱序 relation 映射：动作按 sortOrder、组按 setNumber 显式排序输出。
     *
     * Room @Relation 不保证返回顺序，mapper 是排序契约的唯一执行点
     * （WorkoutRepository.update 按列表位置重写编号，读出乱序会永久错位）。
     */
    @Test
    fun testRelationToModel_sortsBySortOrderAndSetNumber() {
        val relation = WorkoutWithExerciseLogs(
            workout = WorkoutEntity(
                id = 20L,
                userId = 0L,
                date = date,
                feelings = null,
                sourceFileName = null,
                rawContent = null,
            ),
            exerciseLogs = listOf(
                ExerciseLogWithSets(
                    exerciseLog = ExerciseLogEntity(
                        id = 201L,
                        workoutId = 20L,
                        exerciseKey = null,
                        name = "第二个动作",
                        sortOrder = 1,
                    ),
                    sets = listOf(
                        SetLogEntity(id = 5L, exerciseLogId = 201L, setNumber = 2, weightKg = 60f, reps = 8),
                        SetLogEntity(id = 4L, exerciseLogId = 201L, setNumber = 1, weightKg = 50f, reps = 10),
                    ),
                ),
                ExerciseLogWithSets(
                    exerciseLog = ExerciseLogEntity(
                        id = 200L,
                        workoutId = 20L,
                        exerciseKey = null,
                        name = "第一个动作",
                        sortOrder = 0,
                    ),
                    sets = emptyList(),
                ),
            ),
        )

        val model = relation.toModel()

        assertEquals(listOf("第一个动作", "第二个动作"), model.exercises.map { it.name })
        val sets = model.exercises[1].sets
        assertEquals(listOf(50f to 10, 60f to 8), sets.map { it.weightKg to it.reps })
    }

    /**
     * 测试领域模型转 Entity：扁平字段完整透传。
     *
     * 注意：当前 [Workout.toEntity] 不携带 exercises（分层插入由 DAO 分别完成），
     * 这里只验证 Entity 本身的字段映射。
     */
    @Test
    fun testWorkoutToEntity_flatFieldsMapped() {
        val model = Workout(
            id = 42L,
            userId = 1L,
            date = date,
            exercises = listOf(
                ExerciseLog(
                    name = "杠铃卧推",
                    exerciseKey = "barbell-bench-press",
                    sets = listOf(SetLog(weightKg = 80f, reps = 10)),
                ),
            ),
            feelings = "累",
            sourceFileName = "2026-05-20.md",
            rawContent = "raw",
        )

        val entity = model.toEntity()

        assertEquals(42L, entity.id)
        assertEquals(1L, entity.userId)
        assertEquals(date, entity.date)
        assertEquals("累", entity.feelings)
        assertEquals("2026-05-20.md", entity.sourceFileName)
        assertEquals("raw", entity.rawContent)
    }

    // ── startedAt / endedAt ──

    /**
     * 测试训练开始/结束时间在 Relation → Model 与 Model → Entity 方向完整透传。
     */
    @Test
    fun testStartedAtEndedAt_passThroughBothDirections() {
        val entity = WorkoutEntity(
            id = 5L,
            date = date,
            feelings = null,
            startedAt = 1_777_000_000_000L,
            endedAt = 1_777_003_600_000L,
            sourceFileName = null,
            rawContent = null,
        )

        val relation = WorkoutWithExerciseLogs(workout = entity, exerciseLogs = emptyList())
        val cascaded = relation.toModel()
        assertEquals(1_777_000_000_000L, cascaded.startedAt)
        assertEquals(1_777_003_600_000L, cascaded.endedAt)

        val backToEntity = cascaded.toEntity()
        assertEquals(1_777_000_000_000L, backToEntity.startedAt)
        assertEquals(1_777_003_600_000L, backToEntity.endedAt)
    }

    // ── setType ──

    /**
     * 测试组类型枚举名双向映射："WARMUP" ↔ [SetType.WARMUP]。
     */
    @Test
    fun testSetType_mapsBothDirections() {
        val relation = ExerciseLogWithSets(
            exerciseLog = ExerciseLogEntity(
                id = 100L,
                workoutId = 10L,
                exerciseKey = null,
                name = "杠铃卧推",
                sortOrder = 1,
            ),
            sets = listOf(
                SetLogEntity(id = 1L, exerciseLogId = 100L, setNumber = 1, weightKg = 40f, reps = 12, setType = "WARMUP"),
                SetLogEntity(id = 2L, exerciseLogId = 100L, setNumber = 2, weightKg = 80f, reps = 10),
            ),
        )

        val model = relation.toModel()
        assertEquals(SetType.WARMUP, model.sets[0].setType)
        assertEquals(SetType.WORKING, model.sets[1].setType)

        val warmupEntity = SetLog(weightKg = 40f, reps = 12, setType = SetType.WARMUP)
            .toEntity(exerciseLogId = 100L, setNumber = 1)
        assertEquals("WARMUP", warmupEntity.setType)

        val workingEntity = SetLog(weightKg = 80f, reps = 10)
            .toEntity(exerciseLogId = 100L, setNumber = 2)
        assertEquals("WORKING", workingEntity.setType)
    }

    /**
     * 测试库中非法组类型字符串按正式组容错（不崩溃）。
     */
    @Test
    fun testSetType_invalidStringFallsBackToWorking() {
        val relation = ExerciseLogWithSets(
            exerciseLog = ExerciseLogEntity(
                id = 100L,
                workoutId = 10L,
                exerciseKey = null,
                name = "杠铃卧推",
                sortOrder = 1,
            ),
            sets = listOf(
                SetLogEntity(id = 1L, exerciseLogId = 100L, setNumber = 1, weightKg = 80f, reps = 10, setType = "DROP"),
            ),
        )

        val model = relation.toModel()
        assertEquals(SetType.WORKING, model.sets[0].setType)
    }
}

package com.example.fitlog.data.mapper

import com.example.fitlog.data.local.entity.workout.ExerciseLogEntity
import com.example.fitlog.data.local.entity.workout.SetLogEntity
import com.example.fitlog.data.local.entity.workout.WorkoutEntity
import com.example.fitlog.data.local.relation.ExerciseLogWithSets
import com.example.fitlog.data.local.relation.WorkoutWithExerciseLogs
import com.example.fitlog.model.ExerciseLog
import com.example.fitlog.model.SetLog
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
     * 测试扁平 [WorkoutEntity] 转领域模型：字段透传，exercises 为空列表。
     */
    @Test
    fun testWorkoutEntityToModel_flatFieldsMapped_exercisesEmpty() {
        val entity = WorkoutEntity(
            id = 7L,
            userId = 3L,
            date = date,
            feelings = "状态不错",
            sourceFileName = "2026-05-20.md",
            rawContent = "原始内容",
        )

        val model = entity.toModel()

        assertEquals(7L, model.id)
        assertEquals(3L, model.userId)
        assertEquals(date, model.date)
        assertEquals("状态不错", model.feelings)
        assertEquals("2026-05-20.md", model.sourceFileName)
        assertEquals("原始内容", model.rawContent)
        assertTrue(model.exercises.isEmpty())
    }

    /**
     * 测试可空字段为 null 时透传为 null。
     */
    @Test
    fun testWorkoutEntityToModel_nullableFieldsPassThrough() {
        val entity = WorkoutEntity(
            id = 1L,
            date = date,
            feelings = null,
            sourceFileName = null,
            rawContent = null,
        )

        val model = entity.toModel()

        assertNull(model.feelings)
        assertNull(model.sourceFileName)
        assertNull(model.rawContent)
    }

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
}

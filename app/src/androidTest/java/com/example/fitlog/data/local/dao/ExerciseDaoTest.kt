package com.example.fitlog.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fitlog.data.local.AppDatabase
import com.example.fitlog.data.local.entity.ExerciseEntity
import com.example.fitlog.model.BodyPart
import com.example.fitlog.model.Muscle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ExerciseDao] 的仪器化测试。
 *
 * 在真实 SQLite 上验证动作库的 CRUD、REPLACE 冲突策略、
 * 身体部位/肌群/自定义筛选、模糊搜索，以及 TypeConverter
 * （肌群列表、JSON 步骤列表）与真实数据库的集成。
 */
@RunWith(AndroidJUnit4::class)
class ExerciseDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var exerciseDao: ExerciseDao

    /**
     * 创建内存数据库。
     */
    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        exerciseDao = db.exerciseDao()
    }

    /**
     * 关闭数据库。
     */
    @After
    fun closeDb() {
        db.close()
    }

    private fun exercise(
        id: String,
        name: String,
        primaryMuscles: List<Muscle> = listOf(Muscle.CHEST),
        isCustom: Boolean = false,
        bodyPart: BodyPart = BodyPart.CHEST,
    ) = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscles = primaryMuscles,
        isCustom = isCustom,
        bodyPart = bodyPart,
    )

    /**
     * 测试插入与按 ID 查询。
     */
    @Test
    fun insertAndGetById() = runTest {
        exerciseDao.insert(exercise("barbell-bench-press", "杠铃卧推"))

        val fetched = exerciseDao.getById("barbell-bench-press")
        assertNotNull(fetched)
        assertEquals("杠铃卧推", fetched?.name)
    }

    /**
     * 测试相同 ID 重复插入时替换（REPLACE）且保持单行。
     */
    @Test
    fun insertDuplicateId_replacesAndKeepsSingleRow() = runTest {
        exerciseDao.insert(exercise("barbell-bench-press", "杠铃卧推"))
        exerciseDao.insert(exercise("barbell-bench-press", "杠铃平板卧推"))

        assertEquals("杠铃平板卧推", exerciseDao.getById("barbell-bench-press")?.name)
        assertEquals(1, exerciseDao.getAll().size)
    }

    /**
     * 测试批量插入与计数。
     */
    @Test
    fun insertAllAndGetCount() = runTest {
        exerciseDao.insertAll(
            listOf(
                exercise("ex-1", "动作一"),
                exercise("ex-2", "动作二"),
                exercise("ex-3", "动作三"),
            ),
        )

        assertEquals(3, exerciseDao.getCount())
    }

    /**
     * 测试更新与删除。
     */
    @Test
    fun updateAndDelete() = runTest {
        exerciseDao.insert(exercise("barbell-bench-press", "杠铃卧推"))

        exerciseDao.update(exercise("barbell-bench-press", "杠铃卧推（更新）"))
        assertEquals("杠铃卧推（更新）", exerciseDao.getById("barbell-bench-press")?.name)

        exerciseDao.delete(exercise("barbell-bench-press", "杠铃卧推（更新）"))
        assertNull(exerciseDao.getById("barbell-bench-press"))
    }

    /**
     * 测试按身体部位、主要肌群、自定义标识筛选。
     */
    @Test
    fun filters_byBodyPartMuscleAndCustom() = runTest {
        exerciseDao.insert(exercise("ex-1", "杠铃卧推", listOf(Muscle.CHEST), isCustom = false))
        exerciseDao.insert(
            exercise("ex-2", "杠铃深蹲", listOf(Muscle.QUADRICEPS), isCustom = true, bodyPart = BodyPart.UPPER_LEGS),
        )
        exerciseDao.insert(
            exercise("ex-3", "慢跑", listOf(Muscle.CARDIO), isCustom = false, bodyPart = BodyPart.CARDIO),
        )

        assertEquals(1, exerciseDao.getByBodyPart("CHEST").size)
        assertEquals(1, exerciseDao.getByBodyPart("CARDIO").size)

        val chest = exerciseDao.getByMuscle("CHEST")
        assertEquals(1, chest.size)
        assertEquals("ex-1", chest[0].id)

        // 多肌群动作：primaryMuscles 含 LOWER_BACK 和 HAMSTRINGS 时都能被 LIKE 匹配到
        exerciseDao.insert(
            exercise("ex-4", "硬拉", listOf(Muscle.HAMSTRINGS, Muscle.LOWER_BACK), bodyPart = BodyPart.BACK),
        )
        assertEquals(1, exerciseDao.getByMuscle("HAMSTRINGS").size)
        assertEquals(1, exerciseDao.getByMuscle("LOWER_BACK").size)

        val customs = exerciseDao.getCustomExercises()
        assertEquals(1, customs.size)
        assertEquals("ex-2", customs[0].id)
    }

    /**
     * 测试按名称精确查询与模糊搜索（模糊搜索结果按名称升序）。
     */
    @Test
    fun getByNameAndSearchByName() = runTest {
        exerciseDao.insert(exercise("ex-1", "哑铃推举"))
        exerciseDao.insert(exercise("ex-2", "哑铃侧平举"))
        exerciseDao.insert(exercise("ex-3", "杠铃卧推"))

        val exact = exerciseDao.getByName("哑铃推举")
        assertNotNull(exact)
        assertEquals("ex-1", exact?.id)

        val fuzzy = exerciseDao.searchByName("哑铃")
        assertEquals(2, fuzzy.size)
        assertEquals(listOf("哑铃侧平举", "哑铃推举"), fuzzy.map { it.name })

        assertTrue(exerciseDao.searchByName("不存在的动作").isEmpty())
    }

    /**
     * 测试 TypeConverter 与真实数据库的集成：
     * 肌群列表与含逗号/空格的步骤列表原样往返。
     */
    @Test
    fun typeConverters_roundTripThroughRealDatabase() = runTest {
        val entity = ExerciseEntity(
            id = "barbell-bench-press",
            name = "杠铃卧推",
            primaryMuscles = listOf(Muscle.CHEST),
            secondaryMuscles = listOf(Muscle.SHOULDERS, Muscle.TRICEPS),
            instructions = listOf("躺在平板椅上, 双手略宽于肩", "缓慢下放到胸口", "呼气推起"),
        )

        exerciseDao.insert(entity)
        val fetched = exerciseDao.getById("barbell-bench-press")

        assertNotNull(fetched)
        assertEquals(
            listOf(Muscle.SHOULDERS, Muscle.TRICEPS),
            fetched?.secondaryMuscles,
        )
        // 含逗号的步骤文本必须原样保留（JSON 序列化而非逗号拼接的关键验证）
        assertEquals(
            listOf("躺在平板椅上, 双手略宽于肩", "缓慢下放到胸口", "呼气推起"),
            fetched?.instructions,
        )
    }
}

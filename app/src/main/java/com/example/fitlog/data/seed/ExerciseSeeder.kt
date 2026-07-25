package com.example.fitlog.data.seed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.fitlog.R
import com.example.fitlog.data.local.dao.ExerciseDao
import com.example.fitlog.data.local.entity.ExerciseEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 动作库种子数据导入器。
 *
 * 从 `res/raw/exercises.json` 解析预处理的 exercises-dataset 数据，
 * 映射为 [ExerciseEntity] 并批量写入 Room。
 *
 * 通过 DataStore 记录 seed 版本号，确保仅在首次启动或数据更新时执行导入。
 */
@Singleton
class ExerciseSeeder @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 检查并执行种子数据导入。
     *
     * 跳过条件：seed 版本已最新 **且** 动作表非空。
     * 版本号存在但表为空（如 fallbackToDestructiveMigration 升级清库后，
     * DataStore 版本号残留）时强制重灌，避免动作库永久缺失。
     */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val currentSeedVersion = dataStore.data
            .map { it[SEED_VERSION_KEY] ?: 0 }
            .first()

        if (currentSeedVersion >= SEED_VERSION && exerciseDao.getCount() > 0) return@withContext

        val jsonString = context.resources.openRawResource(R.raw.exercises)
            .bufferedReader().use { it.readText() }

        val seedList = json.decodeFromString<List<ExerciseSeedData>>(jsonString)
        val entities = seedList.mapNotNull { ExerciseSeedMapper.toEntity(it) }
        exerciseDao.insertAll(entities)

        dataStore.edit { it[SEED_VERSION_KEY] = SEED_VERSION }
    }

    companion object {
        /** 当前种子数据版本号，更新数据时递增。 */
        private const val SEED_VERSION = 1
        private val SEED_VERSION_KEY = intPreferencesKey("exercise_seed_version")

        /** GIF 动图的 GitHub raw URL 前缀。 */
        internal const val GIF_BASE_URL =
            "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/videos/"
    }
}

/**
 * 种子数据 DTO → Room 实体的映射器。
 *
 * 独立为 object 以便单元测试。
 */
internal object ExerciseSeedMapper {

    /**
     * 将种子数据 DTO 映射为 Room 实体。
     *
     * 映射逻辑：
     * - `target` → primaryMuscles 第一个元素
     * - `muscle_group` 若与 target 不同 → primaryMuscles 第二个元素
     * - `secondary_muscles` → secondaryMuscles
     * - `name` → kebab-case ID + 首字母大写名称
     * - `isCompound` ← 总肌肉数 >= 3
     * - `instructions["zh"]` → description
     * - `instruction_steps["zh"]` → instructions
     * - `image` → imageUrl（assets 相对路径）
     * - `gif_url` → gifUrl（GitHub raw URL）
     *
     * @return 映射成功的 [ExerciseEntity]，若 target 无法映射则返回 null
     */
    fun toEntity(data: ExerciseSeedData): ExerciseEntity? {
        val primaryMuscle = MuscleMapper.map(data.target) ?: return null
        val secondaryMuscleList = data.secondary_muscles.mapNotNull { MuscleMapper.map(it) }
        val groupMuscle = MuscleMapper.map(data.muscle_group)

        // target 为主，muscle_group 如果不同则加入 primary
        val primaryMuscles = if (groupMuscle != null && groupMuscle != primaryMuscle) {
            listOf(primaryMuscle, groupMuscle)
        } else {
            listOf(primaryMuscle)
        }

        val allMuscles = primaryMuscles + secondaryMuscleList

        return ExerciseEntity(
            id = data.name.toKebabCase(),
            name = data.name.replaceFirstChar { it.uppercase() },
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscleList,
            isCompound = allMuscles.size >= 3,
            isCustom = false,
            equipment = EquipmentMapper.map(data.equipment),
            bodyPart = BodyPartMapper.map(data.body_part),
            description = data.instructions["zh"],
            instructions = data.instruction_steps["zh"] ?: emptyList(),
            imageUrl = data.image.substringAfterLast("/"),
            gifUrl = ExerciseSeeder.GIF_BASE_URL + data.gif_url.removePrefix("videos/"),
        )
    }

    /**
     * 将动作名称转为 kebab-case ID。
     *
     * 例如 "barbell bench front squat" → "barbell-bench-front-squat"
     * 例如 "3/4 sit-up" → "3-4-sit-up"
     */
    internal fun String.toKebabCase(): String =
        lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}

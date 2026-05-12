package com.example.fitlog.data.local

import androidx.room.TypeConverter
import com.example.fitlog.model.Difficulty
import com.example.fitlog.model.Equipment
import com.example.fitlog.model.ExerciseCategory
import com.example.fitlog.model.Force
import com.example.fitlog.model.MovementPattern
import com.example.fitlog.model.MuscleGroup
import com.example.fitlog.model.PrimaryMuscle
import kotlinx.serialization.json.Json

/**
 * [ExerciseEntity] 的 Room TypeConverter。
 *
 * 负责枚举和列表类型与数据库可存储的字符串之间的双向转换。
 */
class ExerciseConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromPrimaryMuscle(value: PrimaryMuscle?): String? = value?.name

    @TypeConverter
    fun toPrimaryMuscle(value: String?): PrimaryMuscle? = value?.let { PrimaryMuscle.valueOf(it) }

    @TypeConverter
    fun fromMovementPattern(value: MovementPattern?): String? = value?.name

    @TypeConverter
    fun toMovementPattern(value: String?): MovementPattern? = value?.let { MovementPattern.valueOf(it) }

    @TypeConverter
    fun fromForce(value: Force?): String? = value?.name

    @TypeConverter
    fun toForce(value: String?): Force? = value?.let { Force.valueOf(it) }

    @TypeConverter
    fun fromDifficulty(value: Difficulty?): String? = value?.name

    @TypeConverter
    fun toDifficulty(value: String?): Difficulty? = value?.let { Difficulty.valueOf(it) }

    @TypeConverter
    fun fromEquipment(value: Equipment?): String? = value?.name

    @TypeConverter
    fun toEquipment(value: String?): Equipment? = value?.let { Equipment.valueOf(it) }

    @TypeConverter
    fun fromExerciseCategory(value: ExerciseCategory): String = value.name

    @TypeConverter
    fun toExerciseCategory(value: String): ExerciseCategory = ExerciseCategory.valueOf(value)

    @TypeConverter
    fun fromMuscleGroupList(value: List<MuscleGroup>): String =
        value.joinToString(",") { it.name }

    @TypeConverter
    fun toMuscleGroupList(value: String): List<MuscleGroup> =
        if (value.isEmpty()) emptyList() else value.split(",").map { MuscleGroup.valueOf(it) }

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        value.joinToString("") { it }

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split("")
}

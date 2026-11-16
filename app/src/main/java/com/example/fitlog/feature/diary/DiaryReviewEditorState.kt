package com.example.fitlog.feature.diary

import com.example.fitlog.model.record.ReviewedExercise
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit

internal data class DiaryExerciseAddress(val session: Int, val exercise: Int)
internal data class DiaryExerciseEdit(val address: DiaryExerciseAddress, val exercise: ReviewedExercise?,
    val fragments: List<DiaryReviewFragment>)

internal enum class DiarySetField { WEIGHT, REPS, BASIS }
internal data class DiarySetEdit(val address: DiarySetAddress, val number: Int, val field: DiarySetField,
    val weight: Double?, val unit: WeightUnit?, val reps: Int?, val basis: WeightBasis? = null)

internal fun repOptions(current: Int?): List<Int?> = listOf(null) +
    ((1..100).toList() + listOfNotNull(current)).distinct().sorted()

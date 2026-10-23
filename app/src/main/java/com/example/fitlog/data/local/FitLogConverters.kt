package com.example.fitlog.data.local

import androidx.room.TypeConverter
import com.example.fitlog.model.analysis.ParseRunStatus
import com.example.fitlog.model.record.RecordKind
import com.example.fitlog.model.record.RecordOrigin
import com.example.fitlog.model.record.WeightBasis
import com.example.fitlog.model.record.WeightUnit

internal class FitLogConverters {
    @TypeConverter fun origin(value: String): RecordOrigin = RecordOrigin.valueOf(value)
    @TypeConverter fun origin(value: RecordOrigin): String = value.name
    @TypeConverter fun kind(value: String): RecordKind = RecordKind.valueOf(value)
    @TypeConverter fun kind(value: RecordKind): String = value.name
    @TypeConverter fun status(value: String): ParseRunStatus = ParseRunStatus.valueOf(value)
    @TypeConverter fun status(value: ParseRunStatus): String = value.name
    @TypeConverter fun unit(value: String?): WeightUnit? = value?.let(WeightUnit::valueOf)
    @TypeConverter fun unit(value: WeightUnit?): String? = value?.name
    @TypeConverter fun basis(value: String?): WeightBasis? = value?.let(WeightBasis::valueOf)
    @TypeConverter fun basis(value: WeightBasis?): String? = value?.name
}

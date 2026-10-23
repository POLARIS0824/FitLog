package com.example.fitlog.data.local

import android.content.Context
import androidx.room.*
import com.example.fitlog.data.analysis.ParseRunRow
import com.example.fitlog.data.record.ConfirmedExerciseRow
import com.example.fitlog.data.record.ConfirmedSessionRow
import com.example.fitlog.data.record.ConfirmedSetRow
import com.example.fitlog.data.record.TrainingRecordRow

@Database(entities = [ParseRunRow::class, TrainingRecordRow::class, ConfirmedSessionRow::class,
    ConfirmedExerciseRow::class, ConfirmedSetRow::class], version = 3, exportSchema = true)
@TypeConverters(FitLogConverters::class)
internal abstract class FitLogDatabase : RoomDatabase() {
    abstract fun analysis(): FitLogDao
    companion object {
        fun open(context: Context): FitLogDatabase =
            Room.databaseBuilder(context.applicationContext, FitLogDatabase::class.java, "training-records.db").build()
    }
}

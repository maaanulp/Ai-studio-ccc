package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CrewAccountEntity
import com.example.data.model.IntelligenceReportEntity
import com.example.data.model.LogEntryEntity
import com.example.data.model.OcrTextResultEntity
import com.example.data.model.RaidLogEntity
import com.example.data.model.TargetEntity

@Database(
    entities = [
        TargetEntity::class,
        RaidLogEntity::class,
        CrewAccountEntity::class,
        IntelligenceReportEntity::class,
        LogEntryEntity::class,
        OcrTextResultEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetDao(): TargetDao
    abstract fun intelligenceReportDao(): IntelligenceReportDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun ocrTextResultDao(): OcrTextResultDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "crypt0_cr3w_central.db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

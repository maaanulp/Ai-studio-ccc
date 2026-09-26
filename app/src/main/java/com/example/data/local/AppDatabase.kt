package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CrewAccountEntity
import com.example.data.model.FeedCommentEntity
import com.example.data.model.FeedPostEntity
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
        OcrTextResultEntity::class,
        FeedPostEntity::class,
        FeedCommentEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetDao(): TargetDao
    abstract fun intelligenceReportDao(): IntelligenceReportDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun ocrTextResultDao(): OcrTextResultDao
    abstract fun feedDao(): FeedDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "crypt0_cr3w_central.db"
                ).fallbackToDestructiveMigration(true)
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

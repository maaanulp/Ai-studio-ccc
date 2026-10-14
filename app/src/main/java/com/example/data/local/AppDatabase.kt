package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.*

@Database(
    entities = [
        TargetEntity::class,
        RaidLogEntity::class,
        CrewAccountEntity::class,
        FeedPostEntity::class,
        FeedCommentEntity::class,
        IntelligenceReportEntity::class,
        LogEntryEntity::class,
        OcrTextResultEntity::class,
        AuditLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun targetDao(): TargetDao
    abstract fun feedDao(): FeedDao
    abstract fun intelligenceReportDao(): IntelligenceReportDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun ocrTextResultDao(): OcrTextResultDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "crypt0_cr3w.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

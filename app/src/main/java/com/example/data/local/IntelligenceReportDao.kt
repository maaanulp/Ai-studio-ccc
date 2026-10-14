package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.IntelligenceReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntelligenceReportDao {
    @Query("SELECT * FROM intelligence_reports ORDER BY createdAt DESC")
    fun getAllReports(): Flow<List<IntelligenceReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: IntelligenceReportEntity): Long

    @Query("DELETE FROM intelligence_reports WHERE id = :id")
    suspend fun deleteReport(id: Long)

    @Query("DELETE FROM intelligence_reports")
    suspend fun purgeReports()
}

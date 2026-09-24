package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DatabaseScope
import com.example.data.model.IntelligenceReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IntelligenceReportDao {
    @Query("SELECT * FROM intelligence_reports ORDER BY generatedTimestamp DESC")
    fun getAllReports(): Flow<List<IntelligenceReportEntity>>

    @Query("SELECT * FROM intelligence_reports WHERE scope = :scope ORDER BY generatedTimestamp DESC")
    fun getReportsByScope(scope: DatabaseScope): Flow<List<IntelligenceReportEntity>>

    @Query("SELECT * FROM intelligence_reports WHERE targetIp = :ip ORDER BY generatedTimestamp DESC")
    fun getReportsForIp(ip: String): Flow<List<IntelligenceReportEntity>>

    @Query("SELECT * FROM intelligence_reports WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: Long): IntelligenceReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: IntelligenceReportEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<IntelligenceReportEntity>)

    @Update
    suspend fun updateReport(report: IntelligenceReportEntity)

    @Delete
    suspend fun deleteReport(report: IntelligenceReportEntity)

    @Query("DELETE FROM intelligence_reports WHERE id = :id")
    suspend fun deleteReportById(id: Long)

    @Query("DELETE FROM intelligence_reports WHERE scope = :scope")
    suspend fun purgeReportsByScope(scope: DatabaseScope)

    @Query("DELETE FROM intelligence_reports")
    suspend fun purgeAllReports()
}

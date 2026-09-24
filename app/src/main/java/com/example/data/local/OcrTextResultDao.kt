package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.OcrTextResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrTextResultDao {
    @Query("SELECT * FROM ocr_text_results ORDER BY processedTimestamp DESC")
    fun getAllOcrResults(): Flow<List<OcrTextResultEntity>>

    @Query("SELECT * FROM ocr_text_results WHERE scanType = :scanType ORDER BY processedTimestamp DESC")
    fun getOcrResultsByType(scanType: String): Flow<List<OcrTextResultEntity>>

    @Query("SELECT * FROM ocr_text_results WHERE id = :id LIMIT 1")
    suspend fun getOcrResultById(id: Long): OcrTextResultEntity?

    @Query("SELECT * FROM ocr_text_results WHERE detectedIp = :ip ORDER BY processedTimestamp DESC")
    fun getOcrResultsForIp(ip: String): Flow<List<OcrTextResultEntity>>

    @Query("SELECT * FROM ocr_text_results ORDER BY processedTimestamp DESC LIMIT :limit")
    fun getRecentOcrResults(limit: Int): Flow<List<OcrTextResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrResult(result: OcrTextResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrResults(results: List<OcrTextResultEntity>)

    @Delete
    suspend fun deleteOcrResult(result: OcrTextResultEntity)

    @Query("DELETE FROM ocr_text_results WHERE id = :id")
    suspend fun deleteOcrResultById(id: Long)

    @Query("DELETE FROM ocr_text_results")
    suspend fun purgeAllOcrResults()
}

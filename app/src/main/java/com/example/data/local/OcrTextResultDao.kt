package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.DatabaseScope
import com.example.data.model.OcrTextResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrTextResultDao {
    @Query("SELECT * FROM ocr_text_results WHERE scope = :scope ORDER BY processedTimestamp DESC")
    fun getOcrResults(scope: DatabaseScope): Flow<List<OcrTextResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOcrResult(result: OcrTextResultEntity): Long

    @Query("DELETE FROM ocr_text_results WHERE scope = :scope")
    suspend fun purgeOcrResults(scope: DatabaseScope)
}

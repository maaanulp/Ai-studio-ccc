package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.DatabaseScope
import com.example.data.model.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries WHERE scope = :scope ORDER BY ingestedAt DESC")
    fun getLogEntries(scope: DatabaseScope): Flow<List<LogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogEntries(entries: List<LogEntryEntity>)

    @Query("DELETE FROM log_entries WHERE scope = :scope")
    suspend fun purgeLogEntries(scope: DatabaseScope)
}

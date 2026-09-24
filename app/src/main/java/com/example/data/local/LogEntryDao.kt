package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.DatabaseScope
import com.example.data.model.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY id DESC")
    fun getAllLogEntries(): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE logType = :logType ORDER BY id DESC")
    fun getLogEntriesByType(logType: String): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE scope = :scope ORDER BY id DESC")
    fun getLogEntriesByScope(scope: DatabaseScope): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE parsedIp = :ip ORDER BY id DESC")
    fun getLogEntriesForIp(ip: String): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries ORDER BY id DESC LIMIT :limit")
    fun getRecentLogEntries(limit: Int): Flow<List<LogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogEntry(entry: LogEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogEntries(entries: List<LogEntryEntity>)

    @Delete
    suspend fun deleteLogEntry(entry: LogEntryEntity)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteLogEntryById(id: Long)

    @Query("DELETE FROM log_entries WHERE scope = :scope")
    suspend fun purgeLogEntriesByScope(scope: DatabaseScope)

    @Query("DELETE FROM log_entries WHERE logType = :logType")
    suspend fun purgeLogEntriesByType(logType: String)

    @Query("DELETE FROM log_entries")
    suspend fun purgeAllLogEntries()
}

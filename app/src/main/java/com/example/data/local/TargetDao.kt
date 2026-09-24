package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CrewAccountEntity
import com.example.data.model.DatabaseScope
import com.example.data.model.RaidLogEntity
import com.example.data.model.TargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets WHERE scope = :scope ORDER BY lastUpdated DESC")
    fun getTargetsByScope(scope: DatabaseScope): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets WHERE scope = :scope AND ip = :ip LIMIT 1")
    suspend fun getTargetByIpAndScope(ip: String, scope: DatabaseScope): TargetEntity?

    @Query("SELECT * FROM targets WHERE scope = :scope AND name = :name LIMIT 1")
    suspend fun getTargetByNameAndScope(name: String, scope: DatabaseScope): TargetEntity?

    @Query("SELECT * FROM targets WHERE ip = :ip LIMIT 1")
    suspend fun findAnyTargetByIp(ip: String): TargetEntity?

    @Query("""
        SELECT * FROM targets 
        WHERE scope = :scope 
        AND (ip LIKE '%' || :query || '%' 
             OR name LIKE '%' || :query || '%' 
             OR wallet LIKE '%' || :query || '%'
             OR crew LIKE '%' || :query || '%')
        ORDER BY lastUpdated DESC
    """)
    fun searchTargets(scope: DatabaseScope, query: String): Flow<List<TargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTarget(target: TargetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargets(targets: List<TargetEntity>)

    @Update
    suspend fun updateTarget(target: TargetEntity)

    @Query("DELETE FROM targets WHERE scope = :scope")
    suspend fun purgeScope(scope: DatabaseScope)

    @Query("DELETE FROM targets WHERE id = :id")
    suspend fun deleteTargetById(id: Long)

    // Raid logs queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaidLogs(logs: List<RaidLogEntity>)

    @Query("SELECT * FROM raid_logs WHERE scope = :scope ORDER BY id DESC")
    fun getRaidLogs(scope: DatabaseScope): Flow<List<RaidLogEntity>>

    @Query("SELECT * FROM raid_logs WHERE ip = :ip ORDER BY id DESC")
    fun getRaidLogsForIp(ip: String): Flow<List<RaidLogEntity>>

    @Query("DELETE FROM raid_logs WHERE scope = :scope")
    suspend fun purgeRaidLogs(scope: DatabaseScope)

    // Crew accounts
    @Query("SELECT * FROM crew_accounts ORDER BY username ASC")
    fun getAllCrewAccounts(): Flow<List<CrewAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrewAccount(account: CrewAccountEntity)
}

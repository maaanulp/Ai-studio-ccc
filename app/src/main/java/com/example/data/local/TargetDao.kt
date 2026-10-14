package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets WHERE scope = :scope ORDER BY lastUpdated DESC")
    fun getTargetsByScope(scope: DatabaseScope): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets WHERE scope = :scope AND (ip LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%' OR wallet LIKE '%' || :query || '%' OR crew LIKE '%' || :query || '%') ORDER BY lastUpdated DESC")
    fun searchTargets(scope: DatabaseScope, query: String): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets WHERE ip = :ip AND scope = :scope LIMIT 1")
    suspend fun getTargetByIpAndScope(ip: String, scope: DatabaseScope): TargetEntity?

    @Query("SELECT * FROM targets WHERE name = :name AND scope = :scope LIMIT 1")
    suspend fun getTargetByNameAndScope(name: String, scope: DatabaseScope): TargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTarget(target: TargetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargets(targets: List<TargetEntity>)

    @Update
    suspend fun updateTarget(target: TargetEntity)

    @Delete
    suspend fun deleteTarget(target: TargetEntity)

    @Query("DELETE FROM targets WHERE scope = :scope")
    suspend fun purgeTargetsByScope(scope: DatabaseScope)

    @Query("SELECT * FROM raid_logs WHERE scope = :scope ORDER BY createdAt DESC")
    fun getRaidLogs(scope: DatabaseScope): Flow<List<RaidLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaidLogs(logs: List<RaidLogEntity>)

    @Query("DELETE FROM raid_logs WHERE scope = :scope")
    suspend fun purgeRaidLogs(scope: DatabaseScope)

    @Query("SELECT * FROM crew_accounts")
    fun getCrewAccounts(): Flow<List<CrewAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrewAccount(account: CrewAccountEntity)

    @Query("DELETE FROM crew_accounts WHERE username = :username")
    suspend fun deleteCrewAccount(username: String)
}

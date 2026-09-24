package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DatabaseScope {
    INTERNAL,
    EXTERNAL,
    GENERAL
}

@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ip: String,
    val name: String = "",
    val level: Int = 1,
    val fw: Int = 1,                 // Firewall level
    val enc: Int = 1,                // Encryptor level
    val rep: Int = 0,                // Reputation
    val score: Long = 0L,
    val crew: String = "",
    val stolenCrypto: Long = 0L,     // Total crypto stolen
    val hitCount: Int = 0,           // Total hits count
    val avgPerHit: Long = 0L,        // Average crypto stolen per hit
    val crPerHour: Long = 0L,        // Estimated Crypto per Hour
    val peakHour: String = "--:--",   // Peak attack window
    val wallet: String = "",
    val scope: DatabaseScope = DatabaseScope.INTERNAL,
    val contributor: String = "m0lt0rn",
    val lastUpdated: Long = System.currentTimeMillis(),

    // 11 Installed Apps Levels (from APPS screenshot OCR)
    val antivirusLvl: Int = 0,
    val spamLvl: Int = 0,
    val rootkitLvl: Int = 0,
    val firewallAppLvl: Int = 0,
    val bypasserLvl: Int = 0,
    val passwordCrackerLvl: Int = 0,
    val passwordEncryptorLvl: Int = 0,
    val proxyLvl: Int = 0,
    val traceLvl: Int = 0,
    val keygenLvl: Int = 0,
    val siphonLvl: Int = 0,
    val appsParsed: Boolean = false,
    val notes: String = ""
)

@Entity(tableName = "raid_logs")
data class RaidLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ip: String,
    val wallet: String,
    val stolenAmount: Long,
    val timestampStr: String, // e.g. "9-22 11:05"
    val parsedHour: Int = -1,
    val scope: DatabaseScope = DatabaseScope.INTERNAL,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "crew_accounts")
data class CrewAccountEntity(
    @PrimaryKey val username: String,
    val crewId: String = "CCC",
    val isActive: Boolean = true,
    val isOnline: Boolean = true,
    val role: String = "OPERATIVE" // "ADMIN" or "OPERATIVE"
)

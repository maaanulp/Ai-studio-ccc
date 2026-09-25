package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_entries")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logType: String, // "INPUT" or "OUTPUT"
    val rawText: String,
    val parsedIp: String? = null,
    val parsedWallet: String? = null,
    val parsedAmount: Long? = null,
    val eventTimestamp: String? = null,
    val scope: DatabaseScope = DatabaseScope.INTERNAL,
    val contributor: String = "",
    val ingestedAt: Long = System.currentTimeMillis()
)

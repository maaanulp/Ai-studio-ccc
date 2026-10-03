package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operatorHandle: String = "UNASSIGNED",
    val action: String = "",
    val details: String = "",
    val targetIp: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
)

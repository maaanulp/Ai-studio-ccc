package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intelligence_reports")
data class IntelligenceReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetIp: String,
    val targetName: String = "",
    val targetCrew: String = "",
    val targetLevel: Int = 1,
    val firewallLvl: Int = 1,
    val encryptorLvl: Int = 1,
    val totalStolen: Long = 0L,
    val hitCount: Int = 0,
    val avgStolen: Long = 0L,
    val peakHour: String = "--:--",
    val generatedBy: String = "OPERATIVE",
    val reportJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)

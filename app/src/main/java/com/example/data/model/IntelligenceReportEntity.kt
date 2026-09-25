package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intelligence_reports")
data class IntelligenceReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetIp: String,
    val targetName: String = "",
    val classification: String = "CONFIDENTIAL", // e.g. "CONFIDENTIAL", "TOP SECRET", "RESTRICTED"
    val threatLevel: String = "MODERATE",        // "LOW", "MODERATE", "HIGH", "CRITICAL"
    val scope: DatabaseScope = DatabaseScope.INTERNAL,
    val executiveSummary: String,
    val defenseAnalysis: String,
    val financialPayload: String,
    val recommendedVector: String,
    val authorOperative: String = "",
    val generatedTimestamp: Long = System.currentTimeMillis()
)

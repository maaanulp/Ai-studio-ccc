package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_text_results")
data class OcrTextResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String? = null,
    val scanType: String, // "PROFILE", "APPS", "MANUAL", "GENERIC"
    val rawExtractedText: String,
    val detectedAccountName: String? = null,
    val detectedIp: String? = null,
    val detectedCrew: String? = null,
    val detectedLevel: Int? = null,
    val detectedFw: Int? = null,
    val detectedEncr: Int? = null,
    val detectedAppsSummary: String? = null,
    val confidenceScore: Float = 0.98f,
    val processedTimestamp: Long = System.currentTimeMillis()
)

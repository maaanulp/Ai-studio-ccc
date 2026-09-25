package com.example

import com.example.data.model.DatabaseScope
import com.example.data.model.IntelligenceReportEntity
import com.example.data.model.LogEntryEntity
import com.example.data.model.OcrTextResultEntity
import com.example.data.model.TargetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomEntitiesTest {

    @Test
    fun testIntelligenceReportEntityCreation() {
        val report = IntelligenceReportEntity(
            id = 1L,
            targetIp = "192.168.1.100",
            targetName = "Target Alpha",
            title = "CLASSIFIED DOSSIER // Target Alpha [192.168.1.100]",
            classification = "TOP SECRET // EYES ONLY",
            threatLevel = "CRITICAL",
            defenseAnalysis = "FW: 65 (Hardware Level 65) | ENC: 55",
            financialPayload = "Total Stolen: 150000 ₡ across 10 ops | Peak Rate: 25000 ₡/hr",
            recommendedVector = "Deploy High-tier Bypasser against FW: 65, followed by Password Decryptor.",
            executiveSummary = "High-priority node under crew ShadowOps control.",
            scope = DatabaseScope.INTERNAL,
            authorOperative = "CyberOperative",
            generatedTimestamp = 1695484800000L
        )

        assertEquals("192.168.1.100", report.targetIp)
        assertEquals("Target Alpha", report.targetName)
        assertEquals("CRITICAL", report.threatLevel)
        assertEquals(DatabaseScope.INTERNAL, report.scope)
        assertEquals("CyberOperative", report.authorOperative)
        assertTrue(report.defenseAnalysis.contains("FW: 65"))
    }

    @Test
    fun testLogEntryEntityCreation() {
        val log = LogEntryEntity(
            id = 10L,
            logType = "INPUT",
            rawText = "Target 10.0.0.1 | Crypto: 45000 ₡ | Wallet: 0xabc...123",
            parsedIp = "10.0.0.1",
            parsedWallet = "0xabc...123",
            parsedAmount = 45000L,
            eventTimestamp = "2026-09-23 15:30:00",
            scope = DatabaseScope.INTERNAL,
            contributor = "CyberOperative"
        )

        assertEquals("INPUT", log.logType)
        assertEquals("10.0.0.1", log.parsedIp)
        assertEquals(45000L, log.parsedAmount)
        assertEquals(DatabaseScope.INTERNAL, log.scope)
    }

    @Test
    fun testOcrTextResultEntityCreation() {
        val ocrResult = OcrTextResultEntity(
            id = 5L,
            imageUri = "content://media/external/images/1",
            scanType = "PROFILE",
            rawExtractedText = "IP: 172.16.0.42 NAME: GhostNode LVL: 50 FW: 40 ENC: 35",
            detectedAccountName = "GhostNode",
            detectedIp = "172.16.0.42",
            detectedCrew = "CyberSyndicate",
            detectedLevel = 50,
            detectedFw = 40,
            detectedEncr = 35,
            confidenceScore = 0.98f
        )

        assertEquals("PROFILE", ocrResult.scanType)
        assertEquals("GhostNode", ocrResult.detectedAccountName)
        assertEquals("172.16.0.42", ocrResult.detectedIp)
        assertEquals(50, ocrResult.detectedLevel)
        assertTrue(ocrResult.confidenceScore > 0.9f)
    }

    @Test
    fun testTargetEntityCreation() {
        val target = TargetEntity(
            ip = "10.10.10.5",
            name = "Infiltrator",
            level = 42,
            fw = 38,
            enc = 40,
            wallet = "0x123...456",
            scope = DatabaseScope.INTERNAL,
            contributor = "CyberOperative"
        )

        assertNotNull(target)
        assertEquals("10.10.10.5", target.ip)
        assertEquals("Infiltrator", target.name)
        assertEquals(42, target.level)
    }
}

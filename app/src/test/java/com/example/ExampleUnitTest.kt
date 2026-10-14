package com.example

import com.example.parser.OcrParseOutput
import com.example.parser.OcrParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testArdForYaAppsMatrixFullExtraction() {
        val rawText = """
            ArdForYa apps
            Antivirus LVL 24
            Spam LVL 18
            Rootkit LVL 30
            Firewall LVL 28
            Bypasser LVL 22
            Password Cracker LVL 35
            Password Encryptor LVL 35
            Proxy LVL 12
            Trace LVL 20
            Keygen LVL 40
            Siphon LVL 25
        """.trimIndent()

        val apps = OcrParser.parseAppsMatrix(rawText)
        assertEquals("ArdForYa", apps.accountName)
        assertEquals(24, apps.antivirusLvl)
        assertEquals(18, apps.spamLvl)
        assertEquals(30, apps.rootkitLvl)
        assertEquals(28, apps.firewallLvl)
        assertEquals(22, apps.bypasserLvl)
        assertEquals(35, apps.passwordCrackerLvl)
        assertEquals(35, apps.passwordEncryptorLvl)
        assertEquals(12, apps.proxyLvl)
        assertEquals(20, apps.traceLvl)
        assertEquals(40, apps.keygenLvl)
        assertEquals(25, apps.siphonLvl)
        assertEquals(11, apps.getExtractedCount())
        assertTrue(apps.isValidMatrix)
        assertTrue(apps.getMissingApps().isEmpty())

        // Test through parseOcrText
        val output = OcrParser.parseOcrText(rawText)
        assertTrue(output is OcrParseOutput.AppsData)
        val data = output as OcrParseOutput.AppsData
        assertTrue(data.apps.isValidMatrix)
        assertEquals(11, data.apps.getExtractedCount())
    }

    @Test
    fun testFlexiblePartialAppsMatrixExtraction() {
        val rawText = """
            ArdForYa apps
            Antivirus LVL 24
            Spam LVL 18
            Rootkit LVL 30
            Bypasser LVL 22
            Password Cracker LVL 35
            Password Encryptor LVL 35
            Proxy LVL 12
            Trace LVL 20
            Keygen LVL 40
            Siphon LVL 25
        """.trimIndent() // Missing Firewall

        val apps = OcrParser.parseAppsMatrix(rawText)
        assertEquals("ArdForYa", apps.accountName)
        assertEquals(10, apps.getExtractedCount())
        assertTrue(apps.isValidMatrix) // Flexible partial ingestion is accepted
        assertEquals(listOf("Firewall"), apps.getMissingApps())
    }

    @Test
    fun testCrewExtractionFromAppsScreenshot() {
        val rawText = """
            ArdForYa [GSEX] apps
            Antivirus LVL 24
            Firewall LVL 28
        """.trimIndent()
        val apps = OcrParser.parseAppsMatrix(rawText)
        assertEquals("ArdForYa", apps.accountName)
        assertEquals("GSEX", apps.crew)
        assertEquals(24, apps.antivirusLvl)
        assertEquals(28, apps.firewallLvl)
    }

    @Test
    fun testRowLevelMappingWithDelimitersAndNoise() {
        val rawText = """
            // APPS
            Target: Operator_X
            Antivirus : LVL 45
            Spam - 32
            Rootkit | 40
            Firewall -> LVL 48
            Bypasser [Installed] LVL 45
            Password Cracker : LVL 48
            Password Encryptor : LVL 46
            Proxy LVL: 25
            Trace LVL- 33
            Keygen #50
            Siphon v42
        """.trimIndent()

        val apps = OcrParser.parseAppsMatrix(rawText)
        assertEquals(45, apps.antivirusLvl)
        assertEquals(32, apps.spamLvl)
        assertEquals(40, apps.rootkitLvl)
        assertEquals(48, apps.firewallLvl)
        assertEquals(45, apps.bypasserLvl)
        assertEquals(48, apps.passwordCrackerLvl)
        assertEquals(46, apps.passwordEncryptorLvl)
        assertEquals(25, apps.proxyLvl)
        assertEquals(33, apps.traceLvl)
        assertEquals(50, apps.keygenLvl)
        assertEquals(42, apps.siphonLvl)
        assertEquals(11, apps.getExtractedCount())
        assertTrue(apps.isValidMatrix)
    }

    @Test
    fun testAppsMatrixFuzzyAnchorAndIpFallback() {
        // Screenshot with Android status bar noise and no standard "apps" suffix
        val rawText = """
            12:45 PM  100%  LTE
            Target Node: 10.20.30.40
            INSTALLED SOFTWARE
            Antivirus LVL 50
            Firewall LVL 45
            Password Encryptor LVL 48
        """.trimIndent()

        val apps = OcrParser.parseAppsMatrix(rawText)
        assertTrue(apps.hasValidAnchor)
        assertTrue(apps.isValidMatrix)
        assertTrue(apps.accountName.isNotBlank())
        assertEquals(50, apps.antivirusLvl)
        assertEquals(45, apps.firewallLvl)
        assertEquals(48, apps.passwordEncryptorLvl)
        assertEquals(3, apps.getExtractedCount())
    }

    @Test
    fun testAppsMatrixTolerantFuzzySoftwareAliases() {
        val rawText = """
            CyberGhost
            Installed Programs
            Anti-Virus [Installed] LVL 30
            SpamBot LVL 22
            Root-Kit LVL 15
            Firewall App LVL 40
            By-passer LVL 25
            PW Cracker LVL 35
            PW Encryptor LVL 32
            Proxy LVL 18
            Tracer LVL 14
            Key-Gen LVL 42
            Crypto-Siphon LVL 28
        """.trimIndent()

        val apps = OcrParser.parseAppsMatrix(rawText)
        assertEquals("CyberGhost", apps.accountName)
        assertEquals(30, apps.antivirusLvl)
        assertEquals(22, apps.spamLvl)
        assertEquals(15, apps.rootkitLvl)
        assertEquals(40, apps.firewallLvl)
        assertEquals(25, apps.bypasserLvl)
        assertEquals(35, apps.passwordCrackerLvl)
        assertEquals(32, apps.passwordEncryptorLvl)
        assertEquals(18, apps.proxyLvl)
        assertEquals(14, apps.traceLvl)
        assertEquals(42, apps.keygenLvl)
        assertEquals(28, apps.siphonLvl)
        assertEquals(11, apps.getExtractedCount())
        assertTrue(apps.isValidMatrix)
    }
}


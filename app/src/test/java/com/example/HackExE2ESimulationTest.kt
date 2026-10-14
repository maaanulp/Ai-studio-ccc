package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.DatabaseScope
import com.example.data.model.TargetEntity
import com.example.data.repository.IntelRepository
import com.example.parser.LogParser
import com.example.parser.OcrParser
import com.example.ui.viewmodel.IntelViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HackExE2ESimulationTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: IntelRepository
    private lateinit var viewModel: IntelViewModel
    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = IntelRepository(
            targetDao = db.targetDao(),
            reportDao = db.intelligenceReportDao(),
            logEntryDao = db.logEntryDao(),
            ocrResultDao = db.ocrTextResultDao(),
            feedDao = db.feedDao()
        )
        viewModel = IntelViewModel(app, repository)
    }

    @After
    fun tearDown() {
        viewModel.stopOnlineCrewSync()
        db.close()
    }

    @Test
    fun `test reverse chronological log parsing and target extraction`() = runBlocking {
        viewModel.loginOperativeLocal("CyberGhost_88", "")
        assertEquals("CyberGhost_88", viewModel.currentProfile.value)

        val rawLogs = """
            [9-23 8:08] Accessed device at 111.98.13.146
            [9-23 8:08] Stole 485 Crypto from hx51f3...933d
            [9-23 8:08] Accessed device at xxx.xxx.xxx.xxx
            [9-23 8:08] Stole 259 Crypto from hxfa9c...ce4b
            [9-23 8:08] Accessed device at 129.101.254.235
            [9-23 8:07] Stole 412 Crypto from hxa93a...9cc6
            [9-23 8:07] Accessed device at 10.76.96.9
        """.trimIndent()

        val parseResult = LogParser.parseLogs(rawLogs, DatabaseScope.INTERNAL, viewModel.currentProfile.value)
        assertEquals(3, parseResult.targets.size)
        assertEquals(1, parseResult.ignoredMaskedCount)
        assertTrue(parseResult.summary.contains("3 valid targets extracted"))

        for (target in parseResult.targets) {
            repository.insertOrUpdateTarget(target)
        }

        val internalTargets = repository.getTargets(DatabaseScope.INTERNAL).first()
        assertEquals(3, internalTargets.size)

        val target10 = internalTargets.find { it.ip == "10.76.96.9" }
        assertNotNull(target10)
        assertEquals("hxa93a...9cc6", target10?.wallet)
        assertEquals(412L, target10?.stolenCrypto)
    }

    @Test
    fun `test header anchor rejection on apps screen without account name`() {
        val ocrWithoutAnchor = """
            // APPS
            Installed Software:
            Antivirus LVL 20
            Spam LVL 14
            Rootkit LVL 11
            Firewall LVL 25
        """.trimIndent()

        val parseOutput = OcrParser.parseOcrText(ocrWithoutAnchor)
        assertTrue("Capture without verified header anchor should be rejected as Unknown", parseOutput is com.example.parser.OcrParseOutput.Unknown)
    }

    @Test
    fun `test software level out of range and hallucination rejection`() {
        val ocrWithHallucination = """
            ArdForYa [GSEX] apps
            Antivirus LVL 24 1.8 MB
            Spam LVL 88
            Rootkit LVL 30
            Firewall LVL 28
            Bypasser LVL 99
            Password Cracker LVL 35
            Password Encryptor LVL 35
            Proxy LVL 12
            Trace LVL 20
            Keygen LVL 40
            Siphon LVL 25
        """.trimIndent()

        val apps = OcrParser.parseAppsMatrix(ocrWithHallucination)
        assertTrue(apps.hasValidAnchor)
        assertEquals("ArdForYa", apps.accountName)
        assertEquals("GSEX", apps.crew)
        assertEquals(24, apps.antivirusLvl)
        assertEquals(0, apps.spamLvl) // 88 is out of range 1..50 -> rejected to 0
        assertEquals(30, apps.rootkitLvl)
        assertEquals(28, apps.firewallLvl)
        assertEquals(0, apps.bypasserLvl) // 99 is out of range 1..50 -> rejected to 0
        assertEquals(35, apps.passwordCrackerLvl)
        assertEquals(35, apps.passwordEncryptorLvl)
        assertEquals(12, apps.proxyLvl)
        assertEquals(20, apps.traceLvl)
        assertEquals(40, apps.keygenLvl)
        assertEquals(25, apps.siphonLvl)
        assertEquals(9, apps.getExtractedCount())
        assertTrue("Partial matrix with 9 confirmed levels should be valid", apps.isValidMatrix)
    }

    @Test
    fun `test advanced search query key value pairs`() {
        val target = TargetEntity(
            ip = "10.0.0.1",
            name = "ShadowByte",
            crew = "CCC",
            antivirusLvl = 25,
            firewallAppLvl = 30,
            passwordCrackerLvl = 40
        )

        assertTrue(IntelViewModel.matchesIntelSearchQuery(target, "antivirus:25"))
        assertTrue(IntelViewModel.matchesIntelSearchQuery(target, "av:25"))
        assertTrue(IntelViewModel.matchesIntelSearchQuery(target, "firewall:30"))
        assertTrue(IntelViewModel.matchesIntelSearchQuery(target, "fw:30"))
        assertTrue(IntelViewModel.matchesIntelSearchQuery(target, "cracker:40"))
        assertTrue(IntelViewModel.matchesIntelSearchQuery(target, "ShadowByte"))
        assertTrue(IntelViewModel.matchesIntelSearchQuery(target, "10.0.0.1"))
        assertFalse(IntelViewModel.matchesIntelSearchQuery(target, "antivirus:30"))
        assertFalse(IntelViewModel.matchesIntelSearchQuery(target, "firewall:35"))
    }
}

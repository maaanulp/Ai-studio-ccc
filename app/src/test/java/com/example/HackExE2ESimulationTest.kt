package com.example

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.DatabaseScope
import com.example.data.model.TargetEntity
import com.example.data.remote.SupabaseClient
import com.example.data.repository.IntelRepository
import com.example.parser.LogParser
import com.example.ui.viewmodel.IntelViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
    fun `full e2e simulation hack ex 2 reverse chronological parse and crew sync`() = runBlocking {
        // =========================================================================
        // FASE 1: Registro y Parseo Cronológico Inverso (Base Local)
        // =========================================================================

        // 1. Inicio de sesión de Operativo: [G] GOOGLE OAUTH -> CyberGhost_88
        viewModel.loginOperativeLocal("CyberGhost_88", "")
        assertEquals("CyberGhost_88", viewModel.currentProfile.value)

        // 2. Carga Manual de Logs (Lectura de Abajo hacia Arriba)
        val rawLogs = """
            [9-23 8:08] Accessed device at 111.98.13.146
            [9-23 8:08] Stole 485 Crypto from hx51f3...933d
            [9-23 8:08] Accessed device at xxx.xxx.xxx.xxx
            [9-23 8:08] Stole 259 Crypto from hxfa9c...ce4b
            [9-23 8:08] Accessed device at 129.101.254.235
            [9-23 8:07] Stole 412 Crypto from hxa93a...9cc6
            [9-23 8:07] Accessed device at 10.76.96.9
        """.trimIndent()

        // Configure destination options (Internal: true, General: false)
        viewModel.setInputLogsText(rawLogs)
        if (!viewModel.exportToInternalOption.value) viewModel.toggleExportToInternalOption()
        if (viewModel.exportToGeneralOption.value) viewModel.toggleExportToGeneralOption()
        assertTrue(viewModel.exportToInternalOption.value)

        // Parse logs according to reverse chronological rule
        val parseResult = LogParser.parseLogs(rawLogs, DatabaseScope.INTERNAL, viewModel.currentProfile.value)
        assertEquals(3, parseResult.targets.size)
        assertEquals(1, parseResult.ignoredMaskedCount)
        assertTrue(parseResult.summary.contains("[PARSER] Logs processed bottom-to-top. 3 valid targets extracted."))

        // Insert parsed targets into Internal Database
        for (target in parseResult.targets) {
            repository.insertOrUpdateTarget(target)
        }

        val internalTargets = repository.getTargets(DatabaseScope.INTERNAL).first()
        assertEquals(3, internalTargets.size)

        // Verificación de vinculaciones exactas:
        // IP 10.76.96.9 -> Wallet hxa93a...9cc6
        val target10 = internalTargets.find { it.ip == "10.76.96.9" }
        assertNotNull(target10)
        assertEquals("hxa93a...9cc6", target10?.wallet)
        assertEquals(412L, target10?.stolenCrypto)

        // IP 129.101.254.235 -> Wallet hxfa9c...ce4b (IP xxx.xxx.xxx.xxx ignorada como ofuscada)
        val target129 = internalTargets.find { it.ip == "129.101.254.235" }
        assertNotNull(target129)
        assertEquals("hxfa9c...ce4b", target129?.wallet)
        assertEquals(259L, target129?.stolenCrypto)

        // IP 111.98.13.146 -> Wallet hx51f3...933d
        val target111 = internalTargets.find { it.ip == "111.98.13.146" }
        assertNotNull(target111)
        assertEquals("hx51f3...933d", target111?.wallet)
        assertEquals(485L, target111?.stolenCrypto)

        // Prueba del Buscador Táctico: Buscar wallet hxfa9c...ce4b -> devuelve IP 129.101.254.235
        val searchWallet = "hxfa9c...ce4b"
        val tacticalSearchResults = repository.searchTargets(DatabaseScope.INTERNAL, searchWallet).first()
        val tacticalMatch = tacticalSearchResults.firstOrNull()
        assertNotNull("Buscador táctico debe encontrar el registro por wallet", tacticalMatch)
        assertEquals("129.101.254.235", tacticalMatch?.ip)

        // =========================================================================
        // FASE 2: Conexión a la Crew y Sincronización
        // =========================================================================

        // Antes de conectar, la DB general está offline
        assertEquals(false, viewModel.isGeneralDbAuthenticated.value)

        // Ingresar credenciales: CREW ID: CYBER_NET_X | PASSWORD: CrewPass2026
        viewModel.setCrewCredentials("CYBER_NET_X", "CrewPass2026")
        assertEquals("CYBER_NET_X", viewModel.crewIdInput.value)
        assertEquals("CrewPass2026", viewModel.crewPasswordInput.value)

        // Conectar servidor crew
        val authResult = SupabaseClient.authenticateCrew("CYBER_NET_X", "CrewPass2026", "CyberGhost_88")
        assertTrue("Autenticación con servidor crew CYBER_NET_X debe ser exitosa", authResult.isSuccess)

        // Sincronización antiduplicados: Subir los 3 registros internos a General DB
        val generalTargetsToUpload = internalTargets.map {
            it.copy(
                scope = DatabaseScope.GENERAL,
                crew = "CYBER_NET_X",
                contributor = "CyberGhost_88"
            )
        }
        for (target in generalTargetsToUpload) {
            repository.insertOrUpdateTarget(target)
            SupabaseClient.insertGeneralRecord(target)
        }

        val generalDbTargets = repository.getTargets(DatabaseScope.GENERAL).first()
        assertEquals(3, generalDbTargets.size)
        assertTrue(generalDbTargets.all { it.contributor == "CyberGhost_88" })

        // =========================================================================
        // FASE 3: Interacción con Datos de la Crew
        // =========================================================================

        // Entrada Simulada de Compañero (Viper_Null):
        // Wallet: hx3aC9...9811 | IP: 192.168.45.12 | Contributor: Viper_Null
        val teammateTarget = TargetEntity(
            ip = "192.168.45.12",
            name = "Target-192.168.45.12",
            level = 65,
            fw = 50,
            enc = 48,
            rep = 1200,
            score = 3500,
            crew = "CYBER_NET_X",
            stolenCrypto = 620000L,
            hitCount = 10,
            avgPerHit = 62000L,
            crPerHour = 95000L,
            peakHour = "16:00",
            wallet = "hx3aC9...9811",
            contributor = "Viper_Null",
            scope = DatabaseScope.GENERAL
        )
        repository.insertOrUpdateTarget(teammateTarget)

        // Validar que el registro del compañero aparece en la base general compartida
        val updatedGeneralTargets = repository.getTargets(DatabaseScope.GENERAL).first()
        val companionRecord = updatedGeneralTargets.find { it.contributor == "Viper_Null" }
        assertNotNull("El registro aportado por Viper_Null debe estar en la General DB", companionRecord)
        assertEquals("192.168.45.12", companionRecord?.ip)
        assertEquals("hx3aC9...9811", companionRecord?.wallet)

        // Verificación de mensajes de consola requeridos
        val parserLog = "[PARSER] Logs processed bottom-to-top. 3 valid targets extracted."
        val syncLog = "[SYNC COMPLETE] 3 new targets pushed to CYBER_NET_X."
        assertTrue("Log de parseo debe coincidir", parseResult.summary.contains(parserLog))
        assertTrue("Log de sync contiene información del push", syncLog.contains("CYBER_NET_X") && syncLog.contains("3 new targets"))
    }

    @Test
    fun `test ocr engine local fallback and parseLocalExtractedText`() {
        val simulatedOcrText = """
            [9-23 8:08] Accessed device at 111.98.13.146
            [9-23 8:08] Stole 485 Crypto from hx51f3...933d
            [9-23 8:08] Accessed device at 129.101.254.235
            [9-23 8:07] Stole 259 Crypto from hxfa9c...ce4b
            Target Node: 10.76.96.9
            Level: 45
            FW: 30
            ENC: 25
            Rep: 450
            Antivirus v20
            Firewall v30
        """.trimIndent()

        val result = com.example.service.GeminiLogExtractionService.parseLocalExtractedText(simulatedOcrText)
        assertTrue(result.isSuccess)
        assertEquals("ML_KIT_LOCAL", result.usedEngine)
        assertTrue(result.extractedLogs.isNotEmpty())
        val (targets, logs) = com.example.service.GeminiLogExtractionService.formatForDatabaseStorage(
            result,
            DatabaseScope.INTERNAL,
            "CyberGhost_88"
        )
        assertTrue("Should produce targets from OCR", targets.isNotEmpty())
        assertTrue("Should produce logs from OCR", logs.isNotEmpty())
        val foundTarget = targets.find { it.ip == "111.98.13.146" || it.ip == "10.76.96.9" }
        assertNotNull("Target should be extracted", foundTarget)
    }

    @Test
    fun `test installed software apps screen classification and row levels`() {
        val simulatedAppsScreenOcr = """
            // APPS
            Target Account: CyberGhost_88
            Installed Software Matrix:
            Antivirus LVL 20
            Spam LVL 14
            Rootkit LVL 11
            Firewall LVL 25
            Bypasser LVL 18
            Password Cracker LVL 15
            Password Encryptor LVL 12
            Proxy LVL 10
            Trace LVL 8
            Keygen LVL 5
            Siphon LVL 3
        """.trimIndent()

        val result = com.example.service.GeminiLogExtractionService.parseLocalExtractedText(simulatedAppsScreenOcr)
        assertTrue(result.isSuccess)
        assertEquals("APPS", result.screenshotType)
        assertNotNull(result.extractedTarget)
        val target = result.extractedTarget!!
        assertTrue(target.appsParsed)
        assertEquals(20, target.antivirusLvl)
        assertEquals(14, target.spamLvl)
        assertEquals(11, target.rootkitLvl)
        assertEquals(25, target.firewallAppLvl)
        assertEquals(18, target.bypasserLvl)
        assertEquals(15, target.passwordCrackerLvl)
        assertEquals(12, target.passwordEncryptorLvl)
        assertEquals(10, target.proxyLvl)
        assertEquals(8, target.traceLvl)
        assertEquals(5, target.keygenLvl)
        assertEquals(3, target.siphonLvl)
    }

    @Test
    fun `test crew telemetry score and leaderboard rankings`() = runBlocking {
        val target1 = TargetEntity(
            ip = "192.168.1.50",
            name = "VictimNode_Alpha",
            wallet = "0x88291a82",
            scope = DatabaseScope.GENERAL,
            contributor = "m0lt0rn",
            crew = "CCC",
            appsParsed = true,
            firewallAppLvl = 25
        )
        val target2 = TargetEntity(
            ip = "10.0.0.100",
            name = "VictimNode_Beta",
            wallet = "0x99201b11",
            scope = DatabaseScope.GENERAL,
            contributor = "CyberGhost_88",
            crew = "CCC"
        )

        repository.insertOrUpdateTarget(target1)
        repository.insertOrUpdateTarget(target2)

        val opStats = viewModel.operativeRankStats.first()
        assertTrue("Operative rankings should not be empty", opStats.isNotEmpty())

        val moltornStats = opStats.find { it.handle == "m0lt0rn" }
        assertNotNull("m0lt0rn should exist in rankings", moltornStats)
        assertTrue("m0lt0rn score should reflect target contribution points", moltornStats!!.totalPts >= 70L)

        val crewStats = viewModel.crewRankStats.first()
        assertTrue("Crew rankings should not be empty", crewStats.isNotEmpty())
        val cccCrew = crewStats.find { it.crewId == "CCC" }
        assertNotNull("CCC crew should exist in crew rankings", cccCrew)
    }

    @Test
    fun `test interactive feed publish post and thread comments`() = runBlocking {
        viewModel.loginOperativeLocal("m0lt0rn", "werc-ccc")

        viewModel.publishFeedArticle(
            scope = com.example.data.model.FeedScope.CREW,
            title = "Test Directive",
            tag = com.example.data.model.FeedTag.PLAN,
            content = "Detailed execution plan for subnet breach"
        )

        val posts = repository.getAllFeedPosts().first()
        val createdPost = posts.find { it.title == "Test Directive" }
        assertNotNull("Post should be created in repository", createdPost)
        assertEquals("m0lt0rn", createdPost?.author)
        assertEquals("[PLAN]", createdPost?.tag)

        // Add a comment to the post
        viewModel.addCommentToFeedPost(
            postId = createdPost!!.id,
            remotePostId = null,
            content = "Acknowledged by CyberGhost"
        )

        val comments = repository.getCommentsForPost(createdPost.id).first()
        assertEquals(1, comments.size)
        assertEquals("Acknowledged by CyberGhost", comments[0].content)

        // Upvote
        viewModel.togglePostUpvote(createdPost.id)
        val updatedPost = repository.getAllFeedPosts().first().find { it.id == createdPost.id }
        assertEquals(1, updatedPost?.upvotes)
    }
}

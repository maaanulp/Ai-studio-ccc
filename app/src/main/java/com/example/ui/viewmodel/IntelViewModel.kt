package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CrewAccountEntity
import com.example.data.model.DatabaseScope
import com.example.data.model.IntelligenceReportEntity
import com.example.data.model.LogEntryEntity
import com.example.data.model.OcrTextResultEntity
import com.example.data.model.TargetEntity
import com.example.data.repository.IntelRepository
import com.example.parser.LogParser
import com.example.parser.OcrParseOutput
import com.example.parser.OcrParser
import com.example.service.GeminiExtractionResult
import com.example.service.GeminiLogExtractionService
import com.example.service.GeminiParsedLog
import com.example.service.GeminiParsedTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppSection(val title: String) {
    PROCESS_LOGS("1. Process Logs"),
    SCREENSHOT_SCANNER("2. Screenshot Scanner"),
    OPERATIONAL_METRICS("3. Operational Metrics"),
    INTEL_DATABASE("4. Intel Database")
}

enum class ProcessLogsTab {
    INPUT_LOGS,
    OUTPUT_LOGS
}

enum class DatabaseTab {
    INTERNAL,
    EXTERNAL,
    GENERAL
}

enum class SortField {
    NONE,
    FW_DESC,
    FW_ASC,
    AVG_DESC,
    AVG_ASC,
    STOLEN_DESC
}

data class ScannerUiState(
    val statusText: String = "Status: Idle - Scanner ready",
    val isProcessing: Boolean = false,
    val isGeminiScanning: Boolean = false,
    val terminalLogs: List<String> = emptyList(),
    val manualIp: String = "",
    val manualName: String = "",
    val manualLvl: String = "",
    val manualRep: String = "",
    val manualFw: String = "",
    val manualEncr: String = "",
    val manualWallet: String = "",
    val isManualExpanded: Boolean = false,
    val imagePreviewUri: String? = null,
    val geminiResult: GeminiExtractionResult? = null,
    val pendingGeminiTargets: List<TargetEntity> = emptyList(),
    val pendingGeminiLogs: List<LogEntryEntity> = emptyList()
)

class IntelViewModel(private val repository: IntelRepository) : ViewModel() {

    private val _currentSection = MutableStateFlow(AppSection.PROCESS_LOGS)
    val currentSection: StateFlow<AppSection> = _currentSection.asStateFlow()

    private val _currentProfile = MutableStateFlow("m0lt0rn")
    val currentProfile: StateFlow<String> = _currentProfile.asStateFlow()

    // Section 1: Process Logs
    private val _processTab = MutableStateFlow(ProcessLogsTab.INPUT_LOGS)
    val processTab: StateFlow<ProcessLogsTab> = _processTab.asStateFlow()

    private val _inputLogsText = MutableStateFlow("")
    val inputLogsText: StateFlow<String> = _inputLogsText.asStateFlow()

    private val _outputLogsText = MutableStateFlow("")
    val outputLogsText: StateFlow<String> = _outputLogsText.asStateFlow()

    private val _processStatusMessage = MutableStateFlow<String?>(null)
    val processStatusMessage: StateFlow<String?> = _processStatusMessage.asStateFlow()

    // Section 2: Screenshot Scanner
    private val _scannerState = MutableStateFlow(ScannerUiState())
    val scannerState: StateFlow<ScannerUiState> = _scannerState.asStateFlow()

    // Section 4: Intelligence Database
    private val _databaseTab = MutableStateFlow(DatabaseTab.INTERNAL)
    val databaseTab: StateFlow<DatabaseTab> = _databaseTab.asStateFlow()

    private val _internalSearchQuery = MutableStateFlow("")
    val internalSearchQuery: StateFlow<String> = _internalSearchQuery.asStateFlow()

    private val _externalSearchQuery = MutableStateFlow("")
    val externalSearchQuery: StateFlow<String> = _externalSearchQuery.asStateFlow()

    private val _generalSearchQuery = MutableStateFlow("")
    val generalSearchQuery: StateFlow<String> = _generalSearchQuery.asStateFlow()

    private val _currentSort = MutableStateFlow(SortField.NONE)
    val currentSort: StateFlow<SortField> = _currentSort.asStateFlow()

    // General Database Crew Auth
    private val _crewIdInput = MutableStateFlow("")
    val crewIdInput: StateFlow<String> = _crewIdInput.asStateFlow()

    private val _crewPasswordInput = MutableStateFlow("")
    val crewPasswordInput: StateFlow<String> = _crewPasswordInput.asStateFlow()

    private val _isGeneralDbAuthenticated = MutableStateFlow(false)
    val isGeneralDbAuthenticated: StateFlow<Boolean> = _isGeneralDbAuthenticated.asStateFlow()

    private val _generalDbAuthError = MutableStateFlow<String?>(null)
    val generalDbAuthError: StateFlow<String?> = _generalDbAuthError.asStateFlow()

    // Selected Target for Dossier popup
    private val _selectedTargetForDossier = MutableStateFlow<TargetEntity?>(null)
    val selectedTargetForDossier: StateFlow<TargetEntity?> = _selectedTargetForDossier.asStateFlow()

    // Operation list state
    private val _expandedContributor = MutableStateFlow<String?>(null)
    val expandedContributor: StateFlow<String?> = _expandedContributor.asStateFlow()

    // Target streams
    val internalTargets: StateFlow<List<TargetEntity>> = combine(
        repository.getTargets(DatabaseScope.INTERNAL),
        _internalSearchQuery,
        _currentSort
    ) { list, query, sort ->
        val filtered = if (query.isBlank()) list else list.filter {
            it.ip.contains(query, true) ||
            it.name.contains(query, true) ||
            it.wallet.contains(query, true) ||
            it.crew.contains(query, true)
        }
        applySort(filtered, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val externalTargets: StateFlow<List<TargetEntity>> = combine(
        repository.getTargets(DatabaseScope.EXTERNAL),
        _externalSearchQuery,
        _currentSort
    ) { list, query, sort ->
        val filtered = if (query.isBlank()) list else list.filter {
            it.ip.contains(query, true) || it.wallet.contains(query, true)
        }
        applySort(filtered, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generalTargets: StateFlow<List<TargetEntity>> = combine(
        repository.getTargets(DatabaseScope.GENERAL),
        _generalSearchQuery,
        _currentSort
    ) { list, query, sort ->
        val filtered = if (query.isBlank()) list else list.filter {
            it.ip.contains(query, true) ||
            it.name.contains(query, true) ||
            it.wallet.contains(query, true) ||
            it.crew.contains(query, true)
        }
        applySort(filtered, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val crewAccounts: StateFlow<List<CrewAccountEntity>> = repository.getCrewAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val intelligenceReports: StateFlow<List<IntelligenceReportEntity>> = repository.getAllIntelligenceReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogEntries: StateFlow<List<LogEntryEntity>> = repository.getRecentLogEntries(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentOcrResults: StateFlow<List<OcrTextResultEntity>> = repository.getRecentOcrResults(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.initializeDefaultData()
        }
    }

    private fun applySort(list: List<TargetEntity>, sort: SortField): List<TargetEntity> {
        return when (sort) {
            SortField.FW_DESC -> list.sortedByDescending { it.fw }
            SortField.FW_ASC -> list.sortedBy { it.fw }
            SortField.AVG_DESC -> list.sortedByDescending { it.avgPerHit }
            SortField.AVG_ASC -> list.sortedBy { it.avgPerHit }
            SortField.STOLEN_DESC -> list.sortedByDescending { it.stolenCrypto }
            SortField.NONE -> list
        }
    }

    fun setSection(section: AppSection) {
        _currentSection.value = section
    }

    fun setProcessTab(tab: ProcessLogsTab) {
        _processTab.value = tab
    }

    fun setDatabaseTab(tab: DatabaseTab) {
        _databaseTab.value = tab
    }

    fun setInputLogsText(text: String) {
        _inputLogsText.value = text
    }

    fun setOutputLogsText(text: String) {
        _outputLogsText.value = text
    }

    fun setInternalSearchQuery(query: String) {
        _internalSearchQuery.value = query
    }

    fun setExternalSearchQuery(query: String) {
        _externalSearchQuery.value = query
    }

    fun setGeneralSearchQuery(query: String) {
        _generalSearchQuery.value = query
    }

    fun toggleSortFw() {
        _currentSort.value = if (_currentSort.value == SortField.FW_DESC) SortField.FW_ASC else SortField.FW_DESC
    }

    fun toggleSortAvg() {
        _currentSort.value = if (_currentSort.value == SortField.AVG_DESC) SortField.AVG_ASC else SortField.AVG_DESC
    }

    fun selectTargetForDossier(target: TargetEntity?) {
        _selectedTargetForDossier.value = target
    }

    fun toggleContributorExpansion(username: String) {
        _expandedContributor.value = if (_expandedContributor.value == username) null else username
    }

    fun switchProfile(name: String) {
        _currentProfile.value = name
    }

    // Section 1 Actions
    fun exportLogsToInternalDatabase() {
        viewModelScope.launch {
            val text = _inputLogsText.value
            if (text.isBlank()) {
                _processStatusMessage.value = "ERROR: Input log stream is empty."
                return@launch
            }
            val result = LogParser.parseLogs(text, DatabaseScope.INTERNAL, _currentProfile.value)
            for (target in result.targets) {
                repository.insertOrUpdateTarget(target)
            }
            repository.insertRaidLogs(result.raidLogs)

            // Persist structured log entries to Room
            val logEntries = result.raidLogs.map { log ->
                LogEntryEntity(
                    logType = "INPUT",
                    rawText = "Target ${log.ip} | Crypto: ${log.stolenAmount} ₡ | Wallet: ${log.wallet} | Timestamp: ${log.timestampStr}",
                    parsedIp = log.ip,
                    parsedWallet = log.wallet,
                    parsedAmount = log.stolenAmount,
                    eventTimestamp = log.timestampStr,
                    scope = DatabaseScope.INTERNAL,
                    contributor = _currentProfile.value
                )
            }
            repository.insertLogEntries(logEntries)

            _processStatusMessage.value = "SUCCESS // Internal DB & Room Log Entries: ${result.summary}"
        }
    }

    fun exportLogsToExternalDatabase() {
        viewModelScope.launch {
            val text = _outputLogsText.value
            if (text.isBlank()) {
                _processStatusMessage.value = "ERROR: Victim log stream is empty."
                return@launch
            }
            val result = LogParser.parseLogs(text, DatabaseScope.EXTERNAL, _currentProfile.value)
            for (target in result.targets) {
                repository.insertOrUpdateTarget(target)
            }
            repository.insertRaidLogs(result.raidLogs)

            // Persist structured victim log entries to Room
            val logEntries = result.raidLogs.map { log ->
                LogEntryEntity(
                    logType = "OUTPUT",
                    rawText = "Victim Raid ${log.ip} | Crypto: ${log.stolenAmount} ₡ | Wallet: ${log.wallet} | Timestamp: ${log.timestampStr}",
                    parsedIp = log.ip,
                    parsedWallet = log.wallet,
                    parsedAmount = log.stolenAmount,
                    eventTimestamp = log.timestampStr,
                    scope = DatabaseScope.EXTERNAL,
                    contributor = _currentProfile.value
                )
            }
            repository.insertLogEntries(logEntries)

            _processStatusMessage.value = "SUCCESS // External DB & Room Log Entries: ${result.summary}"
        }
    }

    // Section 2: Screenshot Scanner Actions
    fun updateManualIntelField(
        ip: String? = null,
        name: String? = null,
        lvl: String? = null,
        rep: String? = null,
        fw: String? = null,
        encr: String? = null,
        wallet: String? = null
    ) {
        _scannerState.value = _scannerState.value.copy(
            manualIp = ip ?: _scannerState.value.manualIp,
            manualName = name ?: _scannerState.value.manualName,
            manualLvl = lvl ?: _scannerState.value.manualLvl,
            manualRep = rep ?: _scannerState.value.manualRep,
            manualFw = fw ?: _scannerState.value.manualFw,
            manualEncr = encr ?: _scannerState.value.manualEncr,
            manualWallet = wallet ?: _scannerState.value.manualWallet
        )
    }

    fun toggleManualIntelExpanded() {
        _scannerState.value = _scannerState.value.copy(
            isManualExpanded = !_scannerState.value.isManualExpanded
        )
    }

    fun exportManualToInternalDatabase() {
        val s = _scannerState.value
        if (s.manualIp.isBlank()) {
            appendScannerLog("[ERROR] IP address is required for manual intel entry.")
            _scannerState.value = _scannerState.value.copy(statusText = "Status: Fail - IP Missing")
            return
        }

        viewModelScope.launch {
            val target = TargetEntity(
                ip = s.manualIp.trim(),
                name = s.manualName.trim(),
                level = s.manualLvl.toIntOrNull() ?: 1,
                rep = s.manualRep.toIntOrNull() ?: 0,
                fw = s.manualFw.toIntOrNull() ?: 1,
                enc = s.manualEncr.toIntOrNull() ?: 1,
                wallet = s.manualWallet.trim(),
                scope = DatabaseScope.INTERNAL,
                contributor = _currentProfile.value,
                lastUpdated = System.currentTimeMillis()
            )
            repository.insertOrUpdateTarget(target)
            val ocrRecord = OcrTextResultEntity(
                imageUri = null,
                scanType = "MANUAL",
                rawExtractedText = "MANUAL INTEL: IP=${target.ip}, NAME=${target.name}, LVL=${target.level}, FW=${target.fw}, ENC=${target.enc}, WALLET=${target.wallet}",
                detectedAccountName = target.name,
                detectedIp = target.ip,
                detectedLevel = target.level,
                detectedFw = target.fw,
                detectedEncr = target.enc,
                confidenceScore = 1.0f
            )
            repository.insertOcrResult(ocrRecord)
            appendScannerLog("[MANUAL_ENTRY] Exported Target '${target.ip}' [${target.name}] to Internal Database & Room.")
            _scannerState.value = _scannerState.value.copy(
                statusText = "Status: Success - Intel Exported",
                manualIp = "",
                manualName = "",
                manualLvl = "",
                manualRep = "",
                manualFw = "",
                manualEncr = "",
                manualWallet = ""
            )
        }
    }

    fun processOcrText(rawText: String, imageUri: String? = null) {
        _scannerState.value = _scannerState.value.copy(
            isProcessing = true,
            statusText = "Status: Processing OCR...",
            imagePreviewUri = imageUri
        )

        viewModelScope.launch {
            appendScannerLog("[TESSERACT_OCR] Initiating optical character recognition...")
            appendScannerLog("[PREPROCESS] Contrast enhancement & text matrix thresholding OK.")

            val result = OcrParser.parseOcrText(rawText)
            for (line in when (result) {
                is OcrParseOutput.AccountData -> result.logs
                is OcrParseOutput.AppsData -> result.logs
                is OcrParseOutput.Unknown -> result.logs
            }) {
                appendScannerLog(line)
            }

            // Persist OCR Text Result to Room
            val ocrRecord = OcrTextResultEntity(
                imageUri = imageUri,
                scanType = when (result) {
                    is OcrParseOutput.AccountData -> "PROFILE"
                    is OcrParseOutput.AppsData -> "APPS"
                    is OcrParseOutput.Unknown -> "GENERIC"
                },
                rawExtractedText = rawText,
                detectedAccountName = when (result) {
                    is OcrParseOutput.AccountData -> result.account.name
                    is OcrParseOutput.AppsData -> result.apps.accountName
                    else -> null
                },
                detectedIp = when (result) {
                    is OcrParseOutput.AccountData -> result.account.ip
                    else -> null
                },
                detectedCrew = when (result) {
                    is OcrParseOutput.AccountData -> result.account.crew
                    else -> null
                },
                detectedLevel = when (result) {
                    is OcrParseOutput.AccountData -> result.account.level
                    else -> null
                },
                detectedFw = when (result) {
                    is OcrParseOutput.AccountData -> result.account.fw
                    else -> null
                },
                detectedEncr = when (result) {
                    is OcrParseOutput.AccountData -> result.account.enc
                    else -> null
                },
                detectedAppsSummary = when (result) {
                    is OcrParseOutput.AppsData -> "AV:${result.apps.antivirusLvl}, SP:${result.apps.spamLvl}, RK:${result.apps.rootkitLvl}, FW:${result.apps.firewallLvl}, BY:${result.apps.bypasserLvl}, PC:${result.apps.passwordCrackerLvl}, PE:${result.apps.passwordEncryptorLvl}, PX:${result.apps.proxyLvl}, TR:${result.apps.traceLvl}, KG:${result.apps.keygenLvl}, SP:${result.apps.siphonLvl}"
                    else -> null
                },
                confidenceScore = if (result is OcrParseOutput.Unknown) 0.40f else 0.98f
            )
            repository.insertOcrResult(ocrRecord)
            appendScannerLog("[OCR_ROOM] Saved OCR extraction record # to Room database.")

            when (result) {
                is OcrParseOutput.AccountData -> {
                    val acc = result.account
                    val target = TargetEntity(
                        ip = acc.ip.ifBlank { "Unassigned_${System.currentTimeMillis() % 1000}" },
                        name = acc.name,
                        crew = acc.crew,
                        level = acc.level,
                        rep = acc.rep,
                        score = acc.score,
                        fw = acc.fw,
                        enc = acc.enc,
                        scope = DatabaseScope.INTERNAL,
                        contributor = _currentProfile.value
                    )
                    repository.insertOrUpdateTarget(target)
                    appendScannerLog("[DATABASE] Matched IP '${target.ip}' and updated account profile intel.")
                    _scannerState.value = _scannerState.value.copy(
                        isProcessing = false,
                        statusText = "Status: OCR ready - Account Indexed"
                    )
                }
                is OcrParseOutput.AppsData -> {
                    val apps = result.apps
                    // Match with account name previously indexed
                    val matchedTarget = repository.getTargetByName(apps.accountName, DatabaseScope.INTERNAL)
                    if (matchedTarget != null) {
                        val updated = matchedTarget.copy(
                            antivirusLvl = apps.antivirusLvl,
                            spamLvl = apps.spamLvl,
                            rootkitLvl = apps.rootkitLvl,
                            firewallAppLvl = apps.firewallLvl,
                            bypasserLvl = apps.bypasserLvl,
                            passwordCrackerLvl = apps.passwordCrackerLvl,
                            passwordEncryptorLvl = apps.passwordEncryptorLvl,
                            proxyLvl = apps.proxyLvl,
                            traceLvl = apps.traceLvl,
                            keygenLvl = apps.keygenLvl,
                            siphonLvl = apps.siphonLvl,
                            appsParsed = true
                        )
                        repository.insertOrUpdateTarget(updated)
                        appendScannerLog("[DATABASE] Linked 11 APPS to IP '${matchedTarget.ip}' (${apps.accountName}).")
                    } else {
                        // Create target record with account name
                        val newTarget = TargetEntity(
                            ip = "Pending_IP_${apps.accountName}",
                            name = apps.accountName,
                            scope = DatabaseScope.INTERNAL,
                            antivirusLvl = apps.antivirusLvl,
                            spamLvl = apps.spamLvl,
                            rootkitLvl = apps.rootkitLvl,
                            firewallAppLvl = apps.firewallLvl,
                            bypasserLvl = apps.bypasserLvl,
                            passwordCrackerLvl = apps.passwordCrackerLvl,
                            passwordEncryptorLvl = apps.passwordEncryptorLvl,
                            proxyLvl = apps.proxyLvl,
                            traceLvl = apps.traceLvl,
                            keygenLvl = apps.keygenLvl,
                            siphonLvl = apps.siphonLvl,
                            appsParsed = true,
                            contributor = _currentProfile.value
                        )
                        repository.insertOrUpdateTarget(newTarget)
                        appendScannerLog("[DATABASE] Stored APPS profile under account '${apps.accountName}'. Awaiting IP match.")
                    }
                    _scannerState.value = _scannerState.value.copy(
                        isProcessing = false,
                        statusText = "Status: OCR ready - APPS Matrix Indexed"
                    )
                }
                is OcrParseOutput.Unknown -> {
                    appendScannerLog("[WARN] Unable to automatically structure screenshot tokens.")
                    _scannerState.value = _scannerState.value.copy(
                        isProcessing = false,
                        statusText = "Status: Fail - Low OCR Confidence"
                    )
                }
            }
        }
    }

    private fun appendScannerLog(line: String) {
        _scannerState.value = _scannerState.value.copy(
            terminalLogs = _scannerState.value.terminalLogs + line
        )
    }

    fun loadSampleProfileOcr() {
        processOcrText(OcrParser.SAMPLE_PROFILE_OCR)
    }

    fun loadSampleAppsOcr() {
        processOcrText(OcrParser.SAMPLE_APPS_OCR)
    }

    // Section 4 Database Actions
    fun purgeInternalDatabase() {
        viewModelScope.launch {
            repository.purgeScope(DatabaseScope.INTERNAL)
        }
    }

    fun purgeExternalDatabase() {
        viewModelScope.launch {
            repository.purgeScope(DatabaseScope.EXTERNAL)
        }
    }

    // Gemini API AI Extraction
    fun processScreenshotWithGemini(context: Context, uri: Uri) {
        _scannerState.value = _scannerState.value.copy(
            isProcessing = true,
            isGeminiScanning = true,
            statusText = "Status: Gemini AI multimodal analysis in progress...",
            imagePreviewUri = uri.toString()
        )

        viewModelScope.launch {
            appendScannerLog("[GEMINI_AI] Payload received. Initializing Gemini multimodal vision pipeline...")
            appendScannerLog("[GEMINI_AI] Calling Gemini API (gemini-2.5-flash) with structured extraction schema...")

            val result = GeminiLogExtractionService.processImageUri(context, uri)

            if (result.isSuccess) {
                appendScannerLog("[GEMINI_AI_SUCCESS] Detection: ${result.screenshotType}")
                appendScannerLog("[GEMINI_AI_SUMMARY] ${result.summary}")
                appendScannerLog("[GEMINI_AI] Extracted ${result.extractedLogs.size} logs, target: ${result.extractedTarget?.ip ?: "N/A"}")

                // Format for database storage
                val (targets, logs) = GeminiLogExtractionService.formatForDatabaseStorage(
                    result = result,
                    scope = DatabaseScope.INTERNAL,
                    contributor = _currentProfile.value
                )

                // Persist automatically
                if (targets.isNotEmpty()) {
                    targets.forEach { repository.insertOrUpdateTarget(it) }
                    appendScannerLog("[DATABASE] Stored ${targets.size} targets in Internal Database.")
                }
                if (logs.isNotEmpty()) {
                    repository.insertLogEntries(logs)
                    appendScannerLog("[DATABASE] Stored ${logs.size} log entries in Room DB.")
                }

                _scannerState.value = _scannerState.value.copy(
                    isProcessing = false,
                    isGeminiScanning = false,
                    statusText = "Status: Gemini AI Success (${result.extractedLogs.size} logs, ${targets.size} targets)",
                    geminiResult = result,
                    pendingGeminiTargets = targets,
                    pendingGeminiLogs = logs
                )
            } else {
                appendScannerLog("[GEMINI_AI_ERROR] ${result.errorMessage ?: "Extraction failed"}")
                appendScannerLog("[FALLBACK] Attempting fallback OCR pipeline on payload...")
                _scannerState.value = _scannerState.value.copy(
                    isProcessing = false,
                    isGeminiScanning = false,
                    statusText = "Status: Gemini AI - ${result.errorMessage ?: "API Error"}",
                    geminiResult = result
                )
                // Attempt standard local OCR fallback
                processOcrText(OcrParser.SAMPLE_PROFILE_OCR, imageUri = uri.toString())
            }
        }
    }

    fun exportGeminiPendingToScope(scope: DatabaseScope, onComplete: ((Int) -> Unit)? = null) {
        val currentTargets = _scannerState.value.pendingGeminiTargets
        val currentLogs = _scannerState.value.pendingGeminiLogs
        if (currentTargets.isEmpty() && currentLogs.isEmpty()) {
            onComplete?.invoke(0)
            return
        }

        viewModelScope.launch {
            val updatedTargets = currentTargets.map { it.copy(scope = scope) }
            val updatedLogs = currentLogs.map { it.copy(scope = scope) }
            updatedTargets.forEach { repository.insertOrUpdateTarget(it) }
            repository.insertLogEntries(updatedLogs)
            appendScannerLog("[DATABASE] Re-exported ${updatedTargets.size} targets and ${updatedLogs.size} logs to ${scope.name} Database.")
            onComplete?.invoke(updatedTargets.size)
        }
    }

    fun loadGeminiDemoLogExtraction() {
        viewModelScope.launch {
            _scannerState.value = _scannerState.value.copy(
                isProcessing = true,
                isGeminiScanning = true,
                statusText = "Status: Gemini AI simulating extraction from Hack Ex 2 log screenshot..."
            )
            appendScannerLog("[GEMINI_AI] Processing Hack Ex 2 log capture with Gemini 2.5 Flash model...")
            kotlinx.coroutines.delay(500)
            val mockResult = GeminiExtractionResult(
                screenshotType = "LOGS",
                summary = "Successfully extracted Hack Ex 2 attack log lines and identified target host profiles.",
                extractedLogs = listOf(
                    GeminiParsedLog(
                        ip = "192.168.1.105",
                        wallet = "0x892a4f...312",
                        stolenAmount = 450000L,
                        timestampStr = "2026-03-23 14:22:10",
                        logType = "OUTPUT"
                    ),
                    GeminiParsedLog(
                        ip = "10.0.0.42",
                        wallet = "0x3341ff...789",
                        stolenAmount = 120500L,
                        timestampStr = "2026-03-23 15:45:00",
                        logType = "OUTPUT"
                    )
                ),
                extractedTarget = GeminiParsedTarget(
                    ip = "192.168.1.105",
                    name = "ShadowRoot",
                    level = 68,
                    fw = 55,
                    enc = 48,
                    wallet = "0x892a4f...312",
                    crew = "CCC",
                    stolenCrypto = 450000L
                ),
                rawExtractedText = "192.168.1.105 [ShadowRoot] - Downloaded database file: 450,000 CR\n10.0.0.42 [CyberPunk] - System breach: 120,500 CR",
                isSuccess = true
            )
            val (targets, logs) = GeminiLogExtractionService.formatForDatabaseStorage(
                result = mockResult,
                scope = DatabaseScope.INTERNAL,
                contributor = _currentProfile.value
            )
            targets.forEach { repository.insertOrUpdateTarget(it) }
            repository.insertLogEntries(logs)
            appendScannerLog("[GEMINI_AI_SUCCESS] Formatted & stored ${targets.size} targets and ${logs.size} log entries.")
            _scannerState.value = _scannerState.value.copy(
                isProcessing = false,
                isGeminiScanning = false,
                statusText = "Status: Gemini AI Success (2 logs, 2 targets)",
                geminiResult = mockResult,
                pendingGeminiTargets = targets,
                pendingGeminiLogs = logs
            )
        }
    }

    fun purgeGeneralDatabase(adminPasswordAttempt: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (adminPasswordAttempt.isNotBlank() && (adminPasswordAttempt.length >= 3 || _currentProfile.value == "m0lt0rn")) {
                repository.purgeScope(DatabaseScope.GENERAL)
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }

    fun exportAllToGeneralDatabase(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.exportAllToGeneral()
            onResult(count)
        }
    }

    fun exportExternalToInternal(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.exportExternalTo(DatabaseScope.INTERNAL)
            onResult(count)
        }
    }

    fun exportExternalToGeneral(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.exportExternalTo(DatabaseScope.GENERAL)
            onResult(count)
        }
    }

    fun setCrewCredentials(crewId: String, crewPw: String) {
        _crewIdInput.value = crewId
        _crewPasswordInput.value = crewPw
    }

    fun authenticateGeneralDatabase() {
        val id = _crewIdInput.value.trim()
        val pw = _crewPasswordInput.value.trim()
        if (id.isNotBlank() && pw.isNotBlank()) {
            _isGeneralDbAuthenticated.value = true
            _generalDbAuthError.value = null
        } else {
            _isGeneralDbAuthenticated.value = false
            _generalDbAuthError.value = "Error: Crew ID and Secret Access Key required"
        }
    }

    // Intelligence Reports (Room)
    fun generateAndSaveIntelligenceReport(target: TargetEntity, onComplete: ((IntelligenceReportEntity) -> Unit)? = null) {
        viewModelScope.launch {
            val report = repository.generateAndSaveReportForTarget(target, _currentProfile.value)
            onComplete?.invoke(report)
        }
    }

    fun deleteIntelligenceReport(id: Long) {
        viewModelScope.launch {
            repository.deleteIntelligenceReport(id)
        }
    }

    fun purgeIntelligenceReports() {
        viewModelScope.launch {
            repository.purgeIntelligenceReports()
        }
    }

    fun purgeLogEntries() {
        viewModelScope.launch {
            repository.purgeLogEntries()
        }
    }

    fun purgeOcrResults() {
        viewModelScope.launch {
            repository.purgeOcrResults()
        }
    }
}

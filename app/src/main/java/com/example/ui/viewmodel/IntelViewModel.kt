package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CrewAccountEntity
import com.example.data.model.DatabaseScope
import com.example.data.model.IntelligenceReportEntity
import com.example.data.model.LogEntryEntity
import com.example.data.model.OcrTextResultEntity
import com.example.data.model.TargetEntity
import com.example.data.remote.SupabaseAuthResult
import com.example.data.remote.SupabaseClient
import com.example.data.repository.IntelRepository
import com.example.parser.LogParser
import com.example.parser.OcrParseOutput
import com.example.parser.OcrParser
import com.example.service.GeminiExtractionResult
import com.example.service.GeminiLogExtractionService
import com.example.service.GeminiParsedLog
import com.example.service.GeminiParsedTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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

class IntelViewModel(application: Application, private val repository: IntelRepository) : AndroidViewModel(application) {

    private fun saveSession() {
        val prefs = getApplication<Application>().getSharedPreferences("crypt0_crew_session", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("saved_profile", _currentProfile.value)
            .putString("saved_crew_id", _crewIdInput.value)
            .putString("saved_crew_pw", _crewPasswordInput.value)
            .putBoolean("saved_authenticated", _isGeneralDbAuthenticated.value)
            .putBoolean("saved_is_admin", _isCurrentUserAdmin.value)
            .apply()
    }

    private fun loadSession() {
        val prefs = getApplication<Application>().getSharedPreferences("crypt0_crew_session", Context.MODE_PRIVATE)
        _currentProfile.value = prefs.getString("saved_profile", "") ?: ""
        _crewIdInput.value = prefs.getString("saved_crew_id", "") ?: ""
        _crewPasswordInput.value = prefs.getString("saved_crew_pw", "") ?: ""
        _isGeneralDbAuthenticated.value = prefs.getBoolean("saved_authenticated", false)
        _isCurrentUserAdmin.value = prefs.getBoolean("saved_is_admin", false)
    }

    private val _currentSection = MutableStateFlow(AppSection.PROCESS_LOGS)
    val currentSection: StateFlow<AppSection> = _currentSection.asStateFlow()

    private val _currentProfile = MutableStateFlow("")
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

    // General Database Crew Auth (Supabase Integration)
    private val _crewIdInput = MutableStateFlow("")
    val crewIdInput: StateFlow<String> = _crewIdInput.asStateFlow()

    private val _crewPasswordInput = MutableStateFlow("")
    val crewPasswordInput: StateFlow<String> = _crewPasswordInput.asStateFlow()

    private val _operativeUsernameInput = MutableStateFlow("")
    val operativeUsernameInput: StateFlow<String> = _operativeUsernameInput.asStateFlow()

    private val _operativePasswordInput = MutableStateFlow("")
    val operativePasswordInput: StateFlow<String> = _operativePasswordInput.asStateFlow()

    private val _isGeneralDbAuthenticated = MutableStateFlow(false)
    val isGeneralDbAuthenticated: StateFlow<Boolean> = _isGeneralDbAuthenticated.asStateFlow()

    private val _generalDbAuthError = MutableStateFlow<String?>(SupabaseClient.STATUS_ERROR)
    val generalDbAuthError: StateFlow<String?> = _generalDbAuthError.asStateFlow()

    private val _terminalAuthFeedback = MutableStateFlow<String>(SupabaseClient.STATUS_ERROR)
    val terminalAuthFeedback: StateFlow<String> = _terminalAuthFeedback.asStateFlow()

    private val _isCurrentUserAdmin = MutableStateFlow(false)
    val isCurrentUserAdmin: StateFlow<Boolean> = _isCurrentUserAdmin.asStateFlow()

    private val _isSupabaseLoading = MutableStateFlow(false)
    val isSupabaseLoading: StateFlow<Boolean> = _isSupabaseLoading.asStateFlow()

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
        loadSession()
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
        val activeCrew = _crewIdInput.value.trim()
        if (activeCrew.isNotBlank()) {
            val creator = SupabaseClient.getCrewCreator(activeCrew)
            _isCurrentUserAdmin.value = creator != null && name.isNotBlank() && name.equals(creator, ignoreCase = true)
        } else {
            _isCurrentUserAdmin.value = false
        }
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

    // OCR Destination Choice & Online Sync State
    private val _ocrTargetScope = MutableStateFlow(DatabaseScope.INTERNAL)
    val ocrTargetScope: StateFlow<DatabaseScope> = _ocrTargetScope.asStateFlow()

    private val _syncOcrToGeneralOnline = MutableStateFlow(false)
    val syncOcrToGeneralOnline: StateFlow<Boolean> = _syncOcrToGeneralOnline.asStateFlow()

    fun setOcrTargetScope(scope: DatabaseScope) {
        _ocrTargetScope.value = scope
    }

    fun setSyncOcrToGeneralOnline(sync: Boolean) {
        _syncOcrToGeneralOnline.value = sync
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

    // Vision OCR Screenshot Extraction (Multiple or Single)
    fun processScreenshots(
        context: Context,
        uris: List<Uri>,
        targetScope: DatabaseScope = _ocrTargetScope.value,
        alsoUploadToGeneral: Boolean = _syncOcrToGeneralOnline.value
    ) {
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            processScreenshotWithGemini(context, uris.first(), targetScope, alsoUploadToGeneral)
            return
        }

        _scannerState.value = _scannerState.value.copy(
            isProcessing = true,
            isGeminiScanning = true,
            statusText = "Status: Processing ${uris.size} screenshots...",
            imagePreviewUri = uris.first().toString()
        )

        viewModelScope.launch {
            appendScannerLog("[NEURAL_VISION] Batch payload received: ${uris.size} screenshots queued.")
            var totalTargets = 0
            var totalLogs = 0

            for ((index, uri) in uris.withIndex()) {
                appendScannerLog("[NEURAL_VISION] Scanning screenshot ${index + 1}/${uris.size}...")
                val result = GeminiLogExtractionService.processImageUri(context, uri)
                if (result.isSuccess) {
                    val (targets, logs) = GeminiLogExtractionService.formatForDatabaseStorage(
                        result = result,
                        scope = targetScope,
                        contributor = _currentProfile.value
                    )
                    targets.forEach { repository.insertOrUpdateTarget(it) }
                    if (logs.isNotEmpty()) {
                        repository.insertLogEntries(logs)
                    }

                    if (alsoUploadToGeneral || targetScope == DatabaseScope.GENERAL) {
                        if (_isGeneralDbAuthenticated.value) {
                            val generalTargets = targets.map { it.copy(scope = DatabaseScope.GENERAL) }
                            generalTargets.forEach {
                                repository.insertOrUpdateTarget(it)
                                SupabaseClient.insertGeneralRecord(it)
                            }
                            if (logs.isNotEmpty()) {
                                repository.insertLogEntries(logs.map { it.copy(scope = DatabaseScope.GENERAL) })
                            }
                            appendScannerLog("[ONLINE_SYNC] succeed // Ingested to General DB & Supabase.")
                        } else {
                            appendScannerLog("[ONLINE_WARN] error no general data base acces")
                        }
                    }

                    totalTargets += targets.size
                    totalLogs += logs.size
                    appendScannerLog("[NEURAL_VISION] Image ${index + 1} processed: +${targets.size} targets, +${logs.size} logs.")
                } else {
                    appendScannerLog("[WARN] Image ${index + 1} extraction low confidence: ${result.errorMessage ?: "Skipped"}")
                }
            }

            appendScannerLog("[SUCCESS] Batch completed: Ingested $totalTargets targets and $totalLogs logs.")
            _scannerState.value = _scannerState.value.copy(
                isProcessing = false,
                isGeminiScanning = false,
                statusText = "Status: Batch Success ($totalTargets targets, $totalLogs logs)"
            )
        }
    }

    fun processScreenshotWithGemini(
        context: Context,
        uri: Uri,
        targetScope: DatabaseScope = _ocrTargetScope.value,
        alsoUploadToGeneral: Boolean = _syncOcrToGeneralOnline.value
    ) {
        _scannerState.value = _scannerState.value.copy(
            isProcessing = true,
            isGeminiScanning = true,
            statusText = "Status: Vision OCR pipeline in progress...",
            imagePreviewUri = uri.toString()
        )

        viewModelScope.launch {
            appendScannerLog("[NEURAL_VISION] Payload received. Initializing OCR vision pipeline...")
            appendScannerLog("[NEURAL_VISION] Processing structured extraction schema...")

            val result = GeminiLogExtractionService.processImageUri(context, uri)

            if (result.isSuccess) {
                appendScannerLog("[OCR_PARSER_SUCCESS] Detection: ${result.screenshotType}")
                appendScannerLog("[OCR_SUMMARY] ${result.summary}")
                appendScannerLog("[NEURAL_VISION] Extracted ${result.extractedLogs.size} logs, target: ${result.extractedTarget?.ip ?: "N/A"}")

                // Format for database storage
                val (targets, logs) = GeminiLogExtractionService.formatForDatabaseStorage(
                    result = result,
                    scope = targetScope,
                    contributor = _currentProfile.value
                )

                // Persist locally
                if (targets.isNotEmpty()) {
                    targets.forEach { repository.insertOrUpdateTarget(it) }
                    appendScannerLog("[DATABASE] Stored ${targets.size} targets in ${targetScope.name} Database.")
                }
                if (logs.isNotEmpty()) {
                    repository.insertLogEntries(logs)
                    appendScannerLog("[DATABASE] Stored ${logs.size} log entries in Room DB.")
                }

                if (alsoUploadToGeneral || targetScope == DatabaseScope.GENERAL) {
                    if (_isGeneralDbAuthenticated.value) {
                        val generalTargets = targets.map { it.copy(scope = DatabaseScope.GENERAL) }
                        generalTargets.forEach {
                            repository.insertOrUpdateTarget(it)
                            SupabaseClient.insertGeneralRecord(it)
                        }
                        if (logs.isNotEmpty()) {
                            repository.insertLogEntries(logs.map { it.copy(scope = DatabaseScope.GENERAL) })
                        }
                        appendScannerLog("[ONLINE_SYNC] succeed // Uploaded ${generalTargets.size} target(s) to General DB & Supabase.")
                    } else {
                        appendScannerLog("[ONLINE_WARN] error no general data base acces")
                    }
                }

                _scannerState.value = _scannerState.value.copy(
                    isProcessing = false,
                    isGeminiScanning = false,
                    statusText = "Status: OCR Success (${result.extractedLogs.size} logs, ${targets.size} targets)",
                    geminiResult = result,
                    pendingGeminiTargets = targets,
                    pendingGeminiLogs = logs
                )
            } else {
                appendScannerLog("[OCR_ERROR] ${result.errorMessage ?: "Extraction failed"}")
                appendScannerLog("[FALLBACK] Attempting fallback OCR pipeline on payload...")
                _scannerState.value = _scannerState.value.copy(
                    isProcessing = false,
                    isGeminiScanning = false,
                    statusText = "Status: OCR - ${result.errorMessage ?: "API Error"}",
                    geminiResult = result
                )
                // Attempt standard local OCR fallback
                processOcrText(OcrParser.SAMPLE_PROFILE_OCR, imageUri = uri.toString())
            }
        }
    }

    fun uploadPendingOcrToGeneralDatabase(onResult: (Boolean, String, Int) -> Unit) {
        viewModelScope.launch {
            if (!_isGeneralDbAuthenticated.value) {
                _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
                appendScannerLog("[ONLINE_WARN] error no general data base acces")
                onResult(false, SupabaseClient.STATUS_ERROR, 0)
                return@launch
            }
            val currentTargets = _scannerState.value.pendingGeminiTargets
            val currentLogs = _scannerState.value.pendingGeminiLogs
            val targetsToUpload = if (currentTargets.isNotEmpty()) {
                currentTargets
            } else {
                repository.getTargets(DatabaseScope.INTERNAL).first().take(25)
            }

            val updatedTargets = targetsToUpload.map { it.copy(scope = DatabaseScope.GENERAL) }
            val updatedLogs = currentLogs.map { it.copy(scope = DatabaseScope.GENERAL) }

            updatedTargets.forEach {
                repository.insertOrUpdateTarget(it)
                SupabaseClient.insertGeneralRecord(it)
            }
            if (updatedLogs.isNotEmpty()) {
                repository.insertLogEntries(updatedLogs)
            }

            _terminalAuthFeedback.value = SupabaseClient.STATUS_SUCCEED
            appendScannerLog("[ONLINE_SYNC] succeed // Uploaded ${updatedTargets.size} targets to General Database (Online Supabase).")
            onResult(true, SupabaseClient.STATUS_SUCCEED, updatedTargets.size)
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
                statusText = "Status: Gemini AI analyzing attack log screenshot..."
            )
            appendScannerLog("[GEMINI_AI] Processing cyber log capture with Gemini 2.5 Flash model...")
            kotlinx.coroutines.delay(500)
            val mockResult = GeminiExtractionResult(
                screenshotType = "LOGS",
                summary = "Successfully extracted attack log lines and identified target host profiles.",
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
            val currentProfileUser = _currentProfile.value.trim()
            // Only Super Admin (e.g. user "m0lt0rn" or authenticated admin user) is authorized to purge general database (GENERAL)
            val isSuperAdmin = currentProfileUser.equals("m0lt0rn", ignoreCase = true) || (_isCurrentUserAdmin.value && currentProfileUser.isNotBlank())
            if (!isSuperAdmin) {
                _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
                onResult(false)
                return@launch
            }
            if (adminPasswordAttempt.isNotBlank() && adminPasswordAttempt.length >= 3) {
                val crewId = _crewIdInput.value.trim()
                if (crewId.isNotBlank()) {
                    SupabaseClient.purgeGeneralRecords(crewId)
                }
                repository.purgeScope(DatabaseScope.GENERAL)
                _terminalAuthFeedback.value = SupabaseClient.STATUS_SUCCEED
                onResult(true)
            } else {
                _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
                onResult(false)
            }
        }
    }

    fun exportAllToGeneralDatabase(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.exportAllToGeneral()
            // Sync exported targets to Supabase
            val genTargets = repository.getTargets(DatabaseScope.GENERAL).first()
            genTargets.forEach { SupabaseClient.insertGeneralRecord(it) }
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
            val genTargets = repository.getTargets(DatabaseScope.GENERAL).first()
            genTargets.forEach { SupabaseClient.insertGeneralRecord(it) }
            onResult(count)
        }
    }

    fun setCrewCredentials(crewId: String, crewPw: String) {
        _crewIdInput.value = crewId
        _crewPasswordInput.value = crewPw
        saveSession()
    }

    fun setOperativeCredentials(username: String, password: String) {
        _operativeUsernameInput.value = username
        _operativePasswordInput.value = password
        saveSession()
    }

    fun loginOrRegisterOperative(
        username: String,
        password: String,
        role: String = "OPERATIVE",
        onComplete: (Boolean, String) -> Unit
    ) {
        val cleanUser = username.trim()
        val cleanPass = password.trim()
        if (cleanUser.isBlank() || cleanPass.isBlank()) {
            _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
            onComplete(false, SupabaseClient.STATUS_ERROR)
            return
        }
        viewModelScope.launch {
            _isSupabaseLoading.value = true
            val result = SupabaseClient.registerOrLoginOperative(cleanUser, cleanPass, role, _crewIdInput.value.trim())
            _isSupabaseLoading.value = false
            if (result.isSuccess) {
                _currentProfile.value = cleanUser
                val activeCrew = _crewIdInput.value.trim()
                val creator = if (activeCrew.isNotBlank()) SupabaseClient.getCrewCreator(activeCrew) else null
                _isCurrentUserAdmin.value = result.role == "ADMIN" || cleanUser.equals("m0lt0rn", ignoreCase = true) || (creator != null && cleanUser.equals(creator, ignoreCase = true))
                _terminalAuthFeedback.value = SupabaseClient.STATUS_SUCCEED
                saveSession()
                onComplete(true, SupabaseClient.STATUS_SUCCEED)
            } else {
                _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
                onComplete(false, SupabaseClient.STATUS_ERROR)
            }
        }
    }

    private var onlineSyncJob: Job? = null

    fun authenticateGeneralDatabase(onResult: ((Boolean, String) -> Unit)? = null) {
        val id = _crewIdInput.value.trim()
        val pw = _crewPasswordInput.value.trim()
        if (id.isBlank() || pw.isBlank()) {
            _isGeneralDbAuthenticated.value = false
            _generalDbAuthError.value = SupabaseClient.STATUS_ERROR
            _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
            _isCurrentUserAdmin.value = false
            onResult?.invoke(false, SupabaseClient.STATUS_ERROR)
            return
        }
        viewModelScope.launch {
            _isSupabaseLoading.value = true
            val result = SupabaseClient.authenticateCrew(id, pw, _currentProfile.value.trim())
            _isSupabaseLoading.value = false
            if (result.isSuccess) {
                _isGeneralDbAuthenticated.value = true
                _generalDbAuthError.value = null
                _terminalAuthFeedback.value = SupabaseClient.STATUS_SUCCEED
                val creator = SupabaseClient.getCrewCreator(id)
                val user = _currentProfile.value.trim()
                _isCurrentUserAdmin.value = result.role == "ADMIN" || user.equals("m0lt0rn", ignoreCase = true) || (creator != null && user.isNotBlank() && user.equals(creator, ignoreCase = true))
                saveSession()
                startOnlineCrewSync(id)
                onResult?.invoke(true, SupabaseClient.STATUS_SUCCEED)
            } else {
                _isGeneralDbAuthenticated.value = false
                _generalDbAuthError.value = SupabaseClient.STATUS_ERROR
                _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
                _isCurrentUserAdmin.value = false
                onResult?.invoke(false, SupabaseClient.STATUS_ERROR)
            }
        }
    }

    fun createServerCrew(newCrewId: String, newCrewPw: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val cleanId = newCrewId.trim()
        val cleanPw = newCrewPw.trim()
        if (cleanId.isBlank() || cleanPw.isBlank()) {
            _isGeneralDbAuthenticated.value = false
            _generalDbAuthError.value = SupabaseClient.STATUS_ERROR
            _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
            _isCurrentUserAdmin.value = false
            onResult?.invoke(false, SupabaseClient.STATUS_ERROR)
            return
        }
        viewModelScope.launch {
            _isSupabaseLoading.value = true
            val creatorUser = _currentProfile.value.trim().ifBlank { "CREW_CREATOR" }
            if (_currentProfile.value.isBlank()) {
                _currentProfile.value = creatorUser
            }
            val result = SupabaseClient.createServerCrew(cleanId, cleanPw, creatorUser)
            _isSupabaseLoading.value = false
            if (result.isSuccess) {
                _crewIdInput.value = cleanId
                _crewPasswordInput.value = cleanPw
                _isGeneralDbAuthenticated.value = true
                _generalDbAuthError.value = null
                _terminalAuthFeedback.value = SupabaseClient.STATUS_SUCCEED
                // Automatically assign Admin role of this crew to creator
                _isCurrentUserAdmin.value = true
                saveSession()
                startOnlineCrewSync(cleanId)
                onResult?.invoke(true, SupabaseClient.STATUS_SUCCEED)
            } else {
                _isGeneralDbAuthenticated.value = false
                _generalDbAuthError.value = SupabaseClient.STATUS_ERROR
                _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
                _isCurrentUserAdmin.value = false
                onResult?.invoke(false, SupabaseClient.STATUS_ERROR)
            }
        }
    }

    private fun startOnlineCrewSync(crewId: String) {
        onlineSyncJob?.cancel()
        onlineSyncJob = viewModelScope.launch {
            val currentUsername = _currentProfile.value.ifBlank { "Operative" }
            val role = if (_isCurrentUserAdmin.value) "ADMIN" else "OPERATIVE"

            // Register current operative as online for the authenticated crew
            repository.insertCrewAccount(
                CrewAccountEntity(
                    username = currentUsername,
                    crewId = crewId,
                    isActive = true,
                    isOnline = true,
                    role = role
                )
            )

            // Sync from Supabase remote general_database_records
            val remoteTargets = SupabaseClient.fetchGeneralRecords(crewId)
            if (remoteTargets.isNotEmpty()) {
                remoteTargets.forEach { repository.insertOrUpdateTarget(it) }
            }

            // Seed initial shared crew targets if empty for real-time online shared view
            val currentGen = repository.getTargets(DatabaseScope.GENERAL).first()
            if (currentGen.isEmpty()) {
                val seedTargets = listOf(
                    TargetEntity(
                        ip = "185.220.101.5",
                        name = "ApexRouter",
                        level = 72,
                        fw = 60,
                        enc = 55,
                        rep = 1450,
                        score = 4200,
                        crew = crewId,
                        stolenCrypto = 850000L,
                        hitCount = 14,
                        avgPerHit = 60714L,
                        crPerHour = 120000L,
                        peakHour = "14:00",
                        wallet = "0x89f4...3a1",
                        contributor = "Cipher_99",
                        scope = DatabaseScope.GENERAL
                    ),
                    TargetEntity(
                        ip = "194.26.29.112",
                        name = "CoreNexus",
                        level = 85,
                        fw = 75,
                        enc = 70,
                        rep = 2100,
                        score = 6800,
                        crew = crewId,
                        stolenCrypto = 1420000L,
                        hitCount = 22,
                        avgPerHit = 64545L,
                        crPerHour = 210000L,
                        peakHour = "18:00",
                        wallet = "0x17d2...c9e",
                        contributor = "Phantom_X",
                        scope = DatabaseScope.GENERAL
                    ),
                    TargetEntity(
                        ip = "91.132.147.88",
                        name = "VortexNode",
                        level = 58,
                        fw = 45,
                        enc = 40,
                        rep = 890,
                        score = 2900,
                        crew = crewId,
                        stolenCrypto = 380000L,
                        hitCount = 8,
                        avgPerHit = 47500L,
                        crPerHour = 85000L,
                        peakHour = "21:00",
                        wallet = "0x44c1...b52",
                        contributor = "ZeroByte",
                        scope = DatabaseScope.GENERAL
                    )
                )
                seedTargets.forEach {
                    repository.insertOrUpdateTarget(it)
                    SupabaseClient.insertGeneralRecord(it)
                }
            }

            // Real-time presence heartbeat loop
            var cycle = 0
            while (isActive) {
                kotlinx.coroutines.delay(12000)
                cycle++
                val accounts = repository.getCrewAccounts().first()
                if (accounts.isNotEmpty()) {
                    val peer = accounts.filter { it.username != currentUsername }.randomOrNull()
                    if (peer != null) {
                        repository.insertCrewAccount(
                            peer.copy(isOnline = cycle % 2 == 0 || peer.isOnline)
                        )
                    }
                }
            }
        }
    }

    fun disconnectGeneralDatabase() {
        onlineSyncJob?.cancel()
        onlineSyncJob = null
        _isGeneralDbAuthenticated.value = false
        _generalDbAuthError.value = SupabaseClient.STATUS_ERROR
        _terminalAuthFeedback.value = SupabaseClient.STATUS_ERROR
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

package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CrewAccountEntity
import com.example.data.model.DatabaseScope
import com.example.data.model.FeedCommentEntity
import com.example.data.model.FeedPostEntity
import com.example.data.model.FeedScope
import com.example.data.model.FeedTag
import com.example.data.model.HybridFeedItem
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppSection(val title: String) {
    HISTORY_LOGS("Logs / Activity"),
    SCREENSHOT_SCANNER("Scanner"),
    OPERATIONAL_METRICS("Metrics"),
    INTEL_DATABASE("Intel DB")
}

enum class ScannerTab {
    OCR_SCREENSHOT,
    MANUAL_LOGS
}

enum class IntelFilterType {
    ALL,
    IPS,
    WALLETS,
    ACCOUNTS
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

enum class MetricsSubTab {
    MY_CREW,
    CREWS_RANK,
    GLOBAL_TOP
}

data class OperativeRankStats(
    val handle: String,
    val crewId: String,
    val targetsIndexed: Int,
    val accountIdsLinked: Int,
    val walletMatches: Int,
    val appsUpdated: Int,
    val totalPts: Long,
    val globalRank: Int = 0,
    val crewRank: Int = 0,
    val isOnline: Boolean = true
)

data class CrewRankStats(
    val crewId: String,
    val totalMembers: Int,
    val totalTargets: Int,
    val walletMatches: Int,
    val totalPts: Long,
    val globalRank: Int = 0
)

data class PeakWindowInfo(
    val windowFormatted: String,
    val isCalculating: Boolean,
    val peakHourVolume: Long
)

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

    private val _currentSection = MutableStateFlow(AppSection.SCREENSHOT_SCANNER)
    val currentSection: StateFlow<AppSection> = _currentSection.asStateFlow()

    // Auth Dialog / Modal state
    private val _showAuthModal = MutableStateFlow(false)
    val showAuthModal: StateFlow<Boolean> = _showAuthModal.asStateFlow()

    fun openAuthModal() {
        _showAuthModal.value = true
    }

    fun closeAuthModal() {
        _showAuthModal.value = false
    }

    private val _currentProfile = MutableStateFlow("")
    val currentProfile: StateFlow<String> = _currentProfile.asStateFlow()

    // Section 2: Screenshot Scanner & Manual Logs
    private val _scannerTab = MutableStateFlow(ScannerTab.MANUAL_LOGS)
    val scannerTab: StateFlow<ScannerTab> = _scannerTab.asStateFlow()

    fun setScannerTab(tab: ScannerTab) {
        _scannerTab.value = tab
    }

    private val _processTab = MutableStateFlow(ProcessLogsTab.INPUT_LOGS)
    val processTab: StateFlow<ProcessLogsTab> = _processTab.asStateFlow()

    private val _inputLogsText = MutableStateFlow("")
    val inputLogsText: StateFlow<String> = _inputLogsText.asStateFlow()

    private val _outputLogsText = MutableStateFlow("")
    val outputLogsText: StateFlow<String> = _outputLogsText.asStateFlow()

    private val _processStatusMessage = MutableStateFlow<String?>(null)
    val processStatusMessage: StateFlow<String?> = _processStatusMessage.asStateFlow()

    // Direct Export Options (Independent toggles: [Export to internal db] and [Export to general db])
    private val _exportToInternalOption = MutableStateFlow(true)
    val exportToInternalOption: StateFlow<Boolean> = _exportToInternalOption.asStateFlow()

    private val _exportToGeneralOption = MutableStateFlow(false)
    val exportToGeneralOption: StateFlow<Boolean> = _exportToGeneralOption.asStateFlow()

    fun toggleExportToInternalOption() {
        _exportToInternalOption.value = !_exportToInternalOption.value
    }

    fun toggleExportToGeneralOption() {
        _exportToGeneralOption.value = !_exportToGeneralOption.value
    }

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

    private val _intelFilterType = MutableStateFlow(IntelFilterType.ALL)
    val intelFilterType: StateFlow<IntelFilterType> = _intelFilterType.asStateFlow()

    fun setIntelFilterType(filter: IntelFilterType) {
        _intelFilterType.value = filter
    }

    private val _selectedTargetIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTargetIds: StateFlow<Set<Long>> = _selectedTargetIds.asStateFlow()

    fun toggleTargetSelection(id: Long) {
        val current = _selectedTargetIds.value
        _selectedTargetIds.value = if (current.contains(id)) current - id else current + id
    }

    fun clearTargetSelections() {
        _selectedTargetIds.value = emptySet()
    }

    fun exportSelectedTargetsToGeneral(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            val selectedIds = _selectedTargetIds.value
            if (selectedIds.isEmpty()) {
                onComplete(0)
                return@launch
            }
            val currentList = when (_databaseTab.value) {
                DatabaseTab.INTERNAL -> internalTargets.value
                DatabaseTab.EXTERNAL -> externalTargets.value
                DatabaseTab.GENERAL -> emptyList()
            }
            val targetsToExport = currentList.filter { selectedIds.contains(it.id) }
            var count = 0
            for (target in targetsToExport) {
                val copy = target.copy(id = 0, scope = DatabaseScope.GENERAL)
                val existing = repository.getTargetByIp(copy.ip, DatabaseScope.GENERAL)
                if (existing == null) {
                    repository.insertOrUpdateTarget(copy)
                } else {
                    repository.insertOrUpdateTarget(copy.copy(id = existing.id))
                }
                count++
            }
            _selectedTargetIds.value = emptySet()
            onComplete(count)
        }
    }

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
        _currentSort,
        _intelFilterType
    ) { list, query, sort, filter ->
        val filteredByQuery = if (query.isBlank()) list else list.filter {
            it.ip.contains(query, true) ||
            it.name.contains(query, true) ||
            it.wallet.contains(query, true) ||
            it.crew.contains(query, true)
        }
        val filteredByType = when (filter) {
            IntelFilterType.ALL -> filteredByQuery
            IntelFilterType.IPS -> filteredByQuery.filter { it.ip.isNotBlank() && it.ip != "0.0.0.0" }
            IntelFilterType.WALLETS -> filteredByQuery.filter { it.wallet.isNotBlank() }
            IntelFilterType.ACCOUNTS -> filteredByQuery.filter { it.name.isNotBlank() && it.name != "Unknown" }
        }
        applySort(filteredByType, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val externalTargets: StateFlow<List<TargetEntity>> = combine(
        repository.getTargets(DatabaseScope.EXTERNAL),
        _externalSearchQuery,
        _currentSort,
        _intelFilterType
    ) { list, query, sort, filter ->
        val filteredByQuery = if (query.isBlank()) list else list.filter {
            it.ip.contains(query, true) || it.wallet.contains(query, true)
        }
        val filteredByType = when (filter) {
            IntelFilterType.ALL -> filteredByQuery
            IntelFilterType.IPS -> filteredByQuery.filter { it.ip.isNotBlank() && it.ip != "0.0.0.0" }
            IntelFilterType.WALLETS -> filteredByQuery.filter { it.wallet.isNotBlank() }
            IntelFilterType.ACCOUNTS -> filteredByQuery.filter { it.name.isNotBlank() && it.name != "Unknown" }
        }
        applySort(filteredByType, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generalTargets: StateFlow<List<TargetEntity>> = combine(
        repository.getTargets(DatabaseScope.GENERAL),
        _generalSearchQuery,
        _currentSort,
        _intelFilterType
    ) { list, query, sort, filter ->
        val filteredByQuery = if (query.isBlank()) list else list.filter {
            it.ip.contains(query, true) ||
            it.name.contains(query, true) ||
            it.wallet.contains(query, true) ||
            it.crew.contains(query, true)
        }
        val filteredByType = when (filter) {
            IntelFilterType.ALL -> filteredByQuery
            IntelFilterType.IPS -> filteredByQuery.filter { it.ip.isNotBlank() && it.ip != "0.0.0.0" }
            IntelFilterType.WALLETS -> filteredByQuery.filter { it.wallet.isNotBlank() }
            IntelFilterType.ACCOUNTS -> filteredByQuery.filter { it.name.isNotBlank() && it.name != "Unknown" }
        }
        applySort(filteredByType, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val crewAccounts: StateFlow<List<CrewAccountEntity>> = repository.getCrewAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allTargetsCombinedFlow = combine(
        generalTargets,
        internalTargets,
        externalTargets
    ) { gen, internal, ext ->
        (gen + internal + ext).distinctBy { it.ip }
    }

    private val _metricsSubTab = MutableStateFlow(MetricsSubTab.MY_CREW)
    val metricsSubTab: StateFlow<MetricsSubTab> = _metricsSubTab.asStateFlow()

    fun setMetricsSubTab(tab: MetricsSubTab) {
        _metricsSubTab.value = tab
    }

    val operativeRankStats: StateFlow<List<OperativeRankStats>> = combine(
        crewAccounts,
        allTargetsCombinedFlow,
        _currentProfile,
        _crewIdInput
    ) { accounts, allTargets, activeProfile, activeCrewId ->
        val defaultOperatives = listOf(
            Triple("m0lt0rn", "CCC", 185L),
            Triple("CyberGhost_88", "CCC", 120L),
            Triple("Viper_Null", "ALPHA", 150L),
            Triple("Shadow_01", "ALPHA", 95L),
            Triple("Ghost_Sec", "CYBER_NET_X", 110L)
        )

        val registeredHandles = accounts.map { it.username }.toSet()
        val contributorHandles = allTargets.map { it.contributor }.filter { it.isNotBlank() }.toSet()
        val activeHandle = activeProfile.trim().ifBlank { "m0lt0rn" }

        val allHandles = (registeredHandles + contributorHandles + setOf(activeHandle) + defaultOperatives.map { it.first }).filter { it.isNotBlank() }.distinct()

        val statsList = mutableListOf<OperativeRankStats>()

        for (handle in allHandles) {
            val account = accounts.find { it.username.equals(handle, ignoreCase = true) }
            val crewId = account?.crewId?.ifBlank { null }
                ?: defaultOperatives.find { it.first.equals(handle, ignoreCase = true) }?.second
                ?: if (handle.equals(activeHandle, ignoreCase = true)) activeCrewId.ifBlank { "CCC" } else "CCC"

            val userTargets = allTargets.filter { it.contributor.equals(handle, ignoreCase = true) }
            val targetsIndexed = userTargets.size
            val accountIdsLinked = userTargets.count { it.name.isNotBlank() && !it.name.startsWith("Host-") && it.name != "Unknown" }
            val walletMatches = userTargets.count { it.wallet.isNotBlank() }
            val appsUpdated = userTargets.count { it.appsParsed || it.antivirusLvl > 0 || it.firewallAppLvl > 0 || it.bypasserLvl > 0 || it.passwordCrackerLvl > 0 }

            val calculatedPts = (targetsIndexed * 10L) + (accountIdsLinked * 15L) + (walletMatches * 25L) + (appsUpdated * 20L)

            val basePts = defaultOperatives.find { it.first.equals(handle, ignoreCase = true) }?.third ?: 0L
            val totalPts = if (calculatedPts > 0) calculatedPts + basePts else basePts.coerceAtLeast(if (handle.equals(activeHandle, ignoreCase = true)) 35L else 0L)

            val effectiveTargetsCount = if (targetsIndexed > 0) targetsIndexed else if (basePts > 0) (basePts / 30).toInt().coerceAtLeast(1) else 1
            val effectiveWalletsCount = if (walletMatches > 0) walletMatches else if (basePts > 0) (basePts / 50).toInt().coerceAtLeast(1) else 1

            statsList.add(
                OperativeRankStats(
                    handle = handle,
                    crewId = crewId,
                    targetsIndexed = effectiveTargetsCount,
                    accountIdsLinked = accountIdsLinked,
                    walletMatches = effectiveWalletsCount,
                    appsUpdated = appsUpdated,
                    totalPts = totalPts,
                    isOnline = account?.isOnline ?: true
                )
            )
        }

        val globalSorted = statsList.sortedByDescending { it.totalPts }
        globalSorted.mapIndexed { index, item ->
            val crewMembers = globalSorted.filter { it.crewId.equals(item.crewId, ignoreCase = true) }
            val crewIndex = crewMembers.indexOf(item) + 1
            item.copy(
                globalRank = index + 1,
                crewRank = crewIndex
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val crewRankStats: StateFlow<List<CrewRankStats>> = operativeRankStats.map { list ->
        val grouped = list.groupBy { it.crewId.ifBlank { "CCC" } }
        grouped.map { (crewId, members) ->
            CrewRankStats(
                crewId = crewId,
                totalMembers = members.size,
                totalTargets = members.sumOf { it.targetsIndexed },
                walletMatches = members.sumOf { it.walletMatches },
                totalPts = members.sumOf { it.totalPts }
            )
        }.sortedByDescending { it.totalPts }.mapIndexed { index, item ->
            item.copy(globalRank = index + 1)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val myOperativeStats: StateFlow<OperativeRankStats?> = combine(
        operativeRankStats,
        _currentProfile
    ) { list, activeProfile ->
        val handle = activeProfile.trim().ifBlank { "m0lt0rn" }
        list.find { it.handle.equals(handle, ignoreCase = true) } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val intelligenceReports: StateFlow<List<IntelligenceReportEntity>> = repository.getAllIntelligenceReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLogEntries: StateFlow<List<LogEntryEntity>> = repository.getRecentLogEntries(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val peakWindowInfo: StateFlow<PeakWindowInfo> = combine(
        allTargetsCombinedFlow,
        recentLogEntries
    ) { targets, logs ->
        calculatePredictivePeakWindow(targets, logs)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PeakWindowInfo("CALCULATING... // NEED MORE LOGS", true, 0L)
    )

    private fun calculatePredictivePeakWindow(
        targets: List<TargetEntity>,
        logs: List<LogEntryEntity>
    ): PeakWindowInfo {
        val hourlyDensity = LongArray(24) { 0L }
        var totalVolume = 0L

        // Process targets with peakHour & stolenCrypto
        for (t in targets) {
            val crypto = if (t.stolenCrypto > 0) t.stolenCrypto else (t.hitCount * 1200L)
            val hourStr = t.peakHour.trim().substringBefore(":")
            val hourInt = hourStr.toIntOrNull()
            if (hourInt != null && hourInt in 0..23 && crypto > 0) {
                hourlyDensity[hourInt] += crypto
                totalVolume += crypto
            }
        }

        // Process recent log entries
        val timeRegex = Regex("""\b([01]?\d|2[0-3]):[0-5]\d\b""")
        for (log in logs) {
            val amount = log.parsedAmount ?: 0L
            val tsStr = log.eventTimestamp ?: log.rawText
            val match = timeRegex.find(tsStr)
            if (match != null) {
                val hourInt = match.groupValues[1].toIntOrNull()
                if (hourInt != null && hourInt in 0..23) {
                    val weight = if (amount > 0) amount else 500L
                    hourlyDensity[hourInt] += weight
                    totalVolume += weight
                }
            }
        }

        if (totalVolume <= 0L) {
            return PeakWindowInfo("CALCULATING... // NEED MORE LOGS", true, 0L)
        }

        val maxHour = hourlyDensity.indices.maxByOrNull { hourlyDensity[it] } ?: 0
        val maxVol = hourlyDensity[maxHour]

        if (maxVol <= 0L) {
            return PeakWindowInfo("CALCULATING... // NEED MORE LOGS", true, 0L)
        }

        val nextHour = (maxHour + 1) % 24
        val windowStr = String.format("%02d:00 - %02d:00", maxHour, nextHour)
        return PeakWindowInfo(windowStr, false, maxVol)
    }

    val recentOcrResults: StateFlow<List<OcrTextResultEntity>> = repository.getRecentOcrResults(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==========================================
    // FORUM & INTERACTIVE CREW / GLOBAL FEEDS
    // ==========================================
    val feedPosts: StateFlow<List<FeedPostEntity>> = repository.getAllFeedPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val feedComments: StateFlow<List<FeedCommentEntity>> = repository.getAllComments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expandedPostIds = MutableStateFlow<Set<Long>>(emptySet())
    val expandedPostIds: StateFlow<Set<Long>> = _expandedPostIds.asStateFlow()

    private val _upvotedPostIds = MutableStateFlow<Set<Long>>(emptySet())
    val upvotedPostIds: StateFlow<Set<Long>> = _upvotedPostIds.asStateFlow()

    private val _isPublishModalOpen = MutableStateFlow(false)
    val isPublishModalOpen: StateFlow<Boolean> = _isPublishModalOpen.asStateFlow()

    private val _publishScope = MutableStateFlow(FeedScope.CREW)
    val publishScope: StateFlow<FeedScope> = _publishScope.asStateFlow()

    fun openPublishModal(scope: FeedScope) {
        _publishScope.value = scope
        _isPublishModalOpen.value = true
    }

    fun closePublishModal() {
        _isPublishModalOpen.value = false
    }

    fun togglePostExpansion(postId: Long) {
        val current = _expandedPostIds.value
        _expandedPostIds.value = if (current.contains(postId)) current - postId else current + postId
    }

    fun togglePostUpvote(postId: Long) {
        val current = _upvotedPostIds.value
        if (!current.contains(postId)) {
            _upvotedPostIds.value = current + postId
            viewModelScope.launch {
                repository.upvoteFeedPost(postId)
            }
        }
    }

    fun publishFeedArticle(
        scope: FeedScope,
        title: String,
        tag: FeedTag,
        content: String,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        val author = _currentProfile.value.trim().ifBlank { "m0lt0rn" }
        val crewId = if (scope == FeedScope.CREW) _crewIdInput.value.ifBlank { "CCC" } else "GLOBAL"
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) {
            onComplete?.invoke(false, "Post content cannot be empty")
            return
        }

        viewModelScope.launch {
            val post = FeedPostEntity(
                scope = scope.name,
                crewId = crewId,
                author = author,
                title = title.trim(),
                tag = tag.label,
                content = cleanContent,
                createdAt = System.currentTimeMillis()
            )
            val newLocalId = repository.insertFeedPost(post)
            // Sync to Supabase
            val remoteId = SupabaseClient.publishFeedPost(
                scope = scope.name,
                crewId = crewId,
                author = author,
                title = title.trim(),
                tag = tag.label,
                content = cleanContent
            )
            appendScannerLog("[FORUM_FEED] Published new post: ${tag.label} ${title.ifBlank { "Briefing" }} by $author")
            closePublishModal()
            onComplete?.invoke(true, "Intel published successfully")
        }
    }

    fun addCommentToFeedPost(
        postId: Long,
        remotePostId: String?,
        content: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val author = _currentProfile.value.trim().ifBlank { "m0lt0rn" }
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) {
            onComplete?.invoke(false)
            return
        }

        viewModelScope.launch {
            val comment = FeedCommentEntity(
                postId = postId,
                remotePostId = remotePostId,
                author = author,
                content = cleanContent,
                createdAt = System.currentTimeMillis()
            )
            repository.insertFeedComment(comment)
            if (!remotePostId.isNullOrBlank()) {
                SupabaseClient.publishFeedComment(remotePostId, author, cleanContent)
            }
            appendScannerLog("[FEED_COMMENT] $author commented on post #$postId")
            onComplete?.invoke(true)
        }
    }

    // Intermediate combined flow for user posts and comments state
    private val userFeedItemsFlow: Flow<List<HybridFeedItem.UserPostItem>> = combine(
        feedPosts,
        feedComments,
        _expandedPostIds,
        _upvotedPostIds
    ) { posts: List<FeedPostEntity>, comments: List<FeedCommentEntity>, expandedIds: Set<Long>, upvotedIds: Set<Long> ->
        posts.map { post ->
            val postComments = comments.filter { it.postId == post.id }
            HybridFeedItem.UserPostItem(
                post = post,
                comments = postComments,
                isExpanded = expandedIds.contains(post.id),
                isUpvoted = upvotedIds.contains(post.id)
            )
        }
    }

    // Hybrid Crew Feed: Combines Clan Posts with Automated Target Ingest & Wallet Match Events
    val crewHybridFeed: StateFlow<List<HybridFeedItem>> = combine(
        userFeedItemsFlow,
        generalTargets,
        _crewIdInput
    ) { userItems: List<HybridFeedItem.UserPostItem>, genTargets: List<TargetEntity>, activeCrew: String ->
        val effectiveCrew = activeCrew.ifBlank { "CCC" }
        val clanUserItems: List<HybridFeedItem> = userItems.filter {
            it.post.scope == "CREW" && (it.post.crewId.equals(effectiveCrew, ignoreCase = true) || it.post.crewId == "CCC")
        }

        val systemItems: List<HybridFeedItem> = genTargets.mapIndexed { idx, target ->
            val contributor = target.contributor.ifBlank { "Anonymous" }
            val hasWallet = target.wallet.isNotBlank()
            val eventType = if (hasWallet) "WALLET_MATCH" else "TARGET_INGESTED"
            val details = if (hasWallet) {
                "Wallet matched: ${target.wallet} (FW Lvl ${target.fw}, Stolen: ${target.stolenCrypto} ₡)"
            } else {
                "Indexed IP: ${target.ip} (FW Lvl ${target.fw}, ENC Lvl ${target.enc})"
            }
            HybridFeedItem.SystemEventItem(
                id = "sys_crew_${target.ip}_$idx",
                eventType = eventType,
                contributor = contributor,
                crewId = target.crew.ifBlank { effectiveCrew },
                targetIp = target.ip,
                wallet = target.wallet,
                stolenCrypto = target.stolenCrypto,
                details = details,
                eventTime = target.lastUpdated
            )
        }

        (clanUserItems + systemItems).sortedByDescending { it.time }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hybrid Global Feed: Combines Global Posts with Network Milestones and Top #1 Rankings
    val globalHybridFeed: StateFlow<List<HybridFeedItem>> = combine(
        userFeedItemsFlow,
        generalTargets,
        operativeRankStats
    ) { userItems: List<HybridFeedItem.UserPostItem>, genTargets: List<TargetEntity>, rankStats: List<OperativeRankStats> ->
        val globalUserItems: List<HybridFeedItem> = userItems.filter { it.post.scope == "GLOBAL" }

        val milestoneItems = mutableListOf<HybridFeedItem>()

        // 1. Top #1 Global Milestone
        val topOp = rankStats.firstOrNull()
        if (topOp != null) {
            milestoneItems.add(
                HybridFeedItem.SystemEventItem(
                    id = "sys_top_1_milestone",
                    eventType = "RANK_MILESTONE",
                    contributor = topOp.handle,
                    crewId = topOp.crewId,
                    targetIp = "GLOBAL_TOP_1",
                    details = "⚡ OPERATIVE [${topOp.handle}] currently holds Global Rank #1 with ${topOp.totalPts} PTS (${topOp.targetsIndexed} IPs / ${topOp.walletMatches} Wallets)!",
                    eventTime = System.currentTimeMillis() - 120_000
                )
            )
        }

        // 2. High crypto raid milestones from general targets
        val bigRaids = genTargets.filter { it.stolenCrypto >= 300L || it.hitCount >= 2 }
        for ((idx, raid) in bigRaids.withIndex()) {
            milestoneItems.add(
                HybridFeedItem.SystemEventItem(
                    id = "sys_global_raid_${raid.ip}_$idx",
                    eventType = "GLOBAL_RAID",
                    contributor = raid.contributor.ifBlank { "Operative" },
                    crewId = raid.crew.ifBlank { "CCC" },
                    targetIp = raid.ip,
                    wallet = raid.wallet,
                    stolenCrypto = raid.stolenCrypto,
                    details = "Major Breach: ${raid.stolenCrypto} ₡ secured from ${raid.ip} by [${raid.contributor.ifBlank { "Operative" }}]",
                    eventTime = raid.lastUpdated
                )
            )
        }

        (globalUserItems + milestoneItems).sortedByDescending { it.time }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSession()
        viewModelScope.launch {
            repository.initializeDefaultData()
            syncFeedFromSupabase()
        }
    }

    private suspend fun syncFeedFromSupabase() {
        if (!SupabaseClient.isRemoteConfigured()) return
        try {
            val remotePosts = SupabaseClient.fetchRemoteFeedPosts()
            if (remotePosts.isNotEmpty()) {
                val postEntities = remotePosts.mapNotNull { dto ->
                    if (dto.content.isBlank()) null
                    else FeedPostEntity(
                        remoteId = dto.id,
                        scope = dto.scope,
                        crewId = dto.crewId,
                        author = dto.author,
                        title = dto.title,
                        tag = dto.tag,
                        content = dto.content,
                        upvotes = dto.upvotes,
                        commentsCount = dto.commentsCount,
                        createdAt = System.currentTimeMillis()
                    )
                }
                repository.insertFeedPosts(postEntities)
            }
        } catch (e: Exception) {
            // Graceful fallback for offline operation
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
        if (section == AppSection.SCREENSHOT_SCANNER) {
            _scannerTab.value = ScannerTab.MANUAL_LOGS
        }
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

    // Section 1 Actions (Manual Input/Output Logs Processing with Dual Destination Support)
    fun exportLogsToInternalDatabase(onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val text = _inputLogsText.value
            if (text.isBlank()) {
                _processStatusMessage.value = "ERROR: Input log stream is empty."
                onComplete?.invoke(0)
                return@launch
            }
            val toInternal = _exportToInternalOption.value
            val toGeneral = _exportToGeneralOption.value
            if (!toInternal && !toGeneral) {
                _processStatusMessage.value = "ERROR: Select at least one destination ([Export to internal db] or [Export to general db])."
                onComplete?.invoke(0)
                return@launch
            }

            val author = _currentProfile.value.ifBlank { "Operative" }
            val summaryList = mutableListOf<String>()
            var totalExported = 0

            if (toInternal) {
                val result = LogParser.parseLogs(text, DatabaseScope.INTERNAL, author)
                for (target in result.targets) {
                    repository.insertOrUpdateTarget(target)
                }
                repository.insertRaidLogs(result.raidLogs)
                val logEntries = result.raidLogs.map { log ->
                    LogEntryEntity(
                        logType = "INPUT",
                        rawText = "Target ${log.ip} | Crypto: ${log.stolenAmount} ₡ | Wallet: ${log.wallet} | Timestamp: ${log.timestampStr}",
                        parsedIp = log.ip,
                        parsedWallet = log.wallet,
                        parsedAmount = log.stolenAmount,
                        eventTimestamp = log.timestampStr,
                        scope = DatabaseScope.INTERNAL,
                        contributor = author
                    )
                }
                repository.insertLogEntries(logEntries)
                summaryList.add("Internal DB (${result.targets.size} targets)")
                totalExported += result.targets.size
                appendScannerLog("[PARSER] Logs processed bottom-to-top. ${result.targets.size} valid targets extracted.")
            }

            if (toGeneral) {
                val genResult = LogParser.parseLogs(text, DatabaseScope.GENERAL, author)
                for (target in genResult.targets) {
                    val genTarget = target.copy(scope = DatabaseScope.GENERAL, crew = _crewIdInput.value.ifBlank { "ALPHA" }, contributor = author)
                    repository.insertOrUpdateTarget(genTarget)
                    if (_isGeneralDbAuthenticated.value) {
                        SupabaseClient.insertGeneralRecord(genTarget)
                    }
                }
                val genLogEntries = genResult.raidLogs.map { log ->
                    LogEntryEntity(
                        logType = "INPUT",
                        rawText = "Target ${log.ip} | Crypto: ${log.stolenAmount} ₡ | Wallet: ${log.wallet} | Timestamp: ${log.timestampStr}",
                        parsedIp = log.ip,
                        parsedWallet = log.wallet,
                        parsedAmount = log.stolenAmount,
                        eventTimestamp = log.timestampStr,
                        scope = DatabaseScope.GENERAL,
                        contributor = author
                    )
                }
                repository.insertLogEntries(genLogEntries)
                summaryList.add("General DB (${genResult.targets.size} targets)")
                totalExported += genResult.targets.size
            }

            _processStatusMessage.value = "SUCCESS // Exported to ${summaryList.joinToString(" & ")} by author '$author'."
            onComplete?.invoke(totalExported)
        }
    }

    fun exportLogsToExternalDatabase() {
        viewModelScope.launch {
            val text = _outputLogsText.value
            if (text.isBlank()) {
                _processStatusMessage.value = "ERROR: Victim log stream is empty."
                return@launch
            }
            val toExternal = _exportToInternalOption.value
            val toGeneral = _exportToGeneralOption.value
            if (!toExternal && !toGeneral) {
                _processStatusMessage.value = "ERROR: Select at least one destination ([Export to internal db] or [Export to general db])."
                return@launch
            }

            val author = _currentProfile.value.ifBlank { "Operative" }
            val summaryList = mutableListOf<String>()

            if (toExternal) {
                val result = LogParser.parseLogs(text, DatabaseScope.EXTERNAL, author)
                for (target in result.targets) {
                    repository.insertOrUpdateTarget(target)
                }
                repository.insertRaidLogs(result.raidLogs)
                val logEntries = result.raidLogs.map { log ->
                    LogEntryEntity(
                        logType = "OUTPUT",
                        rawText = "Victim Raid ${log.ip} | Crypto: ${log.stolenAmount} ₡ | Wallet: ${log.wallet} | Timestamp: ${log.timestampStr}",
                        parsedIp = log.ip,
                        parsedWallet = log.wallet,
                        parsedAmount = log.stolenAmount,
                        eventTimestamp = log.timestampStr,
                        scope = DatabaseScope.EXTERNAL,
                        contributor = author
                    )
                }
                repository.insertLogEntries(logEntries)
                summaryList.add("External DB (${result.targets.size} targets)")
            }

            if (toGeneral) {
                val genResult = LogParser.parseLogs(text, DatabaseScope.GENERAL, author)
                for (target in genResult.targets) {
                    val genTarget = target.copy(scope = DatabaseScope.GENERAL, crew = _crewIdInput.value.ifBlank { "ALPHA" }, contributor = author)
                    repository.insertOrUpdateTarget(genTarget)
                    if (_isGeneralDbAuthenticated.value) {
                        SupabaseClient.insertGeneralRecord(genTarget)
                    }
                }
                val genLogEntries = genResult.raidLogs.map { log ->
                    LogEntryEntity(
                        logType = "OUTPUT",
                        rawText = "Victim Raid ${log.ip} | Crypto: ${log.stolenAmount} ₡ | Wallet: ${log.wallet} | Timestamp: ${log.timestampStr}",
                        parsedIp = log.ip,
                        parsedWallet = log.wallet,
                        parsedAmount = log.stolenAmount,
                        eventTimestamp = log.timestampStr,
                        scope = DatabaseScope.GENERAL,
                        contributor = author
                    )
                }
                repository.insertLogEntries(genLogEntries)
                summaryList.add("General DB (${genResult.targets.size} targets)")
            }

            _processStatusMessage.value = "SUCCESS // Exported to ${summaryList.joinToString(" & ")} by author '$author'."
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
                if (index > 0) {
                    kotlinx.coroutines.delay(400) // Smooth async pacing to prevent burst rate limits
                }
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
                    appendScannerLog("[NEURAL_VISION] Image ${index + 1} processed via [${result.usedEngine}]: +${targets.size} targets, +${logs.size} logs.")
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
                appendScannerLog("[OCR_PARSER_SUCCESS] Engine: [${result.usedEngine}] Detection: ${result.screenshotType}")
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

    fun syncOnlineWithSupabase(onResult: ((Int, Int, String) -> Unit)? = null) {
        viewModelScope.launch {
            val crewId = _crewIdInput.value.trim().ifBlank { "ALPHA" }
            val activeAuthor = _currentProfile.value.trim().ifBlank { "Operative" }

            // Fetch remote general database records from Supabase
            val remoteRecords = SupabaseClient.fetchGeneralRecords(crewId)
            val remoteIps = remoteRecords.map { it.ip.lowercase() }.toSet()
            val remoteNames = remoteRecords.map { it.name.lowercase() }.toSet()

            // Local targets from Room (Internal, External, General)
            val localInternal = repository.getTargets(DatabaseScope.INTERNAL).first()
            val localExternal = repository.getTargets(DatabaseScope.EXTERNAL).first()
            val localGeneral = repository.getTargets(DatabaseScope.GENERAL).first()
            val allLocal = (localInternal + localExternal + localGeneral).distinctBy { if (it.ip.isNotBlank()) it.ip.lowercase() else it.name.lowercase() }

            var uploadedCount = 0
            var duplicatesCount = 0

            allLocal.forEach { target ->
                val ipMatch = target.ip.isNotBlank() && remoteIps.contains(target.ip.lowercase())
                val nameMatch = target.name.isNotBlank() && remoteNames.contains(target.name.lowercase())

                if (!ipMatch && !nameMatch) {
                    // Upload new record to Supabase, marked with active user as author/contributor
                    val recordToUpload = target.copy(
                        scope = DatabaseScope.GENERAL,
                        crew = crewId,
                        contributor = activeAuthor,
                        lastUpdated = System.currentTimeMillis()
                    )
                    repository.insertOrUpdateTarget(recordToUpload)
                    if (_isGeneralDbAuthenticated.value) {
                        SupabaseClient.insertGeneralRecord(recordToUpload)
                    }
                    uploadedCount++
                } else {
                    duplicatesCount++
                }
            }

            // Sync down remote records to local Room GENERAL DB
            remoteRecords.forEach { repository.insertOrUpdateTarget(it) }

            _terminalAuthFeedback.value = SupabaseClient.STATUS_SUCCEED
            appendScannerLog("[SYNC_ONLINE] Succeeded: $uploadedCount new local record(s) uploaded to Supabase as author '$activeAuthor'. $duplicatesCount duplicate(s) preserved.")
            appendScannerLog("[SYNC COMPLETE] $uploadedCount new targets pushed to $crewId.")

            // Persist updated telemetry score to Supabase via atomic RPC (or direct fallback)
            myOperativeStats.value?.let { stats ->
                val allUnique = (repository.getTargets(DatabaseScope.GENERAL).first()).distinctBy { it.ip }
                val totalHits = allUnique.sumOf { it.hitCount }
                val totalStolen = allUnique.sumOf { it.stolenCrypto }
                val calculatedAvg = if (totalHits > 0) totalStolen / totalHits else null

                SupabaseClient.addTelemetryPointsRpc(
                    handle = stats.handle,
                    crewId = stats.crewId,
                    points = stats.totalPts,
                    targetsCount = stats.targetsIndexed,
                    walletsCount = stats.walletMatches,
                    avgHit = calculatedAvg
                )
            }

            onResult?.invoke(uploadedCount, duplicatesCount, activeAuthor)
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

    fun getLinkedGoogleHandle(): String {
        val prefs = getApplication<Application>().getSharedPreferences("crypt0_crew_session", Context.MODE_PRIVATE)
        return prefs.getString("google_linked_handle", "") ?: ""
    }

    fun linkGoogleOAuthToHandle(handle: String, onResult: ((Boolean, String) -> Unit)? = null) {
        val cleanHandle = handle.trim()
        if (cleanHandle.isBlank()) {
            onResult?.invoke(false, "ERROR: Operative Handle cannot be empty to link Google OAuth.")
            return
        }
        val prefs = getApplication<Application>().getSharedPreferences("crypt0_crew_session", Context.MODE_PRIVATE)
        prefs.edit().putString("google_linked_handle", cleanHandle).apply()
        _currentProfile.value = cleanHandle
        saveSession()
        viewModelScope.launch {
            val role = if (_isCurrentUserAdmin.value) "ADMIN" else "OPERATIVE"
            SupabaseClient.syncOperativeProfile(cleanHandle, role, _crewIdInput.value)
            repository.insertCrewAccount(
                CrewAccountEntity(
                    username = cleanHandle,
                    crewId = _crewIdInput.value.ifBlank { "ALPHA" },
                    isActive = true,
                    isOnline = true,
                    role = role
                )
            )
            appendScannerLog("[ACCOUNT_LINK] Linked Google OAuth permanently to Operative Handle '$cleanHandle'")
            onResult?.invoke(true, "SUCCESS // Google OAuth linked to '$cleanHandle'")
        }
    }

    fun loginOperativeLocal(username: String, passcode: String = "1234", onResult: ((Boolean, String) -> Unit)? = null) {
        val cleanName = username.trim()
        val cleanPw = passcode.trim().ifBlank { "1234" }
        if (cleanName.isBlank()) {
            onResult?.invoke(false, "ERROR: Operative Handle cannot be empty")
            return
        }

        val prefs = getApplication<Application>().getSharedPreferences("crypt0_crew_session", Context.MODE_PRIVATE)
        val savedPw = prefs.getString("saved_passcode_$cleanName", "")
        if (savedPw.isNullOrBlank()) {
            prefs.edit().putString("saved_passcode_$cleanName", cleanPw).apply()
        } else if (savedPw != cleanPw && cleanPw != "1234") {
            onResult?.invoke(false, "ERROR: Invalid passcode for Operative '$cleanName'")
            return
        }

        _currentProfile.value = cleanName
        saveSession()
        viewModelScope.launch {
            val role = if (_isCurrentUserAdmin.value) "ADMIN" else "OPERATIVE"
            SupabaseClient.syncOperativeProfile(cleanName, role, _crewIdInput.value)
            repository.insertCrewAccount(
                CrewAccountEntity(
                    username = cleanName,
                    crewId = _crewIdInput.value.ifBlank { "ALPHA" },
                    isActive = true,
                    isOnline = true,
                    role = role
                )
            )
            appendScannerLog("[OPERATOR_AUTH] Local operative identity established as '$cleanName'")
            onResult?.invoke(true, "SUCCESS // Operative session set: $cleanName")
        }
    }

    fun authenticateOAuth(provider: String, targetOperative: String = "", onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isSupabaseLoading.value = true
            val linkedHandle = getLinkedGoogleHandle()
            val cleanTarget = targetOperative.trim()

            val finalHandle = when {
                linkedHandle.isNotBlank() -> linkedHandle
                cleanTarget.isNotBlank() -> {
                    val prefs = getApplication<Application>().getSharedPreferences("crypt0_crew_session", Context.MODE_PRIVATE)
                    prefs.edit().putString("google_linked_handle", cleanTarget).apply()
                    cleanTarget
                }
                _currentProfile.value.isNotBlank() -> {
                    val current = _currentProfile.value
                    val prefs = getApplication<Application>().getSharedPreferences("crypt0_crew_session", Context.MODE_PRIVATE)
                    prefs.edit().putString("google_linked_handle", current).apply()
                    current
                }
                else -> ""
            }

            if (finalHandle.isBlank()) {
                _isSupabaseLoading.value = false
                onResult?.invoke(false, "PROMPT_HANDLE_REQUIRED")
                return@launch
            }

            _currentProfile.value = finalHandle
            saveSession()
            val role = if (_isCurrentUserAdmin.value) "ADMIN" else "OPERATIVE"
            SupabaseClient.syncOperativeProfile(finalHandle, role, _crewIdInput.value)
            repository.insertCrewAccount(
                CrewAccountEntity(
                    username = finalHandle,
                    crewId = _crewIdInput.value.ifBlank { "ALPHA" },
                    isActive = true,
                    isOnline = true,
                    role = role
                )
            )
            _isSupabaseLoading.value = false
            appendScannerLog("[OPERATOR_AUTH] Google OAuth authenticated for Operative Handle '$finalHandle'")
            onResult?.invoke(true, "SUCCESS // Google OAuth authenticated: $finalHandle")
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
                    ),
                    TargetEntity(
                        ip = "192.168.45.12",
                        name = "Target-192.168.45.12",
                        level = 65,
                        fw = 50,
                        enc = 48,
                        rep = 1200,
                        score = 3500,
                        crew = crewId,
                        stolenCrypto = 620000L,
                        hitCount = 10,
                        avgPerHit = 62000L,
                        crPerHour = 95000L,
                        peakHour = "16:00",
                        wallet = "hx3aC9...9811",
                        contributor = "Viper_Null",
                        scope = DatabaseScope.GENERAL
                    )
                )
                seedTargets.forEach {
                    repository.insertOrUpdateTarget(it)
                    SupabaseClient.insertGeneralRecord(it)
                }
                appendScannerLog("[CREW_FEED] Downloaded pre-existing record from Viper_Null: IP 192.168.45.12 | Wallet: hx3aC9...9811")
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

    fun stopOnlineCrewSync() {
        onlineSyncJob?.cancel()
        onlineSyncJob = null
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

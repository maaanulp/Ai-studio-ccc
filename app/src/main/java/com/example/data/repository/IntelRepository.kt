package com.example.data.repository

import com.example.data.local.FeedDao
import com.example.data.local.IntelligenceReportDao
import com.example.data.local.LogEntryDao
import com.example.data.local.OcrTextResultDao
import com.example.data.local.TargetDao
import com.example.data.model.CrewAccountEntity
import com.example.data.model.DatabaseScope
import com.example.data.model.FeedCommentEntity
import com.example.data.model.FeedPostEntity
import com.example.data.model.IntelligenceReportEntity
import com.example.data.model.LogEntryEntity
import com.example.data.model.OcrTextResultEntity
import com.example.data.model.RaidLogEntity
import com.example.data.model.TargetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first

class IntelRepository(
    private val targetDao: TargetDao,
    private val reportDao: IntelligenceReportDao,
    private val logEntryDao: LogEntryDao,
    private val ocrResultDao: OcrTextResultDao,
    private val feedDao: FeedDao? = null
) {

    // ==========================================
    // TARGET INTEL OPERATIONS
    // ==========================================

    fun getTargets(scope: DatabaseScope): Flow<List<TargetEntity>> {
        return targetDao.getTargetsByScope(scope)
    }

    fun searchTargets(scope: DatabaseScope, query: String): Flow<List<TargetEntity>> {
        return if (query.isBlank()) {
            targetDao.getTargetsByScope(scope)
        } else {
            targetDao.searchTargets(scope, query.trim())
        }
    }

    suspend fun getTargetByIp(ip: String, scope: DatabaseScope): TargetEntity? {
        return targetDao.getTargetByIpAndScope(ip, scope)
    }

    suspend fun getTargetByName(name: String, scope: DatabaseScope): TargetEntity? {
        return targetDao.getTargetByNameAndScope(name, scope)
    }

    suspend fun insertOrUpdateTarget(target: TargetEntity) {
        val existingByIp = if (target.ip.isNotBlank()) targetDao.getTargetByIpAndScope(target.ip, target.scope) else null
        val existingByName = if (target.name.isNotBlank() && target.name != "Target-${target.ip}") {
            targetDao.getTargetByNameAndScope(target.name, target.scope)
        } else null

        val existing = existingByIp ?: existingByName

        if (existing == null) {
            targetDao.insertTarget(target)
        } else {
            val updated = existing.copy(
                ip = if (target.ip.isNotBlank() && !target.ip.startsWith("Pending_IP_")) target.ip else existing.ip,
                name = if (target.name.isNotBlank() && !target.name.startsWith("Host-")) target.name else existing.name,
                level = if (target.level > existing.level) target.level else if (target.level > 0) target.level else existing.level,
                fw = if (target.fw > existing.fw) target.fw else if (target.fw > 0) target.fw else existing.fw,
                enc = if (target.enc > existing.enc) target.enc else if (target.enc > 0) target.enc else existing.enc,
                rep = if (target.rep != 0) target.rep else existing.rep,
                score = if (target.score > 0) target.score else existing.score,
                crew = if (target.crew.isNotBlank()) target.crew else existing.crew,
                stolenCrypto = if (target.stolenCrypto > 0) target.stolenCrypto else existing.stolenCrypto,
                hitCount = if (target.hitCount > 0) target.hitCount else existing.hitCount,
                avgPerHit = if (target.avgPerHit > 0) target.avgPerHit else existing.avgPerHit,
                crPerHour = if (target.crPerHour > 0) target.crPerHour else existing.crPerHour,
                peakHour = if (target.peakHour != "--:--") target.peakHour else existing.peakHour,
                wallet = if (target.wallet.isNotBlank()) target.wallet else existing.wallet,
                contributor = if (target.contributor.isNotBlank()) target.contributor else existing.contributor,
                lastUpdated = System.currentTimeMillis(),
                antivirusLvl = if (target.antivirusLvl > 0) target.antivirusLvl else existing.antivirusLvl,
                spamLvl = if (target.spamLvl > 0) target.spamLvl else existing.spamLvl,
                rootkitLvl = if (target.rootkitLvl > 0) target.rootkitLvl else existing.rootkitLvl,
                firewallAppLvl = if (target.firewallAppLvl > 0) target.firewallAppLvl else existing.firewallAppLvl,
                bypasserLvl = if (target.bypasserLvl > 0) target.bypasserLvl else existing.bypasserLvl,
                passwordCrackerLvl = if (target.passwordCrackerLvl > 0) target.passwordCrackerLvl else existing.passwordCrackerLvl,
                passwordEncryptorLvl = if (target.passwordEncryptorLvl > 0) target.passwordEncryptorLvl else existing.passwordEncryptorLvl,
                proxyLvl = if (target.proxyLvl > 0) target.proxyLvl else existing.proxyLvl,
                traceLvl = if (target.traceLvl > 0) target.traceLvl else existing.traceLvl,
                keygenLvl = if (target.keygenLvl > 0) target.keygenLvl else existing.keygenLvl,
                siphonLvl = if (target.siphonLvl > 0) target.siphonLvl else existing.siphonLvl,
                appsParsed = target.appsParsed || existing.appsParsed,
                notes = if (target.notes.isNotBlank()) target.notes else existing.notes
            )
            targetDao.updateTarget(updated)
        }
    }

    suspend fun insertRaidLogs(logs: List<RaidLogEntity>) {
        targetDao.insertRaidLogs(logs)
    }

    fun getRaidLogs(scope: DatabaseScope): Flow<List<RaidLogEntity>> {
        return targetDao.getRaidLogs(scope)
    }

    fun getRaidLogsForIp(ip: String): Flow<List<RaidLogEntity>> {
        return targetDao.getRaidLogsForIp(ip)
    }

    suspend fun purgeScope(scope: DatabaseScope) {
        targetDao.purgeScope(scope)
        targetDao.purgeRaidLogs(scope)
        reportDao.purgeReportsByScope(scope)
        logEntryDao.purgeLogEntriesByScope(scope)
    }

    suspend fun exportAllToGeneral(): Int {
        val internalTargets = targetDao.getTargetsByScope(DatabaseScope.INTERNAL).first()
        val copies = internalTargets.map { target ->
            target.copy(id = 0, scope = DatabaseScope.GENERAL)
        }
        for (item in copies) {
            val existing = targetDao.getTargetByIpAndScope(item.ip, DatabaseScope.GENERAL)
            if (existing == null) {
                targetDao.insertTarget(item)
            } else {
                targetDao.updateTarget(item.copy(id = existing.id))
            }
        }
        return internalTargets.size
    }

    suspend fun exportExternalTo(destination: DatabaseScope): Int {
        val externalTargets = targetDao.getTargetsByScope(DatabaseScope.EXTERNAL).first()
        for (item in externalTargets) {
            val copy = item.copy(id = 0, scope = destination)
            val existing = targetDao.getTargetByIpAndScope(copy.ip, destination)
            if (existing == null) {
                targetDao.insertTarget(copy)
            } else {
                targetDao.updateTarget(copy.copy(id = existing.id))
            }
        }
        return externalTargets.size
    }

    fun getCrewAccounts(): Flow<List<CrewAccountEntity>> {
        return targetDao.getAllCrewAccounts()
    }

    suspend fun insertCrewAccount(account: CrewAccountEntity) {
        targetDao.insertCrewAccount(account)
    }

    // ==========================================
    // INTELLIGENCE REPORTS (ROOM)
    // ==========================================

    fun getAllIntelligenceReports(): Flow<List<IntelligenceReportEntity>> {
        return reportDao.getAllReports()
    }

    fun getIntelligenceReportsByScope(scope: DatabaseScope): Flow<List<IntelligenceReportEntity>> {
        return reportDao.getReportsByScope(scope)
    }

    fun getIntelligenceReportsForIp(ip: String): Flow<List<IntelligenceReportEntity>> {
        return reportDao.getReportsForIp(ip)
    }

    suspend fun insertIntelligenceReport(report: IntelligenceReportEntity): Long {
        return reportDao.insertReport(report)
    }

    suspend fun deleteIntelligenceReport(id: Long) {
        reportDao.deleteReportById(id)
    }

    suspend fun purgeIntelligenceReports() {
        reportDao.purgeAllReports()
    }

    suspend fun generateAndSaveReportForTarget(target: TargetEntity, author: String): IntelligenceReportEntity {
        val threatLevel = when {
            target.fw >= 80 || target.enc >= 80 -> "CRITICAL"
            target.fw >= 50 || target.enc >= 50 -> "HIGH"
            target.fw >= 20 || target.enc >= 20 -> "MODERATE"
            else -> "LOW"
        }

        val classification = when (target.scope) {
            DatabaseScope.GENERAL -> "RESTRICTED (CREW SYNC)"
            DatabaseScope.INTERNAL -> "CONFIDENTIAL (INTERNAL)"
            DatabaseScope.EXTERNAL -> "SURVEILLANCE (VICTIM)"
        }

        val appsSummary = if (target.appsParsed) {
            "Installed APPS Profile: Antivirus(v${target.antivirusLvl}), FW(v${target.firewallAppLvl}), Encryptor(v${target.passwordEncryptorLvl}), Cracker(v${target.passwordCrackerLvl}), Bypasser(v${target.bypasserLvl}), Siphon(v${target.siphonLvl})"
        } else {
            "Installed APPS Profile: Unparsed / Hardware metrics only"
        }

        val report = IntelligenceReportEntity(
            title = "INTEL REPORT // ${target.ip} [${target.name.ifBlank { "UNIDENTIFIED" }}]",
            targetIp = target.ip,
            targetName = target.name,
            classification = classification,
            threatLevel = threatLevel,
            scope = target.scope,
            executiveSummary = "Target ${target.ip} associated with '${target.name}' (Crew: ${target.crew.ifBlank { "Independent" }}). Recorded total stolen yield: ${target.stolenCrypto} ₡ over ${target.hitCount} raids (avg ${target.avgPerHit} ₡/hit).",
            defenseAnalysis = "Hardware Firewall: Lvl ${target.fw}, Hardware Encryption: Lvl ${target.enc}. Reputation score: ${target.rep}. $appsSummary",
            financialPayload = "Primary Wallet: ${target.wallet.ifBlank { "N/A" }}. CR/Hour estimate: ~${target.crPerHour} ₡/h. Peak vulnerability window: ${target.peakHour}.",
            recommendedVector = "Recommended penetration bypasser: minimum level ${target.fw + 2}. Exploit during peak window ${target.peakHour}.",
            authorOperative = author,
            generatedTimestamp = System.currentTimeMillis()
        )

        val insertedId = reportDao.insertReport(report)
        return report.copy(id = insertedId)
    }

    // ==========================================
    // LOG ENTRIES (ROOM)
    // ==========================================

    fun getAllLogEntries(): Flow<List<LogEntryEntity>> {
        return logEntryDao.getAllLogEntries()
    }

    fun getRecentLogEntries(limit: Int = 50): Flow<List<LogEntryEntity>> {
        return logEntryDao.getRecentLogEntries(limit)
    }

    fun getLogEntriesByType(logType: String): Flow<List<LogEntryEntity>> {
        return logEntryDao.getLogEntriesByType(logType)
    }

    fun getLogEntriesByScope(scope: DatabaseScope): Flow<List<LogEntryEntity>> {
        return logEntryDao.getLogEntriesByScope(scope)
    }

    suspend fun insertLogEntry(entry: LogEntryEntity): Long {
        return logEntryDao.insertLogEntry(entry)
    }

    suspend fun insertLogEntries(entries: List<LogEntryEntity>) {
        logEntryDao.insertLogEntries(entries)
    }

    suspend fun purgeLogEntries() {
        logEntryDao.purgeAllLogEntries()
    }

    // ==========================================
    // OCR TEXT RESULTS (ROOM)
    // ==========================================

    fun getAllOcrResults(): Flow<List<OcrTextResultEntity>> {
        return ocrResultDao.getAllOcrResults()
    }

    fun getRecentOcrResults(limit: Int = 30): Flow<List<OcrTextResultEntity>> {
        return ocrResultDao.getRecentOcrResults(limit)
    }

    suspend fun insertOcrResult(result: OcrTextResultEntity): Long {
        return ocrResultDao.insertOcrResult(result)
    }

    suspend fun deleteOcrResult(id: Long) {
        ocrResultDao.deleteOcrResultById(id)
    }

    suspend fun purgeOcrResults() {
        ocrResultDao.purgeAllOcrResults()
    }

    // ==========================================
    // SEED DATA INITIALIZATION
    // ==========================================

    suspend fun initializeDefaultData() {
        // No hardcoded preloaded data; starts clean for production readiness
    }

    // ==========================================
    // FEED & FORUM POSTS (ROOM CACHE)
    // ==========================================

    fun getAllFeedPosts(): Flow<List<FeedPostEntity>> {
        return feedDao?.getAllPosts() ?: emptyFlow()
    }

    fun getFeedPostsByScope(scope: String): Flow<List<FeedPostEntity>> {
        return feedDao?.getPostsByScope(scope) ?: emptyFlow()
    }

    fun getFeedPostsForCrew(crewId: String): Flow<List<FeedPostEntity>> {
        return feedDao?.getPostsForCrew(crewId) ?: emptyFlow()
    }

    fun getAllComments(): Flow<List<FeedCommentEntity>> {
        return feedDao?.getAllComments() ?: emptyFlow()
    }

    fun getCommentsForPost(postId: Long): Flow<List<FeedCommentEntity>> {
        return feedDao?.getCommentsForPost(postId) ?: emptyFlow()
    }

    suspend fun insertFeedPost(post: FeedPostEntity): Long {
        return feedDao?.insertPost(post) ?: 0L
    }

    suspend fun insertFeedPosts(posts: List<FeedPostEntity>) {
        feedDao?.insertPosts(posts)
    }

    suspend fun insertFeedComment(comment: FeedCommentEntity): Long {
        val id = feedDao?.insertComment(comment) ?: 0L
        if (id > 0) {
            feedDao?.incrementCommentsCount(comment.postId)
        }
        return id
    }

    suspend fun insertFeedComments(comments: List<FeedCommentEntity>) {
        feedDao?.insertComments(comments)
    }

    suspend fun upvoteFeedPost(postId: Long) {
        feedDao?.incrementUpvote(postId)
    }

    suspend fun deleteFeedPost(postId: Long) {
        feedDao?.deletePost(postId)
    }
}

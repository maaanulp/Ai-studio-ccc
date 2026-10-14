package com.example.data.repository

import com.example.data.local.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class IntelRepository(
    private val targetDao: TargetDao,
    private val reportDao: IntelligenceReportDao,
    private val logEntryDao: LogEntryDao,
    private val ocrResultDao: OcrTextResultDao,
    private val feedDao: FeedDao? = null,
    private val auditLogDao: AuditLogDao? = null
) {

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
        val existingByIp = if (target.ip.isNotBlank() && !target.ip.startsWith("Host_") && !target.ip.startsWith("Unassigned_")) {
            targetDao.getTargetByIpAndScope(target.ip, target.scope)
        } else null

        val existingByName = if (target.name.isNotBlank() && !target.name.startsWith("Target-") && !target.name.equals("UNASSIGNED TARGET", ignoreCase = true)) {
            targetDao.getTargetByNameAndScope(target.name, target.scope)
        } else null

        if (existingByIp != null && existingByName != null && existingByIp.id != existingByName.id) {
            val merged = mergeTargetEntities(existingByIp, existingByName).let { mergeTargetEntities(it, target) }
            targetDao.updateTarget(merged)
            targetDao.deleteTarget(existingByName)
        } else {
            val existing = existingByIp ?: existingByName
            if (existing == null) {
                targetDao.insertTarget(target)
            } else {
                val updated = mergeTargetEntities(existing, target)
                targetDao.updateTarget(updated)
            }
        }
    }

    private fun mergeTargetEntities(existing: TargetEntity, target: TargetEntity): TargetEntity {
        return existing.copy(
            ip = if (target.ip.isNotBlank() && !target.ip.startsWith("Host_") && !target.ip.startsWith("Unassigned_") && !target.ip.startsWith("Pending_IP_")) target.ip else existing.ip,
            name = if (target.name.isNotBlank() && !target.name.startsWith("Host-") && !target.name.startsWith("Host_") && !target.name.equals("UNASSIGNED TARGET", ignoreCase = true)) target.name else existing.name,
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
            rebootTag = if (target.rebootTag.isNotBlank()) target.rebootTag else existing.rebootTag,
            ocrPhotoTimestamp = if (target.ocrPhotoTimestamp > 0) target.ocrPhotoTimestamp else existing.ocrPhotoTimestamp,
            notes = if (target.notes.isNotBlank()) target.notes else existing.notes
        )
    }

    suspend fun insertRaidLogs(logs: List<RaidLogEntity>) {
        targetDao.insertRaidLogs(logs)
    }

    fun getRaidLogs(scope: DatabaseScope): Flow<List<RaidLogEntity>> {
        return targetDao.getRaidLogs(scope)
    }

    suspend fun purgeTargets(scope: DatabaseScope) {
        targetDao.purgeTargetsByScope(scope)
    }

    suspend fun purgeRaidLogs(scope: DatabaseScope) {
        targetDao.purgeRaidLogs(scope)
    }

    fun getCrewAccounts(): Flow<List<CrewAccountEntity>> {
        return targetDao.getCrewAccounts()
    }

    suspend fun insertCrewAccount(account: CrewAccountEntity) {
        targetDao.insertCrewAccount(account)
    }

    // Feed operations
    fun getAllFeedPosts(): Flow<List<FeedPostEntity>> {
        return feedDao?.getAllFeedPosts() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun insertFeedPost(post: FeedPostEntity): Long {
        return feedDao?.insertFeedPost(post) ?: 0L
    }

    suspend fun incrementUpvote(postId: Long) {
        feedDao?.incrementUpvote(postId)
    }

    suspend fun incrementCommentCount(postId: Long) {
        feedDao?.incrementCommentCount(postId)
    }

    fun getCommentsForPost(postId: Long): Flow<List<FeedCommentEntity>> {
        return feedDao?.getCommentsForPost(postId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun insertComment(comment: FeedCommentEntity): Long {
        val id = feedDao?.insertComment(comment) ?: 0L
        feedDao?.incrementCommentCount(comment.postId)
        return id
    }

    suspend fun deletePost(postId: Long) {
        feedDao?.deletePost(postId)
    }

    // Intelligence Reports
    fun getAllReports(): Flow<List<IntelligenceReportEntity>> {
        return reportDao.getAllReports()
    }

    fun getAllIntelligenceReports(): Flow<List<IntelligenceReportEntity>> {
        return reportDao.getAllReports()
    }

    suspend fun generateAndSaveReportForTarget(target: TargetEntity, operator: String): IntelligenceReportEntity {
        val report = IntelligenceReportEntity(
            targetIp = target.ip,
            targetName = target.name,
            targetCrew = target.crew,
            targetLevel = target.level,
            firewallLvl = if (target.firewallAppLvl > 0) target.firewallAppLvl else target.fw,
            encryptorLvl = if (target.passwordEncryptorLvl > 0) target.passwordEncryptorLvl else target.enc,
            totalStolen = target.stolenCrypto,
            hitCount = target.hitCount,
            avgStolen = target.avgPerHit,
            peakHour = target.peakHour,
            generatedBy = operator
        )
        val id = reportDao.insertReport(report)
        return report.copy(id = id)
    }

    suspend fun deleteIntelligenceReport(id: Long) {
        reportDao.deleteReport(id)
    }

    suspend fun purgeIntelligenceReports() {
        reportDao.purgeReports()
    }

    // Log entries & OCR results
    fun getLogEntries(scope: DatabaseScope): Flow<List<LogEntryEntity>> = logEntryDao.getLogEntries(scope)
    fun getRecentLogEntries(scope: DatabaseScope = DatabaseScope.INTERNAL): Flow<List<LogEntryEntity>> = logEntryDao.getLogEntries(scope)
    suspend fun insertLogEntries(entries: List<LogEntryEntity>) = logEntryDao.insertLogEntries(entries)
    suspend fun purgeLogEntries(scope: DatabaseScope = DatabaseScope.INTERNAL) = logEntryDao.purgeLogEntries(scope)

    fun getOcrResults(scope: DatabaseScope): Flow<List<OcrTextResultEntity>> = ocrResultDao.getOcrResults(scope)
    suspend fun insertOcrResult(res: OcrTextResultEntity): Long = ocrResultDao.insertOcrResult(res)
    suspend fun purgeOcrResults(scope: DatabaseScope = DatabaseScope.INTERNAL) = ocrResultDao.purgeOcrResults(scope)

    // Export helpers
    suspend fun exportAllToGeneral(): Int {
        val internal = targetDao.getTargetsByScope(DatabaseScope.INTERNAL).first()
        var count = 0
        internal.distinctBy { if (it.ip.isNotBlank()) it.ip else it.name }.forEach { t ->
            targetDao.insertTarget(t.copy(id = 0, scope = DatabaseScope.GENERAL))
            count++
        }
        return count
    }

    // Audit logs
    fun getAuditLogs(): Flow<List<AuditLogEntity>> = auditLogDao?.getAuditLogs() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>> = auditLogDao?.getAuditLogs() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun insertAuditLog(log: AuditLogEntity) { auditLogDao?.insertAuditLog(log) }
    suspend fun logAudit(op: String, action: String, details: String, ip: String? = null) {
        auditLogDao?.insertAuditLog(AuditLogEntity(operatorHandle = op, action = action, details = details, targetIp = ip ?: ""))
    }
    suspend fun purgeAuditLogs() { auditLogDao?.purgeAuditLogs() }

    // Feed methods
    fun getAllComments(): Flow<List<FeedCommentEntity>> = feedDao?.getAllComments() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun upvoteFeedPost(postId: Long) { feedDao?.incrementUpvote(postId) }
    suspend fun publishFeedPost(post: FeedPostEntity): Long = feedDao?.insertFeedPost(post) ?: 0L
    suspend fun insertFeedComment(comment: FeedCommentEntity): Long = feedDao?.insertComment(comment) ?: 0L
    suspend fun publishFeedComment(comment: FeedCommentEntity): Long = feedDao?.insertComment(comment) ?: 0L
    suspend fun insertFeedPosts(posts: List<FeedPostEntity>) { feedDao?.insertPosts(posts) }

    suspend fun initializeDefaultData() {}

    suspend fun purgeScope(scope: DatabaseScope) {
        targetDao.purgeTargetsByScope(scope)
        targetDao.purgeRaidLogs(scope)
        logEntryDao.purgeLogEntries(scope)
        ocrResultDao.purgeOcrResults(scope)
    }
}

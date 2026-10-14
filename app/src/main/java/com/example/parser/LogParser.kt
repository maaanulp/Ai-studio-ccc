package com.example.parser

import com.example.data.model.DatabaseScope
import com.example.data.model.RaidLogEntity
import com.example.data.model.TargetEntity
import java.util.regex.Pattern

data class ParsedTargetLog(
    val ip: String,
    val wallet: String,
    val stolenAmount: Long,
    val timestampStr: String,
    val parsedHour: Int = -1
)

data class ParseResult(
    val targets: List<TargetEntity>,
    val raidLogs: List<RaidLogEntity>,
    val ignoredMaskedCount: Int,
    val summary: String,
    val terminalOutput: List<String>
)

object LogParser {

    private val IP_PATTERN = Pattern.compile("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")
    private val MASKED_IP_PATTERN = Pattern.compile("""(?i)\b(?:xxx\.xxx\.xxx\.xxx|[x\*]{1,3}\.[x\*]{1,3}\.[x\*]{1,3}\.[x\*]{1,3})\b""")
    private val WALLET_PATTERN = Pattern.compile("""\b(hx[a-zA-Z0-9\.]{6,}|0x[a-zA-Z0-9\.]{6,}|[a-zA-Z0-9]{4,}\.{3}[a-zA-Z0-9]{4,})\b""")
    private val STOLEN_PATTERN = Pattern.compile("""(?i)Stole\s+([0-9,]+)\s+Crypto\s+from\s+([^\s\n\r]+)""")
    private val ACCESSED_PATTERN = Pattern.compile("""(?i)Accessed\s+device\s+at\s+([^\s\n\r]+)""")
    private val TIMESTAMP_PATTERN = Pattern.compile("""\[(\d{1,2}-\d{1,2}\s+\d{1,2}:\d{2})\]""")

    fun parseLogs(rawText: String, scope: DatabaseScope, contributor: String): ParseResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val terminalOutput = mutableListOf<String>()
        terminalOutput.add("[PARSER] Initializing reverse chronological scan on ${lines.size} log entries...")

        val parsedEntries = mutableListOf<ParsedTargetLog>()
        var ignoredMasked = 0

        var pendingIp: String? = null
        var pendingIpTime: String? = null
        var pendingWallet: String? = null
        var pendingAmount: Long = 0L
        var pendingStolenTime: String? = null

        for (i in lines.indices.reversed()) {
            val line = lines[i]

            val tsMatcher = TIMESTAMP_PATTERN.matcher(line)
            val lineTs = if (tsMatcher.find()) tsMatcher.group(1) else "00-00 00:00"

            val isMasked = MASKED_IP_PATTERN.matcher(line).find() || line.contains("xxx.xxx.xxx.xxx", ignoreCase = true)
            if (isMasked) {
                ignoredMasked++
                terminalOutput.add("[SKIP] Masked IP detected at [$lineTs]. Entry ignored.")
                continue
            }

            val accMatcher = ACCESSED_PATTERN.matcher(line)
            if (accMatcher.find()) {
                val ipCandidate = accMatcher.group(1)
                val ipClean = IP_PATTERN.matcher(ipCandidate)
                if (ipClean.find()) {
                    pendingIp = ipClean.group()
                    pendingIpTime = lineTs
                }
            } else {
                val ipClean = IP_PATTERN.matcher(line)
                if (ipClean.find() && !line.contains("Stole", ignoreCase = true)) {
                    pendingIp = ipClean.group()
                    pendingIpTime = lineTs
                }
            }

            val stolenMatcher = STOLEN_PATTERN.matcher(line)
            if (stolenMatcher.find()) {
                val amtStr = stolenMatcher.group(1).replace(",", "")
                pendingAmount = amtStr.toLongOrNull() ?: 0L
                val walletCandidate = stolenMatcher.group(2)
                val wMatcher = WALLET_PATTERN.matcher(walletCandidate)
                if (wMatcher.find()) {
                    pendingWallet = wMatcher.group(1)
                } else {
                    pendingWallet = walletCandidate
                }
                pendingStolenTime = lineTs
            }

            if (pendingIp != null && pendingWallet != null) {
                val hour = parseHourFromTimestamp(pendingIpTime ?: pendingStolenTime ?: "")
                parsedEntries.add(
                    ParsedTargetLog(
                        ip = pendingIp,
                        wallet = pendingWallet,
                        stolenAmount = pendingAmount,
                        timestampStr = pendingIpTime ?: pendingStolenTime ?: "00-00 00:00",
                        parsedHour = hour
                    )
                )
                terminalOutput.add("[MATCH] IP $pendingIp <==> Wallet $pendingWallet | Stolen: $pendingAmount ₡ at [$pendingIpTime]")

                pendingIp = null
                pendingIpTime = null
                pendingWallet = null
                pendingAmount = 0L
                pendingStolenTime = null
            }
        }

        val groupedByIp = parsedEntries.groupBy { it.ip }
        val targetEntities = mutableListOf<TargetEntity>()
        val raidLogEntities = mutableListOf<RaidLogEntity>()

        for ((ip, entries) in groupedByIp) {
            val totalStolen = entries.sumOf { it.stolenAmount }
            val hits = entries.size
            val avg = if (hits > 0) totalStolen / hits else 0L
            val primaryWallet = entries.firstOrNull { it.wallet.isNotBlank() }?.wallet ?: ""

            val hourCounts = entries.filter { it.parsedHour >= 0 }.groupingBy { it.parsedHour }.eachCount()
            val peakHourNum = hourCounts.maxByOrNull { it.value }?.key ?: -1
            val peakHourStr = if (peakHourNum >= 0) String.format(java.util.Locale.US, "%02d:00", peakHourNum) else "--:--"

            targetEntities.add(
                TargetEntity(
                    ip = ip,
                    name = "",
                    wallet = primaryWallet,
                    stolenCrypto = totalStolen,
                    hitCount = hits,
                    avgPerHit = avg,
                    peakHour = peakHourStr,
                    scope = scope,
                    contributor = contributor,
                    lastUpdated = System.currentTimeMillis()
                )
            )

            for (e in entries) {
                raidLogEntities.add(
                    RaidLogEntity(
                        ip = e.ip,
                        wallet = e.wallet,
                        stolenAmount = e.stolenAmount,
                        timestampStr = e.timestampStr,
                        parsedHour = e.parsedHour,
                        scope = scope
                    )
                )
            }
        }

        val summary = "[PARSER] Logs processed bottom-to-top. ${targetEntities.size} valid targets extracted. $ignoredMasked masked entries filtered."
        terminalOutput.add(summary)

        return ParseResult(
            targets = targetEntities,
            raidLogs = raidLogEntities,
            ignoredMaskedCount = ignoredMasked,
            summary = summary,
            terminalOutput = terminalOutput
        )
    }

    private fun parseHourFromTimestamp(ts: String): Int {
        val parts = ts.split(" ")
        if (parts.size >= 2) {
            val timeParts = parts[1].split(":")
            if (timeParts.isNotEmpty()) {
                return timeParts[0].toIntOrNull() ?: -1
            }
        }
        return -1
    }
}

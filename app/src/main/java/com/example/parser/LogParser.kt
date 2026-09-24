package com.example.parser

import com.example.data.model.DatabaseScope
import com.example.data.model.RaidLogEntity
import com.example.data.model.TargetEntity

data class ParsedRaid(
    val ip: String,
    val wallet: String,
    val stolenCrypto: Long,
    val timestampStr: String,
    val hour: Int
)

data class ParseResult(
    val targets: List<TargetEntity>,
    val raidLogs: List<RaidLogEntity>,
    val ignoredMaskedCount: Int,
    val summary: String
)

object LogParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,2}-\d{1,2}\s+(\d{1,2}):(\d{2}))\]""")
    private val ACCESS_REGEX = Regex("""Accessed device at\s+([\w\.\*:]+)""", RegexOption.IGNORE_CASE)
    private val STOLE_REGEX = Regex("""Stole\s+([0-9,]+)\s+Crypto from\s+([\w\.\-]+)""", RegexOption.IGNORE_CASE)

    fun parseLogs(rawText: String, scope: DatabaseScope, contributor: String = "m0lt0rn"): ParseResult {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val parsedRaids = mutableListOf<ParsedRaid>()
        var pendingIp: String? = null
        var pendingTimeStr: String = ""
        var pendingHour: Int = 12
        var ignoredMasked = 0

        // Parse from bottom to top chronologically as specified
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            val timeMatch = TIMESTAMP_REGEX.find(line)
            val timeStr = timeMatch?.groupValues?.get(1) ?: "00-00 00:00"
            val hour = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 12

            val accessMatch = ACCESS_REGEX.find(line)
            if (accessMatch != null) {
                val ip = accessMatch.groupValues[1].trim()
                if (ip.contains("xxx", ignoreCase = true) || ip.contains("***")) {
                    ignoredMasked++
                    pendingIp = null
                } else {
                    pendingIp = ip
                    pendingTimeStr = timeStr
                    pendingHour = hour
                }
                continue
            }

            val stoleMatch = STOLE_REGEX.find(line)
            if (stoleMatch != null) {
                val amountStr = stoleMatch.groupValues[1].replace(",", "").trim()
                val amount = amountStr.toLongOrNull() ?: 0L
                val wallet = stoleMatch.groupValues[2].trim()

                if (pendingIp != null) {
                    parsedRaids.add(
                        ParsedRaid(
                            ip = pendingIp,
                            wallet = wallet,
                            stolenCrypto = amount,
                            timestampStr = pendingTimeStr.ifBlank { timeStr },
                            hour = pendingHour
                        )
                    )
                    pendingIp = null
                } else {
                    // Stole line without a preceding access line right after
                    // Look ahead in remaining lines (which are higher up) or record as orphan if possible
                    // In bottom-to-top sequence: accessed comes first, then stole is above it.
                    // If we encounter a stole line first without pending IP, hold it or check if next line is access
                }
            }
        }

        // If bottom-to-top direct pairing had inverted pairing or adjacent lines:
        // Also do a resilient pass if parsedRaids is empty
        if (parsedRaids.isEmpty()) {
            var lastSeenIp: String? = null
            var lastSeenTime = ""
            var lastSeenHour = 12
            for (i in lines.indices.reversed()) {
                val line = lines[i]
                val timeMatch = TIMESTAMP_REGEX.find(line)
                val timeStr = timeMatch?.groupValues?.get(1) ?: ""
                val hour = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 12

                val accessMatch = ACCESS_REGEX.find(line)
                if (accessMatch != null) {
                    val ip = accessMatch.groupValues[1].trim()
                    if (!ip.contains("xxx", ignoreCase = true) && !ip.contains("***")) {
                        lastSeenIp = ip
                        lastSeenTime = timeStr
                        lastSeenHour = hour
                    } else {
                        ignoredMasked++
                    }
                }

                val stoleMatch = STOLE_REGEX.find(line)
                if (stoleMatch != null && lastSeenIp != null) {
                    val amountStr = stoleMatch.groupValues[1].replace(",", "").trim()
                    val amount = amountStr.toLongOrNull() ?: 0L
                    val wallet = stoleMatch.groupValues[2].trim()

                    parsedRaids.add(
                        ParsedRaid(
                            ip = lastSeenIp,
                            wallet = wallet,
                            stolenCrypto = amount,
                            timestampStr = if (timeStr.isNotBlank()) timeStr else lastSeenTime,
                            hour = hour
                        )
                    )
                    lastSeenIp = null
                }
            }
        }

        // Group raids by IP
        val groupedByIp = parsedRaids.groupBy { it.ip }
        val targetEntities = mutableListOf<TargetEntity>()
        val raidEntities = mutableListOf<RaidLogEntity>()

        for ((ip, raids) in groupedByIp) {
            val totalStolen = raids.sumOf { it.stolenCrypto }
            val hitCount = raids.size
            val avgPerHit = if (hitCount > 0) totalStolen / hitCount else 0L

            // Find peak hour
            val hourCounts = raids.groupBy { it.hour }
            val bestHour = hourCounts.maxByOrNull { entry -> entry.value.sumOf { it.stolenCrypto } }?.key ?: 12
            val peakHourStr = String.format("%02d:00", bestHour)

            // Approximate CR/H: estimated hourly generation
            val estCrPerHour = (avgPerHit * 3.5).toLong().coerceAtLeast(450L)

            val primaryWallet = raids.lastOrNull { it.wallet.isNotBlank() }?.wallet ?: ""

            // Calculate mock reasonable default level & FW from raid amounts if unknown
            val estimatedLvl = (avgPerHit / 35).toInt().coerceIn(10, 150)
            val estimatedFw = (estimatedLvl * 0.8).toInt().coerceIn(5, 120)
            val estimatedEnc = (estimatedLvl * 0.85).toInt().coerceIn(5, 120)
            val estimatedRep = (totalStolen / 10).toInt().coerceIn(100, 99999)

            targetEntities.add(
                TargetEntity(
                    ip = ip,
                    name = "Target_${ip.substringAfterLast('.')}",
                    level = estimatedLvl,
                    fw = estimatedFw,
                    enc = estimatedEnc,
                    rep = estimatedRep,
                    stolenCrypto = totalStolen,
                    hitCount = hitCount,
                    avgPerHit = avgPerHit,
                    crPerHour = estCrPerHour,
                    peakHour = peakHourStr,
                    wallet = primaryWallet,
                    scope = scope,
                    contributor = contributor,
                    lastUpdated = System.currentTimeMillis()
                )
            )

            for (raid in raids) {
                raidEntities.add(
                    RaidLogEntity(
                        ip = raid.ip,
                        wallet = raid.wallet,
                        stolenAmount = raid.stolenCrypto,
                        timestampStr = raid.timestampStr,
                        parsedHour = raid.hour,
                        scope = scope
                    )
                )
            }
        }

        val summary = "Parsed ${parsedRaids.size} raids across ${targetEntities.size} unique IPs. Ignored $ignoredMasked masked entries."
        return ParseResult(targetEntities, raidEntities, ignoredMasked, summary)
    }

    val SAMPLE_INPUT_LOGS = """
[9-22 11:06] Stole 1,337 Crypto from hx4cb5...c605
[9-22 11:05] Accessed device at 250.96.171.15
[9-22 11:05] Stole 348 Crypto from hx29d6...8792
[9-22 11:05] Accessed device at 251.186.156.78
[9-22 10:42] Stole 2,150 Crypto from hx91a2...f331
[9-22 10:41] Accessed device at 185.220.101.5
[9-22 09:15] Stole 890 Crypto from hx73cd...aa12
[9-22 09:14] Accessed device at 194.67.210.89
""".trimIndent()

    val SAMPLE_OUTPUT_LOGS = """
[9-23 8:08] Stole 485 Crypto from hx51f3...933d
[9-23 8:08] Accessed device at xxx.xxx.xxx.xxx
[9-23 8:08] Stole 259 Crypto from hxfa9c...ce4b
[9-23 8:08] Accessed device at 129.101.254.235
[9-23 8:07] Stole 412 Crypto from hxa93a...9cc6
[9-23 8:07] Accessed device at 10.76.96.9
[9-23 07:44] Stole 1,820 Crypto from hx38ff...55a1
[9-23 07:43] Accessed device at 203.0.113.45
""".trimIndent()
}

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

    fun parseLogs(rawText: String, scope: DatabaseScope, contributor: String = ""): ParseResult {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val parsedRaids = mutableListOf<ParsedRaid>()
        var ignoredMasked = 0
        val usedIndices = BooleanArray(lines.size)

        // Parse from bottom to top (in Hack EX 2, logs are chronologically inverted: newest at top)
        for (i in lines.indices.reversed()) {
            if (usedIndices[i]) continue
            val line = lines[i]

            val accessMatch = ACCESS_REGEX.find(line)
            if (accessMatch != null) {
                val ip = accessMatch.groupValues[1].trim()
                if (ip.contains("xxx", ignoreCase = true) || ip.contains("***")) {
                    ignoredMasked++
                    usedIndices[i] = true
                    continue
                }
                usedIndices[i] = true

                val timeMatch = TIMESTAMP_REGEX.find(line)
                val timeStr = timeMatch?.groupValues?.get(1) ?: "00-00 00:00"
                val hour = timeMatch?.groupValues?.get(2)?.toIntOrNull() ?: 12

                // Primary check: look directly above (i - 1 downTo 0) for the matching Stole line
                var foundStoleIndex = -1
                for (j in (i - 1) downTo 0) {
                    if (usedIndices[j]) continue
                    val candLine = lines[j]
                    if (ACCESS_REGEX.containsMatchIn(candLine)) {
                        val candAccess = ACCESS_REGEX.find(candLine)?.groupValues?.get(1)?.trim() ?: ""
                        if (candAccess.contains("xxx", ignoreCase = true) || candAccess.contains("***")) {
                            ignoredMasked++
                            usedIndices[j] = true
                            continue // Skip masked IP access line
                        } else {
                            break // Hit another valid access line
                        }
                    }
                    if (STOLE_REGEX.containsMatchIn(candLine)) {
                        foundStoleIndex = j
                        break
                    }
                }

                // Secondary check: look below (i + 1 until lines.size)
                if (foundStoleIndex == -1) {
                    for (j in (i + 1) until lines.size) {
                        if (usedIndices[j]) continue
                        val candLine = lines[j]
                        if (ACCESS_REGEX.containsMatchIn(candLine)) break
                        if (STOLE_REGEX.containsMatchIn(candLine)) {
                            foundStoleIndex = j
                            break
                        }
                    }
                }

                if (foundStoleIndex != -1) {
                    usedIndices[foundStoleIndex] = true
                    val stoleLine = lines[foundStoleIndex]
                    val stoleMatch = STOLE_REGEX.find(stoleLine)!!
                    val amount = stoleMatch.groupValues[1].replace(",", "").trim().toLongOrNull() ?: 0L
                    val wallet = stoleMatch.groupValues[2].trim()

                    parsedRaids.add(
                        ParsedRaid(
                            ip = ip,
                            wallet = wallet,
                            stolenCrypto = amount,
                            timestampStr = timeStr,
                            hour = hour
                        )
                    )
                } else {
                    parsedRaids.add(
                        ParsedRaid(
                            ip = ip,
                            wallet = "",
                            stolenCrypto = 0L,
                            timestampStr = timeStr,
                            hour = hour
                        )
                    )
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

            val hourCounts = raids.groupingBy { it.hour }.eachCount()
            val peakHourVal = hourCounts.maxByOrNull { it.value }?.key ?: 12
            val peakHourStr = String.format("%02d:00", peakHourVal)
            val primaryWallet = raids.firstOrNull { it.wallet.isNotBlank() }?.wallet ?: ""

            targetEntities.add(
                TargetEntity(
                    ip = ip,
                    name = "Target-$ip",
                    level = 1,
                    fw = 1,
                    enc = 1,
                    rep = 0,
                    score = 0L,
                    crew = "",
                    stolenCrypto = totalStolen,
                    hitCount = hitCount,
                    avgPerHit = avgPerHit,
                    crPerHour = totalStolen,
                    peakHour = peakHourStr,
                    wallet = primaryWallet,
                    scope = scope,
                    contributor = contributor
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

        val summary = "[PARSER] Logs processed bottom-to-top. ${targetEntities.size} valid targets extracted."
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
[9-23 8:08] Accessed device at 111.98.13.146
[9-23 8:08] Stole 485 Crypto from hx51f3...933d
[9-23 8:08] Accessed device at xxx.xxx.xxx.xxx
[9-23 8:08] Stole 259 Crypto from hxfa9c...ce4b
[9-23 8:08] Accessed device at 129.101.254.235
[9-23 8:07] Stole 412 Crypto from hxa93a...9cc6
[9-23 8:07] Accessed device at 10.76.96.9
""".trimIndent()
}

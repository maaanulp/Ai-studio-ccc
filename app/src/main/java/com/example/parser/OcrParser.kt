package com.example.parser

import com.example.data.model.DatabaseScope
import com.example.data.model.TargetEntity

data class OcrAccountResult(
    val name: String = "",
    val crew: String = "",
    val level: Int = 0,
    val rep: Int = 0,
    val score: Long = 0L,
    val ip: String = "",
    val fw: Int = 0,
    val enc: Int = 0,
    val wallet: String = "",
    val hotWallet: Long = 0L,
    val coldStorageCurrent: Long = 0L,
    val coldStorageMax: Long = 0L,
    val rebootTag: String = "",
    val photoTimestamp: Long = 0L
)

data class SoftwareMatrixItem(
    val software_name: String,
    val level: Int,
    val is_low_confidence: Boolean = false
)

data class MultimodalAnchor(
    val accountName: String = "",
    val ip: String = "",
    val wallet: String = "",
    val crew: String = "",
    val anchorType: String = "FALLBACK_NODE",
    val softwareRoiStartLine: Int = 0
)

data class OcrAppsResult(
    val accountName: String = "",
    val ip: String = "",
    val crew: String = "",
    val antivirusLvl: Int = 0,
    val spamLvl: Int = 0,
    val rootkitLvl: Int = 0,
    val firewallLvl: Int = 0,
    val bypasserLvl: Int = 0,
    val passwordCrackerLvl: Int = 0,
    val passwordEncryptorLvl: Int = 0,
    val proxyLvl: Int = 0,
    val traceLvl: Int = 0,
    val keygenLvl: Int = 0,
    val siphonLvl: Int = 0,
    val rebootTag: String = "",
    val photoTimestamp: Long = 0L,
    val hasValidAnchor: Boolean = false,
    val isValidMatrix: Boolean = false,
    val anchorType: String = "PRIMARY_HEADER",
    val softwareItems: List<SoftwareMatrixItem> = emptyList()
) {
    fun getExtractedCount(): Int {
        var count = 0
        if (antivirusLvl in 1..50) count++
        if (spamLvl in 1..50) count++
        if (rootkitLvl in 1..50) count++
        if (firewallLvl in 1..50) count++
        if (bypasserLvl in 1..50) count++
        if (passwordCrackerLvl in 1..50) count++
        if (passwordEncryptorLvl in 1..50) count++
        if (proxyLvl in 1..50) count++
        if (traceLvl in 1..50) count++
        if (keygenLvl in 1..50) count++
        if (siphonLvl in 1..50) count++
        return count
    }

    fun getMissingApps(): List<String> {
        val missing = mutableListOf<String>()
        if (antivirusLvl !in 1..50) missing.add("Antivirus")
        if (spamLvl !in 1..50) missing.add("Spam")
        if (rootkitLvl !in 1..50) missing.add("Rootkit")
        if (firewallLvl !in 1..50) missing.add("Firewall")
        if (bypasserLvl !in 1..50) missing.add("Bypasser")
        if (passwordCrackerLvl !in 1..50) missing.add("Password Cracker")
        if (passwordEncryptorLvl !in 1..50) missing.add("Password Encryptor")
        if (proxyLvl !in 1..50) missing.add("Proxy")
        if (traceLvl !in 1..50) missing.add("Trace")
        if (keygenLvl !in 1..50) missing.add("Keygen")
        if (siphonLvl !in 1..50) missing.add("Siphon")
        return missing
    }

    /**
     * Strict JSON Array representation for software matrix:
     * [{"software_name": "Antivirus", "level": 20}, ...]
     */
    fun toStrictJsonArray(): String {
        val array = org.json.JSONArray()
        for (item in toNormalizedSoftwareList()) {
            val obj = org.json.JSONObject().apply {
                put("software_name", item.software_name)
                put("level", item.level)
                if (item.is_low_confidence) {
                    put("low_confidence", true)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun toNormalizedSoftwareList(): List<SoftwareMatrixItem> {
        if (softwareItems.isNotEmpty()) return softwareItems
        val list = mutableListOf<SoftwareMatrixItem>()
        if (antivirusLvl in 1..50) list.add(SoftwareMatrixItem("Antivirus", antivirusLvl))
        if (spamLvl in 1..50) list.add(SoftwareMatrixItem("Spam", spamLvl))
        if (rootkitLvl in 1..50) list.add(SoftwareMatrixItem("Rootkit", rootkitLvl))
        if (firewallLvl in 1..50) list.add(SoftwareMatrixItem("Firewall", firewallLvl))
        if (bypasserLvl in 1..50) list.add(SoftwareMatrixItem("Bypasser", bypasserLvl))
        if (passwordCrackerLvl in 1..50) list.add(SoftwareMatrixItem("Password Cracker", passwordCrackerLvl))
        if (passwordEncryptorLvl in 1..50) list.add(SoftwareMatrixItem("Password Encryptor", passwordEncryptorLvl))
        if (proxyLvl in 1..50) list.add(SoftwareMatrixItem("Proxy", proxyLvl))
        if (traceLvl in 1..50) list.add(SoftwareMatrixItem("Trace", traceLvl))
        if (keygenLvl in 1..50) list.add(SoftwareMatrixItem("Keygen", keygenLvl))
        if (siphonLvl in 1..50) list.add(SoftwareMatrixItem("Siphon", siphonLvl))
        return list
    }
}

sealed class OcrParseOutput {
    data class AccountData(val account: OcrAccountResult, val logs: List<String>) : OcrParseOutput()
    data class AppsData(val apps: OcrAppsResult, val logs: List<String>) : OcrParseOutput()
    data class Unknown(val rawText: String, val logs: List<String>) : OcrParseOutput()
}

object OcrParser {

    const val SAMPLE_PROFILE_OCR = """
> Death.exe [DLC]
LVL 34
REP 47,172
12,500 SCORE
155.4.117.87
Firewall Lv.14
Encryptor Lv.12
"""

    const val SAMPLE_APPS_OCR = """
// APPS / Installed Software
Antivirus LVL 25
Spam LVL 18
Rootkit LVL 30
Firewall LVL 14
Bypasser LVL 22
Password Cracker LVL 10
Password Encryptor LVL 12
Proxy LVL 15
Trace LVL 8
Keygen LVL 40
Siphon LVL 12
"""

    fun isGarbageUiName(name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val clean = name.trim().uppercase()
            .replace(Regex("""^[/#\-:_~`'\">]+|[/#\-:_~`'\">]+$"""), "")
            .removePrefix("HOST_")
            .removePrefix("HOST-")
            .removePrefix(">")
            .trim()
        val garbageSet = setOf(
            "INSTALLED", "SOFTWARE", "APPS", "PROCESSES", "LOGS", "CREWS", "BACK", "WALLET",
            "HOST", "TARGET", "SPECS", "OPEN", "CONNECTED", "TRANSFER", "EXTERNAL", "COLD",
            "HOT", "UNKNOWN", "UNASSIGNED", "GUEST", "NULL", "NONE", "DEVICE", "NETWORK",
            "PROGRAMS", "APPLICATIONS", "ALL", "MY", "THE", "NTTON", "AN25", "SOFTWARE_MATRIX_NODE",
            "OPEN TARGET SPECS", "SYSTEM INFO", "XP PROGRESS", "LIFETIME", "WALLPAPERS",
            "EXTERNAL TRANSFER", "COLD STORAGE", "HOT WALLET", "UPLOAD VIRUS",
            "INSTALLED SOFTWARE", "INSTALLED PROGRAMS", "INSTALLED APPS"
        )
        if (clean in garbageSet) return true
        val words = clean.split(Regex("""\s+""")).map { it.replace(Regex("""^[/#\-:_~`'\">]+|[/#\-:_~`'\">]+$"""), "").trim() }.filter { it.isNotBlank() }
        if (words.isNotEmpty() && words.all { it in garbageSet }) return true
        if (clean.contains("SOFTWARE_MATRIX_NODE") || clean.contains("OPEN TARGET SPECS") || clean.contains("HOST_BACK") || clean.contains("HOST_INSTALLED") || clean.contains("UPLOAD VIRUS")) return true
        return false
    }

    val IP_FIND_REGEX = Regex("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")
    val STRICT_IP_REGEX = Regex("""^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$""")

    fun isValidIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        val trimmed = ip.trim()
        if (trimmed.contains("X", ignoreCase = true)) return false
        if (!STRICT_IP_REGEX.matches(trimmed)) return false
        val parts = trimmed.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            val num = part.toIntOrNull()
            num != null && num in 0..255
        }
    }

    fun extractRebootTag(rawText: String): String {
        val regex = Regex("""(?i)(?:\[|\b)(R[1-6])(?:\]|\b)|\breboot\s*[:#\-\s]*([1-6])\b|\breinicio\s*[:#\-\s]*([1-6])\b""")
        val m = regex.find(rawText)
        if (m != null) {
            val r1 = m.groupValues[1]
            if (r1.isNotBlank()) return r1.uppercase()
            val r2 = m.groupValues[2]
            if (r2.isNotBlank()) return "R$r2"
            val r3 = m.groupValues[3]
            if (r3.isNotBlank()) return "R$r3"
        }
        return ""
    }

    fun extractImageExifTimestamp(context: android.content.Context, uri: android.net.Uri): Long {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = android.media.ExifInterface(inputStream)
                val dateStr = exifInterface.getAttribute(android.media.ExifInterface.TAG_DATETIME)
                    ?: exifInterface.getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exifInterface.getAttribute(android.media.ExifInterface.TAG_DATETIME_DIGITIZED)

                if (!dateStr.isNullOrBlank()) {
                    val format = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                    val parsed = format.parse(dateStr)
                    if (parsed != null && parsed.time > 0) {
                        return parsed.time
                    }
                }
            }
        } catch (_: Exception) {}

        // MediaStore fallback
        try {
            val projection = arrayOf(
                android.provider.MediaStore.Images.Media.DATE_TAKEN,
                android.provider.MediaStore.Images.Media.DATE_MODIFIED
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val takenIdx = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                    if (takenIdx != -1) {
                        val taken = cursor.getLong(takenIdx)
                        if (taken > 0) return taken
                    }
                    val modIdx = cursor.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_MODIFIED)
                    if (modIdx != -1) {
                        val mod = cursor.getLong(modIdx)
                        if (mod > 0) return mod * 1000L
                    }
                }
            }
        } catch (_: Exception) {}

        return System.currentTimeMillis()
    }

    fun extractTargetCrew(rawText: String): String {
        // 1. Explicit Crew / Clan label
        val crewRegex = Regex("""(?i)\b(?:Crew|Clan|Guild|Tag):\s*\[?([A-Za-z0-9_#\-]+)\]?""")
        val m1 = crewRegex.find(rawText)
        if (m1 != null) {
            val candidate = m1.groupValues[1].trim()
            if (candidate.length in 2..12 && candidate.uppercase() !in setOf("NONE", "UNKNOWN", "NULL", "EMPTY")) {
                return candidate
            }
        }

        // 2. Bracketed tag e.g. [GSEX] in account or header lines
        val bracketRegex = Regex("""\[([A-Za-z0-9_#\-]{2,10})\]""")
        for (match in bracketRegex.findAll(rawText)) {
            val tag = match.groupValues[1].trim()
            val ignored = setOf("INSTALLED", "RUNNING", "OPEN", "APPS", "SOFTWARE", "R1", "R2", "R3", "R4", "R5", "R6", "NONE", "UNKNOWN", "VERIFIED", "LVL", "LEVEL")
            if (tag.uppercase() !in ignored) {
                return tag
            }
        }

        return ""
    }

    fun extractTargetAccountName(rawText: String): String {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val headerLines = lines.take(15)

        fun sanitizeCandidate(str: String): String {
            var clean = str.replace(Regex("""\[[A-Za-z0-9_#\-]+\]"""), "").trim()
            clean = clean.replace(Regex("""^[/#\-:_~`'\"]+|[/#\-:_~`'\"]+$"""), "").trim()
            return clean
        }

        val ignored = setOf(
            "ALL", "MY", "THE", "INSTALLED", "SOFTWARE", "TARGET", "RUNNING", "OPEN", "SYSTEM", "PHONE",
            "DOWNLOAD", "APPS", "APPLICATIONS", "UNKNOWN", "UNASSIGNED", "GUEST",
            "NONE", "NULL", "EMPTY", "MATRIX", "NODE", "EXIF", "ROOM", "USER", "ACCOUNT",
            "LEVEL", "LVL", "REP", "REPUTATION", "FIREWALL", "ENCRYPTION", "LOGS", "SCAN",
            "100%", "WIFI", "LTE", "4G", "5G", "AM", "PM", "ANDROID", "DEVICE", "PROGRAMS",
            "INSTALLED SOFTWARE", "OPEN TARGET SPECS", "SYSTEM INFO"
        )

        fun isValidCandidate(c: String): Boolean {
            val s = sanitizeCandidate(c)
            if (s.length !in 2..30) return false
            if (s.uppercase() in ignored) return false
            if (isGarbageUiName(s)) return false
            if (s.uppercase().split(Regex("""\s+""")).all { it in ignored }) return false
            val isAppName = CANONICAL_APP_SPECS.any { spec ->
                spec.primaryPattern.containsMatchIn(s) || spec.aliasPatterns.any { a -> a.containsMatchIn(s) }
            }
            if (isAppName) return false
            return true
        }

        // 0. Prompt symbol "> Username" e.g. > Death.exe, > R34p3rX_, > GhostDrop, > ArdForYa
        val gtSymbolRegex = Regex("""(?i)>\s*([A-Za-z0-9_\.#\-]{2,30})""")
        for (line in headerLines) {
            val m = gtSymbolRegex.find(line)
            if (m != null) {
                val candidate = sanitizeCandidate(m.groupValues[1])
                if (isValidCandidate(candidate)) {
                    return candidate
                }
            }
        }

        // 0b. Wallet owner pattern e.g. Death.exe's wallet
        val walletOwnerRegex = Regex("""(?i)\b([A-Za-z0-9_\.#\-]{2,30})\s*(?:'s|’s|\'s)\s*wallet\b""")
        for (line in headerLines) {
            val m = walletOwnerRegex.find(line)
            if (m != null) {
                val candidate = sanitizeCandidate(m.groupValues[1])
                if (isValidCandidate(candidate)) {
                    return candidate
                }
            }
        }

        // 0c. Installed Software pattern e.g. bcooke25's installed software
        val installedSoftwareRegex = Regex("""(?i)\b([A-Za-z0-9_\.#\-]{2,30})\s*(?:'s|’s|\'s)?\s*installed\s+software\b""")
        for (line in headerLines) {
            val m = installedSoftwareRegex.find(line)
            if (m != null) {
                val candidate = sanitizeCandidate(m.groupValues[1])
                if (isValidCandidate(candidate)) {
                    return candidate
                }
            }
        }

        // 1. Look for patterns like "ArdForYa apps", "ArdForYa [GSEX] apps", "ArdForYa's apps", "User99 software"
        val suffixAppsRegex = Regex("""(?i)\b([A-Za-z0-9_#\-]{2,30}(?:\s+\[[A-Za-z0-9_#\-]+\])?)\s+(?:'s|’s|\'s)?\s*(?:apps|software|applications|programas|app|apss|appz)\b""")
        for (line in headerLines) {
            val m = suffixAppsRegex.find(line)
            if (m != null) {
                val candidate = sanitizeCandidate(m.groupValues[1])
                if (isValidCandidate(candidate)) {
                    return candidate
                }
            }
        }

        // 2. Look for patterns like "APPS for ArdForYa" or "APPS: ArdForYa" or "APPS - ArdForYa"
        val prefixAppsRegex = Regex("""(?i)\b(?:APPS|SOFTWARE|APPLICATIONS)\s*(?:FOR|OF|FROM|DE|PARA|:|\-|_)?\s*([A-Za-z0-9_#\-]{2,30})\b""")
        for (line in headerLines) {
            val m = prefixAppsRegex.find(line)
            if (m != null) {
                val candidate = sanitizeCandidate(m.groupValues[1])
                if (isValidCandidate(candidate)) {
                    return candidate
                }
            }
        }

        // 3. User / Account / Target / Name label in header
        val labelRegex = Regex("""(?i)\b(?:Target Account|Account|Target User|User|Victim Node|Target Node|Victim|Node|Target|Name|Player|Handle|Operative|Alias):\s*([A-Za-z0-9_#\-]{2,30})\b""")
        for (line in headerLines) {
            val m = labelRegex.find(line)
            if (m != null) {
                val candidate = sanitizeCandidate(m.groupValues[1])
                if (isValidCandidate(candidate)) {
                    return candidate
                }
            }
        }

        // 4. Bracketed Clan Tag + Name pattern: "[CLAN] Username" or "Username [CLAN]"
        val bracketTagAndNameRegex = Regex("""\[[A-Za-z0-9_#\-]{2,10}\]\s+([A-Za-z0-9_#\-]{2,24})|([A-Za-z0-9_#\-]{2,24})\s+\[[A-Za-z0-9_#\-]{2,10}\]""")
        for (line in headerLines) {
            val m = bracketTagAndNameRegex.find(line)
            if (m != null) {
                val c1 = m.groupValues[1].takeIf { it.isNotBlank() } ?: m.groupValues[2]
                val candidate = sanitizeCandidate(c1)
                if (isValidCandidate(candidate)) {
                    return candidate
                }
            }
        }

        // 5. Line immediately preceding "APPS" or "INSTALLED SOFTWARE"
        for (idx in headerLines.indices) {
            val line = headerLines[idx]
            val isSoftwareRow = CANONICAL_APP_SPECS.any { spec ->
                spec.primaryPattern.containsMatchIn(line) || spec.aliasPatterns.any { a -> a.containsMatchIn(line) }
            }
            if (isSoftwareRow) continue

            if (line.contains("apps", ignoreCase = true) || line.contains("software", ignoreCase = true) || line.contains("installed", ignoreCase = true)) {
                if (idx > 0) {
                    val prevLine = sanitizeCandidate(headerLines[idx - 1])
                    val words = prevLine.split(Regex("""\s+""")).filter { it.isNotBlank() }
                    val candidate = words.lastOrNull() ?: prevLine
                    if (isValidCandidate(candidate) && !candidate.matches(Regex("""^\d+$"""))) {
                        return candidate
                    }
                }
                val words = sanitizeCandidate(line).split(Regex("""\s+""")).filter { it.isNotBlank() }
                val firstWord = words.firstOrNull() ?: ""
                if (isValidCandidate(firstWord) && !firstWord.matches(Regex("""^\d+$"""))) {
                    return firstWord
                }
            }
        }

        // 6. Header line candidate that looks like a username
        for (line in headerLines.take(5)) {
            val clean = sanitizeCandidate(line)
            if (isValidCandidate(clean) && !clean.matches(Regex("""^\d+$""")) && !clean.contains(".") && !clean.contains(":")) {
                val isAppName = CANONICAL_APP_SPECS.any { it.primaryPattern.containsMatchIn(clean) || it.aliasPatterns.any { a -> a.containsMatchIn(clean) } }
                if (!isAppName) {
                    return clean
                }
            }
        }

        return ""
    }

    /**
     * Resolves contextual markers sequentially when the primary header anchor is unavailable.
     * Searches for:
     * 1. Primary Header Anchor
     * 2. Contextual Account/Cuenta labels
     * 3. IP Contextual Marker or Standalone IPv4
     * 4. Wallet Contextual Marker or Standalone Crypto Address
     * 5. Software Section Header ROI Marker
     * 6. Deterministic Fallback Node Anchor
     */
    fun resolveMultimodalAnchors(rawText: String): MultimodalAnchor {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var detectedName = ""
        var detectedIp = ""
        var detectedWallet = ""
        val detectedCrew = extractTargetCrew(rawText)
        var anchorType = "NONE"
        var roiStartLine = 0

        // 1. IP Check
        val ipMatch = IP_FIND_REGEX.find(rawText)
        if (ipMatch != null && isValidIp(ipMatch.value)) {
            detectedIp = ipMatch.value
        }

        // 2. Wallet Check
        val walletPattern = Regex("""\b(hx[a-zA-Z0-9\.]{6,}|0x[a-zA-Z0-9\.]{6,}|[a-zA-Z0-9]{4,}\.{3}[a-zA-Z0-9]{4,})\b""")
        val walletMatch = walletPattern.find(rawText)
        if (walletMatch != null) {
            detectedWallet = walletMatch.value
        }

        // Tier 1: Primary Header Anchor
        val primaryHeaderName = extractTargetAccountName(rawText)
        if (primaryHeaderName.isNotBlank() && !primaryHeaderName.equals("Target", ignoreCase = true)) {
            detectedName = primaryHeaderName
            anchorType = "PRIMARY_HEADER"
        }

        // Tier 2: Account/User/Cuenta Marker
        if (detectedName.isBlank()) {
            val accountMarkerRegex = Regex("""(?i)\b(?:Account|Cuenta|Target Account|User|Usuario|Target|Victim|Node|Player|Handle|Alias)\s*[:#\-\s]+\s*([A-Za-z0-9_#\-]{2,30})\b""")
            val m = accountMarkerRegex.find(rawText)
            if (m != null) {
                val candidate = m.groupValues[1].trim()
                if (candidate.uppercase() !in setOf("APPS", "SOFTWARE", "INSTALLED", "NULL", "NONE", "UNKNOWN")) {
                    detectedName = candidate
                    anchorType = "ACCOUNT_LABEL_MARKER"
                }
            }
        }

        // Tier 3: IP Marker Anchor
        if (detectedName.isBlank() && detectedIp.isNotBlank()) {
            detectedName = "Target_$detectedIp"
            anchorType = "IP_MARKER"
        }

        // Tier 4: Wallet Marker Anchor
        if (detectedName.isBlank() && detectedWallet.isNotBlank()) {
            detectedName = "Target_${detectedWallet.take(8)}"
            anchorType = "WALLET_MARKER"
        }

        // Tier 5: Software Section Header ROI Marker
        for ((idx, line) in lines.withIndex()) {
            if (line.contains("installed", ignoreCase = true) || line.contains("software", ignoreCase = true) || line.contains("apps", ignoreCase = true)) {
                roiStartLine = idx
                if (anchorType == "NONE") {
                    anchorType = "SOFTWARE_ROI_MARKER"
                }
                break
            }
        }

        // Tier 6: Fallback Deterministic Node Anchor
        if (detectedName.isBlank()) {
            detectedName = "Target_Node_${(rawText.hashCode() and 0xFFFF).toString(16).uppercase()}"
            if (anchorType == "NONE") {
                anchorType = "FALLBACK_NODE"
            }
        }

        return MultimodalAnchor(
            accountName = detectedName,
            ip = detectedIp,
            wallet = detectedWallet,
            crew = detectedCrew,
            anchorType = anchorType,
            softwareRoiStartLine = roiStartLine
        )
    }

    /**
     * Normalizes leetspeak and OCR character confusions (e.g. 1->i, 0->o, 3->e, 4->a, 5->s, 7->t).
     */
    fun normalizeLeetspeak(input: String): String {
        return input.lowercase()
            .replace('0', 'o')
            .replace('1', 'i')
            .replace('3', 'e')
            .replace('4', 'a')
            .replace('5', 's')
            .replace('7', 't')
            .replace('@', 'a')
            .replace('$', 's')
            .replace('_', ' ')
            .replace('-', ' ')
    }

    /**
     * Computes Levenshtein distance for fuzzy tolerance against OCR glyph misrecognitions.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Tolerant fuzzy matcher for canonical software titles.
     */
    fun isFuzzyTokenMatch(token: String, target: String, maxDistance: Int = 2): Boolean {
        val normToken = normalizeLeetspeak(token).replace(Regex("""[^a-z0-9]"""), "")
        val normTarget = normalizeLeetspeak(target).replace(Regex("""[^a-z0-9]"""), "")
        if (normToken.length < 3 || normTarget.length < 3) return normToken == normTarget
        if (normToken == normTarget) return true
        val lenDiff = kotlin.math.abs(normToken.length - normTarget.length)
        if (lenDiff > 2) return false
        val effectiveMaxDist = if (normTarget.length <= 6 || normToken.length <= 6) 1 else maxDistance
        val dist = levenshteinDistance(normToken, normTarget)
        return dist <= effectiveMaxDist
    }

    data class AppSpec(
        val name: String,
        val primaryPattern: Regex,
        val aliasPatterns: List<Regex>,
        val fuzzyAliases: List<String> = emptyList(),
        val setter: (OcrAppsResult, Int) -> OcrAppsResult,
        val getter: (OcrAppsResult) -> Int
    )

    val CANONICAL_APP_SPECS = listOf(
        AppSpec(
            name = "Antivirus",
            primaryPattern = Regex("""(?i)\b(?:anti[- ]?virus|antivirus|ant1virus|anti\s*v|antiv)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\bAV\b"""), Regex("""(?i)\banti\s*virus\b"""), Regex("""(?i)\bantiv\b"""), Regex("""(?i)\bant1\b""")),
            fuzzyAliases = listOf("antivirus", "antiv", "anti virus", "ant1virus", "ant1v1rus", "av"),
            setter = { r, v -> r.copy(antivirusLvl = v) },
            getter = { it.antivirusLvl }
        ),
        AppSpec(
            name = "Spam",
            primaryPattern = Regex("""(?i)\b(?:spambot|spam bot|spam|span|sp4m|spamb0t)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\bspam\s*bot\b"""), Regex("""(?i)\bspan\b"""), Regex("""(?i)\bsp4m\b""")),
            fuzzyAliases = listOf("spambot", "spam bot", "spam", "sp4m", "spamb0t", "span"),
            setter = { r, v -> r.copy(spamLvl = v) },
            getter = { it.spamLvl }
        ),
        AppSpec(
            name = "Rootkit",
            primaryPattern = Regex("""(?i)\b(?:root[- ]?kit|rootkit|r00tkit|ro0tkit|rootk1t)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\br[- ]?kit\b"""), Regex("""(?i)\broot\s*kit\b"""), Regex("""(?i)\br00t\b""")),
            fuzzyAliases = listOf("rootkit", "root kit", "root-kit", "r00tkit", "r00tk1t", "r kit", "rootk1t"),
            setter = { r, v -> r.copy(rootkitLvl = v) },
            getter = { it.rootkitLvl }
        ),
        AppSpec(
            name = "Firewall",
            primaryPattern = Regex("""(?i)\b(?:fire[- ]?wall|firewall|fire\s*wall|f-wall|firewal)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\bFW\b"""), Regex("""(?i)\bfire\s*wall\b"""), Regex("""(?i)\bfwall\b""")),
            fuzzyAliases = listOf("firewall", "fire wall", "fwall", "f1rewall", "f1rew4ll", "fw"),
            setter = { r, v -> r.copy(firewallLvl = v) },
            getter = { it.firewallLvl }
        ),
        AppSpec(
            name = "Bypasser",
            primaryPattern = Regex("""(?i)\b(?:by[- ]?passer|bypasser|bypass|by-pass|by pass|bypas|bypazzer|bypassr)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\b(?:bypass|by-pass|by pass|bypas)\b""")),
            fuzzyAliases = listOf("bypasser", "by-passer", "by passer", "bypass", "byp4ss", "byp4sser", "bypas"),
            setter = { r, v -> r.copy(bypasserLvl = v) },
            getter = { it.bypasserLvl }
        ),
        AppSpec(
            name = "Password Cracker",
            primaryPattern = Regex("""(?i)\b(?:password\s+cracker|pw\s+cracker|pass\s+cracker|passwordcracker|pwd\s+cracker|cracker|passw0rd\s+cracker|pass\s*crack)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\b(?:cracker|pw\s*crack)\b""")),
            fuzzyAliases = listOf("password cracker", "pw cracker", "pass cracker", "pwd cracker", "passw0rd cracker", "p4ssw0rd cr4cker", "cracker", "pw crack"),
            setter = { r, v -> r.copy(passwordCrackerLvl = v) },
            getter = { it.passwordCrackerLvl }
        ),
        AppSpec(
            name = "Password Encryptor",
            primaryPattern = Regex("""(?i)\b(?:password\s+encryptor|pw\s+encryptor|pass\s+encryptor|passwordencryptor|pwd\s+encryptor|encryptor|passw0rd\s+encryptor|pass\s*encryp)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\b(?:encryptor|pw\s*encryp)\b""")),
            fuzzyAliases = listOf("password encryptor", "pw encryptor", "pass encryptor", "pwd encryptor", "passw0rd encryptor", "p4ssw0rd encrypt0r", "encryptor", "pw encryp"),
            setter = { r, v -> r.copy(passwordEncryptorLvl = v) },
            getter = { it.passwordEncryptorLvl }
        ),
        AppSpec(
            name = "Proxy",
            primaryPattern = Regex("""(?i)\b(?:proxy|proxi|pr0xy|prx)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\bpr0xy\b"""), Regex("""(?i)\bproxi\b""")),
            fuzzyAliases = listOf("proxy", "proxi", "pr0xy", "prx"),
            setter = { r, v -> r.copy(proxyLvl = v) },
            getter = { it.proxyLvl }
        ),
        AppSpec(
            name = "Trace",
            primaryPattern = Regex("""(?i)\b(?:tracer|trace|traceroute|trace-route|tr4ce)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\btr4ce\b"""), Regex("""(?i)\btracer\b""")),
            fuzzyAliases = listOf("tracer", "trace", "traceroute", "tr4ce"),
            setter = { r, v -> r.copy(traceLvl = v) },
            getter = { it.traceLvl }
        ),
        AppSpec(
            name = "Keygen",
            primaryPattern = Regex("""(?i)\b(?:key[- ]?gen|keygen|key generator|key-generator|k-gen|keyg3n)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\bkey[- ]?gen\b"""), Regex("""(?i)\bkeyg3n\b""")),
            fuzzyAliases = listOf("keygen", "key-gen", "key gen", "k gen", "k3ygen", "keyg3n"),
            setter = { r, v -> r.copy(keygenLvl = v) },
            getter = { it.keygenLvl }
        ),
        AppSpec(
            name = "Siphon",
            primaryPattern = Regex("""(?i)\b(?:crypto\s+siphon|cryptosiphon|siphon|syphon|siph0n|crypto-siphon|c-siphon|cypto\s+siphon)\b"""),
            aliasPatterns = listOf(Regex("""(?i)\b(?:crypto-siphon|cypto\s*siphon|siph0n)\b""")),
            fuzzyAliases = listOf("siphon", "crypto siphon", "cryptosiphon", "crypto-siphon", "syphon", "siph0n", "s1phon", "crypt0 siphon"),
            setter = { r, v -> r.copy(siphonLvl = v) },
            getter = { it.siphonLvl }
        )
    )

    fun extractLevelFromTextToRight(textToRight: String): Int? {
        val trimmed = textToRight.trim()
        if (trimmed.isEmpty()) return null

        // 1. Explicit LVL indicators: LVL, LEVEL, 1VL, IVL, IVI, LVI, LV, LY, VER, V, L, # with number
        val explicitLvlRegex = Regex("""(?i)(?:\[|\(|\b)(?:lvl|level|1vl|ivl|ivi|lvi|lv|ly|ver|v|l|#)\s*[:#\-\.\s]*\s*(\d{1,3})(?:/\d+)?(?:\]|\)|\b)""")
        val m1 = explicitLvlRegex.find(trimmed)
        if (m1 != null) {
            val v = m1.groupValues[1].toIntOrNull()
            if (v != null && v in 1..50) return v
        }

        // 2. Bracketed or parenthesized level: [24], (24)
        val bracketRegex = Regex("""(?:\[|\()\s*(\d{1,3})\s*(?:\]|\))""")
        val mb = bracketRegex.find(trimmed)
        if (mb != null) {
            val v = mb.groupValues[1].toIntOrNull()
            if (v != null && v in 1..50) return v
        }

        // 3. Direct delimiter: ": 24", "- 24", "| 24", "= 24", "-> 24", ensuring it's not a file size (MB/KB/GB)
        val delimRegex = Regex("""^(?:[:\-|=>#\s]+)\b(\d{1,3})\b(?!\s*(?:mb|kb|gb|b\b|\.))""", RegexOption.IGNORE_CASE)
        val m2 = delimRegex.find(trimmed)
        if (m2 != null) {
            val v = m2.groupValues[1].toIntOrNull()
            if (v != null && v in 1..50) return v
        }

        // 4. Standalone number on this row (not part of decimal like 1.2 or followed by MB/KB/GB):
        val standaloneRegex = Regex("""(?<![\.\d])\b(\d{1,3})\b(?!\s*(?:mb|kb|gb|b\b|\.))""", RegexOption.IGNORE_CASE)
        val m3 = standaloneRegex.find(trimmed)
        if (m3 != null) {
            val v = m3.groupValues[1].toIntOrNull()
            if (v != null && v in 1..50) return v
        }

        return null
    }

    fun extractLevelFromLine(line: String, currentSpec: AppSpec? = null): Int? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        val matchedApps = CANONICAL_APP_SPECS.filter { spec ->
            spec.primaryPattern.containsMatchIn(trimmed) ||
            spec.aliasPatterns.any { it.containsMatchIn(trimmed) }
        }
        if (currentSpec != null) {
            if (matchedApps.any { it.name != currentSpec.name }) return null
        } else {
            if (matchedApps.size > 1) return null
        }

        val explicitLvlRegex = Regex("""(?i)(?:\[|\(|\b)(?:lvl|level|1vl|ivl|ivi|lvi|lv|ly|v|ver|l|#)?\s*[:#\-\.\s]*(\d{1,3})(?:/\d+)?(?:\]|\)|\b)""")
        val m1 = explicitLvlRegex.find(trimmed)
        if (m1 != null) {
            val v = m1.groupValues[1].toIntOrNull()
            if (v != null && v in 1..50) return v
        }

        val standaloneRegex = Regex("""(?<![\.\d])^\s*(\d{1,3})\s*(?!\s*(?:mb|kb|gb|b\b|\.))""", RegexOption.IGNORE_CASE)
        val m2 = standaloneRegex.find(trimmed)
        if (m2 != null) {
            val v = m2.groupValues[1].toIntOrNull()
            if (v != null && v in 1..50) return v
        }

        return null
    }

    fun parseAppsMatrix(rawText: String, photoTimestamp: Long = 0L): OcrAppsResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val multimodalAnchor = resolveMultimodalAnchors(rawText)
        val accountName = multimodalAnchor.accountName
        val appsIp = multimodalAnchor.ip
        val crew = multimodalAnchor.crew.ifBlank { extractTargetCrew(rawText) }
        val rebootTag = extractRebootTag(rawText)

        var appsResult = OcrAppsResult(
            accountName = accountName,
            ip = appsIp,
            crew = crew,
            rebootTag = rebootTag,
            photoTimestamp = photoTimestamp,
            hasValidAnchor = accountName.isNotBlank(),
            anchorType = multimodalAnchor.anchorType
        )

        // 11-Row Grid Matching: Strict row-by-row mapping with right-quadrant extraction
        val assignedSpecs = mutableSetOf<String>()
        val lowConfidenceApps = mutableSetOf<String>()

        for (i in lines.indices) {
            val line = lines[i]
            val normalizedLine = normalizeLeetspeak(line)

            data class MatchOnLine(val spec: AppSpec, val range: IntRange, val isFuzzy: Boolean = false)
            val matchesOnThisLine = mutableListOf<MatchOnLine>()

            for (spec in CANONICAL_APP_SPECS) {
                if (assignedSpecs.contains(spec.name)) continue
                val m = spec.primaryPattern.find(line)
                    ?: spec.aliasPatterns.firstNotNullOfOrNull { it.find(line) }
                if (m != null) {
                    matchesOnThisLine.add(MatchOnLine(spec, m.range, isFuzzy = false))
                } else {
                    val fuzzyMatch = spec.fuzzyAliases.firstOrNull { normalizedLine.contains(it) }
                    if (fuzzyMatch != null) {
                        val startIdx = normalizedLine.indexOf(fuzzyMatch)
                        val endIdx = (startIdx + fuzzyMatch.length).coerceAtMost(line.length)
                        matchesOnThisLine.add(MatchOnLine(spec, startIdx until endIdx, isFuzzy = true))
                    } else {
                        // Token-level Levenshtein similarity for severe OCR noise (e.g., Ant1v1rus, Byp4sser)
                        val tokens = line.split(Regex("""[^A-Za-z0-9]+""")).filter { it.length >= 3 }
                        val matchedToken = tokens.firstOrNull { t ->
                            isFuzzyTokenMatch(t, spec.name) || spec.fuzzyAliases.any { a -> isFuzzyTokenMatch(t, a) }
                        }
                        if (matchedToken != null) {
                            val startIdx = line.indexOf(matchedToken).coerceAtLeast(0)
                            val endIdx = (startIdx + matchedToken.length).coerceAtMost(line.length)
                            matchesOnThisLine.add(MatchOnLine(spec, startIdx until endIdx, isFuzzy = true))
                        }
                    }
                }
            }

            matchesOnThisLine.sortBy { it.range.first }

            for (mIdx in matchesOnThisLine.indices) {
                val current = matchesOnThisLine[mIdx]
                val nextMatch = matchesOnThisLine.getOrNull(mIdx + 1)
                val textAfter = line.substring(current.range.last + 1)
                val textToRight = if (nextMatch != null) {
                    val endBound = nextMatch.range.first - current.range.last - 1
                    if (endBound > 0 && endBound <= textAfter.length) textAfter.substring(0, endBound) else textAfter
                } else textAfter

                var extractedLevel = extractLevelFromTextToRight(textToRight) ?: 0

                // If same line had no level, inspect adjacent next lines (only if they don't contain another app)
                if (extractedLevel <= 0 && i + 1 < lines.size) {
                    val isNextLineApp = CANONICAL_APP_SPECS.any { s ->
                        s.primaryPattern.containsMatchIn(lines[i + 1]) ||
                        s.aliasPatterns.any { it.containsMatchIn(lines[i + 1]) }
                    }
                    if (!isNextLineApp) {
                        extractedLevel = extractLevelFromLine(lines[i + 1]) ?: 0
                    }
                }

                // Check line i + 2 if line i + 1 was an intermediate badge (e.g. "[Installed]")
                if (extractedLevel <= 0 && i + 2 < lines.size) {
                    val isLine1App = CANONICAL_APP_SPECS.any { s ->
                        s.primaryPattern.containsMatchIn(lines[i + 1]) ||
                        s.aliasPatterns.any { it.containsMatchIn(lines[i + 1]) }
                    }
                    val isLine2App = CANONICAL_APP_SPECS.any { s ->
                        s.primaryPattern.containsMatchIn(lines[i + 2]) ||
                        s.aliasPatterns.any { it.containsMatchIn(lines[i + 2]) }
                    }
                    if (!isLine1App && !isLine2App) {
                        extractedLevel = extractLevelFromLine(lines[i + 2]) ?: 0
                    }
                }

                if (extractedLevel in 1..50) {
                    appsResult = current.spec.setter(appsResult, extractedLevel)
                    assignedSpecs.add(current.spec.name)
                    if (current.isFuzzy) {
                        lowConfidenceApps.add(current.spec.name)
                    }
                }
            }
        }

        // Pass 2: Columnar Grid Fallback (Left column of 11 apps, Right column of levels)
        if (appsResult.getExtractedCount() < 11) {
            val levelLineRegex = Regex("""(?i)\b(?:lvl|level|1vl|ivl|ivi|lvi|lv)\s*[:#\-\.]*\s*(\d{1,3})\b""")
            val standaloneDigitRegex = Regex("""^\s*(\d{1,3})\s*$""")
            val levelValues = mutableListOf<Int>()

            for (line in lines) {
                val hasAppName = CANONICAL_APP_SPECS.any { s ->
                    s.primaryPattern.containsMatchIn(line) || s.aliasPatterns.any { it.containsMatchIn(line) }
                }
                if (!hasAppName) {
                    val m1 = levelLineRegex.find(line)
                    if (m1 != null) {
                        m1.groupValues[1].toIntOrNull()?.let { if (it in 1..50) levelValues.add(it) }
                    } else {
                        val m2 = standaloneDigitRegex.find(line)
                        if (m2 != null) {
                            m2.groupValues[1].toIntOrNull()?.let { if (it in 1..50) levelValues.add(it) }
                        }
                    }
                }
            }

            val unassignedSpecs = CANONICAL_APP_SPECS.filter { !assignedSpecs.contains(it.name) }
            if (unassignedSpecs.size == levelValues.size) {
                for (idx in unassignedSpecs.indices) {
                    val spec = unassignedSpecs[idx]
                    val lvl = levelValues[idx]
                    if (lvl in 1..50) {
                        appsResult = spec.setter(appsResult, lvl)
                        assignedSpecs.add(spec.name)
                    }
                }
            } else if (levelValues.size >= 11 && assignedSpecs.isEmpty()) {
                for (idx in CANONICAL_APP_SPECS.indices) {
                    val spec = CANONICAL_APP_SPECS[idx]
                    val lvl = levelValues[idx]
                    if (lvl in 1..50) {
                        appsResult = spec.setter(appsResult, lvl)
                        assignedSpecs.add(spec.name)
                    }
                }
            }
        }

        val count = appsResult.getExtractedCount()
        // Flexible fallback if accountName was empty but software was extracted or IP exists
        val isRealAccountName = accountName.isNotBlank() && !accountName.startsWith("Target_Node_")
        val hasExplicitAnchor = isRealAccountName || appsIp.isNotBlank()
        val finalAccountName = when {
            accountName.isNotBlank() -> accountName
            appsIp.isNotBlank() -> "Target_$appsIp"
            count > 0 -> "Target_Node_${(rawText.hashCode() and 0xFFFF).toString(16).uppercase()}"
            else -> ""
        }
        val isValid = (hasExplicitAnchor || count >= 2) && count > 0

        val items = mutableListOf<SoftwareMatrixItem>()
        for (spec in CANONICAL_APP_SPECS) {
            val lvl = spec.getter(appsResult)
            if (lvl in 1..50) {
                items.add(
                    SoftwareMatrixItem(
                        software_name = spec.name,
                        level = lvl,
                        is_low_confidence = lowConfidenceApps.contains(spec.name)
                    )
                )
            }
        }

        return appsResult.copy(
            accountName = finalAccountName,
            isValidMatrix = isValid,
            hasValidAnchor = hasExplicitAnchor,
            softwareItems = items
        )
    }

    fun parseOcrText(rawText: String, photoTimestamp: Long = 0L): OcrParseOutput {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val logs = mutableListOf<String>()

        logs.add("[OCR_ENGINE] Processing ${lines.size} text tokens...")

        val detectedReboot = extractRebootTag(rawText)
        if (detectedReboot.isNotBlank()) {
            logs.add("[REBOOT_DETECTED] Target reboot tag '$detectedReboot' identified in scan tokens.")
        }

        if (photoTimestamp > 0L) {
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(photoTimestamp))
            logs.add("[EXIF_METADATA] Photo capture timestamp: $dateStr")
        }

        // Determine if screenshot is an APPS Matrix capture
        val appsKeywords = listOf("APPS", "SOFTWARE", "APPLICATIONS", "INSTALLED SOFTWARE")
        val specificApps = listOf("ANTIVIRUS", "BYPASSER", "ROOTKIT", "PASSWORD CRACKER", "KEYGEN", "SIPHON", "SPAM", "FIREWALL")
        val hasAppsHeader = appsKeywords.any { rawText.contains(it, ignoreCase = true) }
        val specificAppCount = specificApps.count { rawText.contains(it, ignoreCase = true) }
        val hasLogAccessEntries = rawText.contains("Accessed device", ignoreCase = true) || rawText.contains("Stole", ignoreCase = true)
        val isAppsScreenshot = !hasLogAccessEntries && (hasAppsHeader || specificAppCount >= 3)

        if (isAppsScreenshot) {
            logs.add("[OCR_DETECT] Signature matched: APPS / Installed Software Matrix Screenshot")

            val apps = parseAppsMatrix(rawText, photoTimestamp)

            if (!apps.hasValidAnchor && apps.ip.isBlank()) {
                logs.add("[OCR_REJECT] APPS screen rejected: No verified account anchor or IP address detected.")
                return OcrParseOutput.Unknown(rawText, logs)
            }

            val resolvedAccountName = when {
                apps.accountName.isNotBlank() && !apps.accountName.equals("Target", ignoreCase = true) -> apps.accountName
                apps.ip.isNotBlank() -> "Target_${apps.ip}"
                else -> "Target_Node_${(rawText.hashCode() and 0xFFFF).toString(16).uppercase()}"
            }
            val resolvedApps = apps.copy(
                accountName = resolvedAccountName,
                hasValidAnchor = true
            )

            logs.add("[OCR_ANCHOR] Resolved Target Account Anchor: '${resolvedApps.accountName}' (Anchor Mode: ${resolvedApps.anchorType})")
            if (resolvedApps.crew.isNotBlank()) {
                logs.add("[OCR_ANCHOR] Verified Crew Tag: [${resolvedApps.crew}]")
            }
            if (resolvedApps.ip.isNotBlank()) {
                logs.add("[OCR_EXTRACT] Verified IP in Software Matrix: ${resolvedApps.ip}")
            }

            // Log extraction results per row
            for (spec in CANONICAL_APP_SPECS) {
                val lvl = spec.getter(resolvedApps)
                val rowNum = CANONICAL_APP_SPECS.indexOf(spec) + 1
                if (lvl in 1..50) {
                    logs.add("[APP_GRID] Row $rowNum/11: ${spec.name} -> Level $lvl")
                } else {
                    logs.add("[APP_WARN] Row $rowNum/11: ${spec.name} -> Level unconfirmed / null ([-])")
                }
            }

            val extractedCount = resolvedApps.getExtractedCount()
            if (extractedCount == 11) {
                logs.add("[OCR_VALIDATED] Full 11/11 software matrix verified for '${resolvedApps.accountName}':")
                logs.add("  • AV:${resolvedApps.antivirusLvl} | SPAM:${resolvedApps.spamLvl} | ROOTKIT:${resolvedApps.rootkitLvl} | FW:${resolvedApps.firewallLvl} | BYPASS:${resolvedApps.bypasserLvl}")
                logs.add("  • CRACKER:${resolvedApps.passwordCrackerLvl} | ENCRYPTOR:${resolvedApps.passwordEncryptorLvl} | PROXY:${resolvedApps.proxyLvl} | TRACE:${resolvedApps.traceLvl} | KEYGEN:${resolvedApps.keygenLvl} | SIPHON:${resolvedApps.siphonLvl}")
                logs.add("[OCR_SUMMARY] Successfully parsed and verified complete 11-level software matrix for '${resolvedApps.accountName}'")
                return OcrParseOutput.AppsData(resolvedApps, logs)
            } else if (extractedCount > 0) {
                val missing = resolvedApps.getMissingApps()
                logs.add("[WARN] Partial software matrix: $extractedCount/11 software levels confirmed for '${resolvedApps.accountName}'.")
                logs.add("[WARN] Unidentified software rows: [${missing.joinToString(", ")}]. Stored as unconfirmed (0).")
                logs.add("[OCR_SUMMARY] Ingesting $extractedCount confirmed software levels associated to account anchor '${resolvedApps.accountName}'")
                return OcrParseOutput.AppsData(resolvedApps, logs)
            } else {
                logs.add("[OCR_WARN] Software matrix node detected for '${resolvedApps.accountName}' without explicit level tokens. Ingesting node anchor.")
                return OcrParseOutput.AppsData(resolvedApps, logs)
            }
        }

        // Check if screenshot is a Crypto Wallet screen
        val isWalletScreen = rawText.contains("CRYPTO WALLET", ignoreCase = true) ||
                             rawText.contains("WALLET ADDRESS", ignoreCase = true) ||
                             rawText.contains("HOT WALLET", ignoreCase = true) ||
                             rawText.contains("COLD STORAGE", ignoreCase = true)

        if (isWalletScreen) {
            logs.add("[OCR_DETECT] Signature matched: Crypto Wallet Screenshot")
            val name = extractTargetAccountName(rawText)
            val walletAddr = Regex("""(?i)\b(hx[a-zA-Z0-9\.]{6,}|0x[a-zA-Z0-9\.]{6,}|[a-zA-Z0-9]{4,}\.{3}[a-zA-Z0-9]{4,})\b""")
                .find(rawText)?.value ?: ""

            val hotMatch = Regex("""(?i)HOT\s+WALLET\s*\n?\s*([0-9,]+)\s*Crypto?""").find(rawText)
            val hotVal = hotMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: 0L

            val coldMatch = Regex("""(?i)COLD\s+STORAGE\s*\n?(?:Secured\s*)?\n?\s*([0-9,]+)\s*Crypto?\s*/\s*([0-9,]+)\s*max""").find(rawText)
            val coldCurrent = coldMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: 0L
            val coldMax = coldMatch?.groupValues?.get(2)?.replace(",", "")?.toLongOrNull() ?: 0L

            logs.add("[OCR_EXTRACT] Wallet Owner: ${name.ifBlank { "Death.exe" }}")
            if (walletAddr.isNotBlank()) logs.add("[OCR_EXTRACT] Wallet Address: $walletAddr")
            if (hotVal > 0L) logs.add("[OCR_EXTRACT] Hot Wallet: $hotVal Crypto")
            if (coldCurrent > 0L) logs.add("[OCR_EXTRACT] Cold Storage: $coldCurrent / $coldMax")

            val account = OcrAccountResult(
                name = name,
                wallet = walletAddr,
                hotWallet = hotVal,
                coldStorageCurrent = coldCurrent,
                coldStorageMax = coldMax,
                rebootTag = detectedReboot,
                photoTimestamp = photoTimestamp
            )
            return OcrParseOutput.AccountData(account, logs)
        }

        // Account Profile Screenshot
        logs.add("[OCR_DETECT] Signature matched: Account Profile Screenshot")

        var ip = ""
        val ipMatch = IP_FIND_REGEX.find(rawText)
        if (ipMatch != null && isValidIp(ipMatch.value)) {
            ip = ipMatch.value
            logs.add("[OCR_EXTRACT] IP Address: $ip")
        } else {
            logs.add("[OCR_WARN] Masked or unassigned IP in profile scan (no valid IPv4 detected).")
        }

        val name = extractTargetAccountName(rawText)
        val crew = extractTargetCrew(rawText)

        val lvlMatch = Regex("""(?i)\bLVL\s*(\d+)""").find(rawText) ?: Regex("""(?i)\bLevel\s*(\d+)""").find(rawText)
        val lvl = lvlMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val repMatch = Regex("""(?i)\bREP\s*[:#\-\s]*([0-9,\s]{2,12})""").find(rawText)
        val rep = if (repMatch != null) {
            repMatch.groupValues[1].replace(",", "").replace(" ", "").trim().toIntOrNull() ?: 0
        } else 0

        val scoreMatch = Regex("""(?i)([0-9,\s]{2,12})\s*SCORE""").find(rawText)
        val score = if (scoreMatch != null) {
            scoreMatch.groupValues[1].replace(",", "").replace(" ", "").trim().toLongOrNull() ?: 0L
        } else 0L

        val fwMatch = Regex("""(?i)(?:FIREWALL|FW)\s*(?:Lv\.?|Lvl\.?|Level)?\s*[:#\-\s]*(\d{1,3})""").find(rawText)
        val fw = fwMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val encMatch = Regex("""(?i)(?:ENCRYPTOR|ENC|ENCRYPTION)\s*(?:Lv\.?|Lvl\.?|Level)?\s*[:#\-\s]*(\d{1,3})""").find(rawText)
        val enc = encMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        if (name.isNotBlank()) logs.add("[OCR_EXTRACT] Account Name: $name")
        if (crew.isNotBlank()) logs.add("[OCR_EXTRACT] Crew: $crew")
        if (lvl > 0) logs.add("[OCR_EXTRACT] Level: $lvl")
        if (rep != 0) logs.add("[OCR_EXTRACT] Reputation: $rep")
        if (score > 0) logs.add("[OCR_EXTRACT] Score: $score")
        if (fw > 0) logs.add("[OCR_EXTRACT] Firewall: $fw")
        if (enc > 0) logs.add("[OCR_EXTRACT] Encryptor: $enc")

        val account = OcrAccountResult(
            name = name,
            crew = crew,
            level = lvl,
            rep = rep,
            score = score,
            ip = ip,
            fw = fw,
            enc = enc,
            rebootTag = detectedReboot,
            photoTimestamp = photoTimestamp
        )

        logs.add("[OCR_SUMMARY] Successfully extracted profile for User: ${name.ifBlank { "UNASSIGNED" }} / IP: $ip")
        return OcrParseOutput.AccountData(account, logs)
    }

}

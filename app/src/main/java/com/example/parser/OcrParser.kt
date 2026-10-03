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
    val rebootTag: String = "",
    val photoTimestamp: Long = 0L
)

data class OcrAppsResult(
    val accountName: String = "",
    val ip: String = "",
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
    val photoTimestamp: Long = 0L
)

sealed class OcrParseOutput {
    data class AccountData(val account: OcrAccountResult, val logs: List<String>) : OcrParseOutput()
    data class AppsData(val apps: OcrAppsResult, val logs: List<String>) : OcrParseOutput()
    data class Unknown(val rawText: String, val logs: List<String>) : OcrParseOutput()
}

object OcrParser {

    val STRICT_IP_REGEX = Regex("""\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b""")

    fun isValidIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return false
        val trimmed = ip.trim()
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

        val appsIndicators = listOf(
            "APPS", "SOFTWARE", "ANTIVIRUS", "BYPASSER", "ROOTKIT",
            "PASSWORD CRACKER", "PASSWORD ENCRYPTOR", "KEYGEN", "SIPHON", "APPLICATIONS"
        )
        val hasAppsKeyword = appsIndicators.any { rawText.contains(it, ignoreCase = true) }

        if (hasAppsKeyword) {
            logs.add("[OCR_DETECT] Signature matched: APPS / Installed Software Screenshot")
            var accountName = ""

            // Look for "APPS <name>" or "APPS for <name>" or "Account: <name>"
            val appsHeaderRegex = Regex("""APPS\s+(?:FOR\s+)?([A-Za-z0-9_#\-]+)""", RegexOption.IGNORE_CASE)
            val nameMatch = appsHeaderRegex.find(rawText)
            if (nameMatch != null) {
                accountName = nameMatch.groupValues[1].trim()
                logs.add("[OCR_EXTRACT] Target Account identified: $accountName")
            } else {
                val userRegex = Regex("""(?:User|Account|Target|Name):\s*([A-Za-z0-9_#\-]+)""", RegexOption.IGNORE_CASE)
                val uMatch = userRegex.find(rawText)
                if (uMatch != null) {
                    accountName = uMatch.groupValues[1].trim()
                    logs.add("[OCR_EXTRACT] Target Account identified: $accountName")
                }
            }

            // Check if there is an IP in the apps screen
            var appsIp = ""
            val appsIpMatch = STRICT_IP_REGEX.find(rawText)
            if (appsIpMatch != null && isValidIp(appsIpMatch.value)) {
                appsIp = appsIpMatch.value
                logs.add("[OCR_EXTRACT] Verified IP in Software Matrix: $appsIp")
            }

            fun extractLevel(appName: String, altNames: List<String> = emptyList()): Int {
                val allPatterns = listOf(appName) + altNames

                // Pass 1: Same line search with comprehensive OCR indicators
                for (pat in allPatterns) {
                    val escaped = Regex.escape(pat)
                    val sameLineRegex = Regex(
                        """(?i)\b$escaped\b[^\n\r]*?(?:lvl|level|1vl|ivi|lv|v|#)?\s*[:#\-\.]?\s*(\d{1,4})\b"""
                    )
                    val m = sameLineRegex.find(rawText)
                    if (m != null) {
                        val lvl = m.groupValues[1].toIntOrNull() ?: 0
                        if (lvl > 0) {
                            logs.add("[APP_INDEX] Found $appName -> Level $lvl")
                            return lvl
                        }
                    }
                }

                // Pass 2: Multi-line adjacent search (MLKit often breaks name and level into separate lines)
                for (i in lines.indices) {
                    val line = lines[i]
                    val matchesPattern = allPatterns.any { pat ->
                        line.contains(pat, ignoreCase = true)
                    }
                    if (matchesPattern) {
                        // Check current line for digit
                        val inlineDigit = Regex("""(?i)(?:lvl|level|1vl|ivi|lv|v|#)?\s*[:#\-\.]?\s*(\d{1,4})\b""").find(line)
                        val numInSame = inlineDigit?.groupValues?.get(1)?.toIntOrNull()
                        if (numInSame != null && numInSame > 0) {
                            logs.add("[APP_INDEX] Found $appName -> Level $numInSame")
                            return numInSame
                        }

                        // Check next line (i + 1)
                        if (i + 1 < lines.size) {
                            val nextLine = lines[i + 1]
                            val nextDigit = Regex("""(?i)^\s*(?:lvl|level|1vl|ivi|lv|v|#)?\s*[:#\-\.]?\s*(\d{1,4})\b""").find(nextLine)
                                ?: Regex("""\b(\d{1,4})\b""").find(nextLine)
                            val numInNext = nextDigit?.groupValues?.get(1)?.toIntOrNull()
                            if (numInNext != null && numInNext > 0) {
                                logs.add("[APP_INDEX] Found $appName -> Level $numInNext (line ${i+1})")
                                return numInNext
                            }
                        }

                        // Check line (i + 2) in case of status badge line
                        if (i + 2 < lines.size) {
                            val next2Line = lines[i + 2]
                            val next2Digit = Regex("""(?i)^\s*(?:lvl|level|1vl|ivi|lv|v|#)?\s*[:#\-\.]?\s*(\d{1,4})\b""").find(next2Line)
                            val numInNext2 = next2Digit?.groupValues?.get(1)?.toIntOrNull()
                            if (numInNext2 != null && numInNext2 > 0) {
                                logs.add("[APP_INDEX] Found $appName -> Level $numInNext2 (line ${i+2})")
                                return numInNext2
                            }
                        }
                    }
                }

                return 0
            }

            val apps = OcrAppsResult(
                accountName = accountName,
                ip = appsIp,
                antivirusLvl = extractLevel("Antivirus", listOf("AV", "Anti-Virus")),
                spamLvl = extractLevel("Spam", listOf("SpamBot")),
                rootkitLvl = extractLevel("Rootkit", listOf("Root-Kit")),
                firewallLvl = extractLevel("Firewall", listOf("FW")),
                bypasserLvl = extractLevel("Bypasser", listOf("Bypass")),
                passwordCrackerLvl = extractLevel("Password Cracker", listOf("PW Cracker", "Cracker")),
                passwordEncryptorLvl = extractLevel("Password Encryptor", listOf("PW Encryptor", "Encryptor")),
                proxyLvl = extractLevel("Proxy"),
                traceLvl = extractLevel("Trace", listOf("Tracer")),
                keygenLvl = extractLevel("Keygen", listOf("Key-Gen")),
                siphonLvl = extractLevel("Siphon", listOf("Crypto Siphon")),
                rebootTag = detectedReboot,
                photoTimestamp = photoTimestamp
            )

            logs.add("[OCR_SUMMARY] Successfully parsed software matrix for '${accountName.ifBlank { "Unassigned" }}'")
            return OcrParseOutput.AppsData(apps, logs)
        }

        // Account Profile Screenshot
        logs.add("[OCR_DETECT] Signature matched: Account Profile Screenshot")

        var ip = ""
        val ipMatch = STRICT_IP_REGEX.find(rawText)
        if (ipMatch != null && isValidIp(ipMatch.value)) {
            ip = ipMatch.value
            logs.add("[OCR_EXTRACT] IP Address: $ip")
        } else {
            logs.add("[OCR_WARN] Extraction discarded: Image does not contain a verified valid IPv4 structure.")
            return OcrParseOutput.Unknown(rawText, logs)
        }

        fun extractString(key: String): String {
            val r = Regex("""$key\s*[:\-=]?\s*([A-Za-z0-9_#\-]+)""", RegexOption.IGNORE_CASE)
            return r.find(rawText)?.groupValues?.get(1)?.trim() ?: ""
        }

        fun extractInt(key: String): Int {
            val r = Regex("""$key\s*(?:lvl|level)?\s*[:\-=]?\s*([0-9,]+)""", RegexOption.IGNORE_CASE)
            val v = r.find(rawText)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
            return v
        }

        val name = extractString("Name").ifBlank { extractString("Account") }
        val crew = extractString("Crew").ifBlank { extractString("Clan") }
        val lvl = extractInt("Lvl").let { if (it == 0) extractInt("Level") else it }
        val rep = extractInt("Rep").let { if (it == 0) extractInt("Reputation") else it }
        val score = extractInt("Score").toLong()
        val fw = extractInt("Firewall").let { if (it == 0) extractInt("FW") else it }
        val enc = extractInt("Encryptor").let { if (it == 0) extractInt("ENCR") else it }

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

        logs.add("[OCR_SUMMARY] Successfully extracted profile for IP: $ip / User: ${name.ifBlank { "UNASSIGNED" }}")
        return OcrParseOutput.AccountData(account, logs)
    }

    val SAMPLE_PROFILE_OCR = """
TARGET ACCOUNT PROFILE
Name: ShadowByte
Crew: NetPhantoms
Lvl: 84
Rep: 3450
Score: 198,240
IP: 250.96.171.15
Firewall lvl 72
Encryptor lvl 80
""".trimIndent()

    val SAMPLE_APPS_OCR = """
APPS ShadowByte
IP: 250.96.171.15
Installed Software:
Antivirus lvl 65
Spam lvl 45
Rootkit lvl 70
Firewall lvl 72
Bypasser lvl 58
Password Cracker lvl 82
Password Encryptor lvl 80
Proxy lvl 50
Trace lvl 60
Keygen lvl 75
Siphon lvl 68
""".trimIndent()
}

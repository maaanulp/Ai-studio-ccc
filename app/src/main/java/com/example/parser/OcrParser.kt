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
    val enc: Int = 0
)

data class OcrAppsResult(
    val accountName: String = "",
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
    val siphonLvl: Int = 0
)

sealed class OcrParseOutput {
    data class AccountData(val account: OcrAccountResult, val logs: List<String>) : OcrParseOutput()
    data class AppsData(val apps: OcrAppsResult, val logs: List<String>) : OcrParseOutput()
    data class Unknown(val rawText: String, val logs: List<String>) : OcrParseOutput()
}

object OcrParser {

    fun parseOcrText(rawText: String): OcrParseOutput {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val logs = mutableListOf<String>()

        logs.add("[OCR_ENGINE] Processing ${lines.size} text tokens...")

        val hasAppsKeyword = rawText.contains("APPS", ignoreCase = true) ||
                             rawText.contains("SOFTWARE", ignoreCase = true) ||
                             rawText.contains("Antivirus", ignoreCase = true)

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
                // Try finding name from first line or "User:" line
                val userRegex = Regex("""(?:User|Account|Target|Name):\s*([A-Za-z0-9_#\-]+)""", RegexOption.IGNORE_CASE)
                val uMatch = userRegex.find(rawText)
                if (uMatch != null) {
                    accountName = uMatch.groupValues[1].trim()
                    logs.add("[OCR_EXTRACT] Target Account identified: $accountName")
                }
            }

            fun extractLevel(appName: String, altNames: List<String> = emptyList()): Int {
                val allPatterns = listOf(appName) + altNames
                for (pat in allPatterns) {
                    val regex = Regex("""$pat\s*(?:lvl|level|v|\:)?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
                    val m = regex.find(rawText)
                    if (m != null) {
                        val lvl = m.groupValues[1].toIntOrNull() ?: 0
                        logs.add("[APP_INDEX] Found $appName -> Level $lvl")
                        return lvl
                    }
                }
                return 0
            }

            val apps = OcrAppsResult(
                accountName = accountName,
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
                siphonLvl = extractLevel("Siphon", listOf("Crypto Siphon"))
            )

            logs.add("[OCR_SUMMARY] Successfully parsed software matrix for '$accountName'")
            return OcrParseOutput.AppsData(apps, logs)
        }

        // Account Profile Screenshot
        logs.add("[OCR_DETECT] Signature matched: Account Profile Screenshot")

        var ip = ""
        val ipRegex = Regex("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b""")
        val ipMatch = ipRegex.find(rawText)
        if (ipMatch != null) {
            ip = ipMatch.value
            logs.add("[OCR_EXTRACT] IP Address: $ip")
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

        if (ip.isBlank() && name.isBlank()) {
            logs.add("[OCR_WARN] Low confidence extraction. Check image clarity or use Manual Entry.")
            return OcrParseOutput.Unknown(rawText, logs)
        }

        val account = OcrAccountResult(
            name = name,
            crew = crew,
            level = lvl,
            rep = rep,
            score = score,
            ip = ip,
            fw = fw,
            enc = enc
        )

        logs.add("[OCR_SUMMARY] Successfully extracted profile for IP: ${ip.ifBlank { "Unassigned" }} / User: $name")
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

package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.DatabaseScope
import com.example.data.model.LogEntryEntity
import com.example.data.model.RaidLogEntity
import com.example.data.model.TargetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GeminiParsedLog(
    val ip: String,
    val wallet: String?,
    val stolenAmount: Long,
    val timestampStr: String,
    val logType: String // "INPUT" or "OUTPUT"
)

data class GeminiParsedTarget(
    val ip: String?,
    val name: String?,
    val level: Int = 1,
    val fw: Int = 0,
    val enc: Int = 0,
    val wallet: String? = null,
    val crew: String? = null,
    val stolenCrypto: Long = 0L,
    val rep: Int = 0,
    val score: Long = 0L,
    val antivirusLvl: Int = 0,
    val spamLvl: Int = 0,
    val rootkitLvl: Int = 0,
    val firewallAppLvl: Int = 0,
    val bypasserLvl: Int = 0,
    val passwordCrackerLvl: Int = 0,
    val passwordEncryptorLvl: Int = 0,
    val proxyLvl: Int = 0,
    val traceLvl: Int = 0,
    val keygenLvl: Int = 0,
    val siphonLvl: Int = 0,
    val appsParsed: Boolean = false
)

data class GeminiExtractionResult(
    val screenshotType: String, // "LOGS", "PROFILE", "APPS", "UNKNOWN"
    val summary: String,
    val extractedLogs: List<GeminiParsedLog>,
    val extractedTarget: GeminiParsedTarget?,
    val rawExtractedText: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object GeminiLogExtractionService {

    private const val MODEL_NAME = "gemini-2.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Checks if the Gemini API key is configured.
     */
    fun hasValidApiKey(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Process an image URI using the Gemini multimodal API.
     */
    suspend fun processImageUri(context: Context, imageUri: Uri): GeminiExtractionResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "Failed to open image stream",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "Cannot access selected image file."
                )

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                return@withContext GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "Failed to decode image bitmap",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "Image decoding failed. Ensure file is a valid PNG or JPEG."
                )
            }

            processBitmap(bitmap)
        } catch (e: Exception) {
            GeminiExtractionResult(
                screenshotType = "UNKNOWN",
                summary = "Error: ${e.message}",
                extractedLogs = emptyList(),
                extractedTarget = null,
                rawExtractedText = "",
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Unknown error while processing image"
            )
        }
    }

    /**
     * Process a bitmap directly using the Gemini multimodal API.
     */
    suspend fun processBitmap(bitmap: Bitmap): GeminiExtractionResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiExtractionResult(
                screenshotType = "UNKNOWN",
                summary = "Gemini API Key missing or not set in AI Studio Secrets panel.",
                extractedLogs = emptyList(),
                extractedTarget = null,
                rawExtractedText = "",
                isSuccess = false,
                errorMessage = "GEMINI_API_KEY is not configured. Please add your Gemini API key in the Secrets panel in AI Studio."
            )
        }

        // Downscale bitmap if too large to conserve bandwidth and latency
        val scaledBitmap = scaleBitmapDown(bitmap, maxDimension = 1536)
        val base64Image = bitmapToBase64(scaledBitmap)

        val prompt = """
            You are an expert OCR and intelligence extractor for cyber attack logs and target dossiers.
            Analyze this uploaded screenshot carefully.
            The screenshot typically depicts:
            1. Ingested Logs (Personal Input logs or Victim Output raid logs). In these logs:
               - IP addresses appear (e.g. 192.168.1.100 or masked xxx.xxx.xxx.xxx)
               - Crypto amounts stolen or transferred appear (numbers followed by ₡, Cr, or crypto)
               - Victim or target crypto wallet hashes appear (e.g. 0x892a...f412)
               - Timestamps appear in format YYYY-MM-DD HH:MM:SS or similar
               - Log type is "INPUT" (personal logs showing raids on targets) or "OUTPUT" (victim logs showing who raided them)
            2. Or a Target Profile Screen (showing Account Name, IP address, Level, Reputation, Firewall FW, Encryption ENC, Wallet, Crew).
            3. Or an Installed APPS Screen (showing Antivirus, Firewall, Password Encryptor, Password Cracker, etc.).

            Your task is to extract ALL textual and structured data from this screenshot and output ONLY a JSON object with this exact structure:
            {
              "screenshotType": "LOGS" | "PROFILE" | "APPS" | "UNKNOWN",
              "summary": "Short 1-2 sentence description of what was extracted",
              "rawExtractedText": "Full reconstructed text extracted from the screenshot",
              "extractedLogs": [
                {
                  "ip": "string",
                  "wallet": "string or null",
                  "stolenAmount": 0,
                  "timestampStr": "YYYY-MM-DD HH:MM:SS",
                  "logType": "INPUT" | "OUTPUT"
                }
              ],
              "extractedTarget": {
                "ip": "string or null",
                "name": "string or null",
                "level": 0,
                "fw": 0,
                "enc": 0,
                "wallet": "string or null",
                "crew": "string or null",
                "stolenCrypto": 0,
                "rep": 0,
                "score": 0,
                "antivirusLvl": 0,
                "spamLvl": 0,
                "rootkitLvl": 0,
                "firewallAppLvl": 0,
                "bypasserLvl": 0,
                "passwordCrackerLvl": 0,
                "passwordEncryptorLvl": 0,
                "proxyLvl": 0,
                "traceLvl": 0,
                "keygenLvl": 0,
                "siphonLvl": 0
              }
            }
            Extract installed software/apps levels if present on the screen (Antivirus, Spam, Rootkit, Firewall, Bypasser, Password Cracker, Password Encryptor, Proxy, Trace, Keygen, Siphon).
            Do not wrap in markdown quotes if possible, output pure JSON.
        """.trimIndent()

        val jsonPayload = JSONObject().apply {
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()

            // Text prompt part
            partsArray.put(JSONObject().put("text", prompt))

            // Inline image data part
            val inlineDataObj = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Image)
            }
            partsArray.put(JSONObject().put("inlineData", inlineDataObj))

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            put("contents", contentsArray)

            // Generation config with JSON response type
            val genConfig = JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            }
            put("generationConfig", genConfig)
        }

        val requestUrl = "$BASE_URL?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .build()

        try {
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                val code = response.code
                return@withContext GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "Gemini API request failed (HTTP $code)",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "Gemini API returned error code $code: ${responseBody?.take(200)}"
                )
            }

            parseGeminiApiResponse(responseBody)
        } catch (e: Exception) {
            GeminiExtractionResult(
                screenshotType = "UNKNOWN",
                summary = "Network exception calling Gemini API",
                extractedLogs = emptyList(),
                extractedTarget = null,
                rawExtractedText = "",
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Unknown network failure"
            )
        }
    }

    private fun parseGeminiApiResponse(responseJsonStr: String): GeminiExtractionResult {
        return try {
            val root = JSONObject(responseJsonStr)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "No response candidates returned by Gemini",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "Gemini API generated no candidates."
                )
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textContent = parts?.optJSONObject(0)?.optString("text") ?: ""

            if (textContent.isBlank()) {
                return GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "Empty text in Gemini candidate content",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "Gemini returned empty text content."
                )
            }

            // Clean markdown wrapping if present
            val cleanJson = textContent.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsedJson = JSONObject(cleanJson)
            val screenshotType = parsedJson.optString("screenshotType", "UNKNOWN")
            val summary = parsedJson.optString("summary", "Extraction completed via Gemini Vision")
            val rawExtractedText = parsedJson.optString("rawExtractedText", "")

            // Parse extractedLogs
            val logsList = mutableListOf<GeminiParsedLog>()
            val logsArray = parsedJson.optJSONArray("extractedLogs")
            if (logsArray != null) {
                for (i in 0 until logsArray.length()) {
                    val logObj = logsArray.getJSONObject(i)
                    val ip = logObj.optString("ip", "").trim()
                    if (ip.isNotBlank()) {
                        logsList.add(
                            GeminiParsedLog(
                                ip = ip,
                                wallet = logObj.optString("wallet").takeIf { it.isNotBlank() && it != "null" },
                                stolenAmount = logObj.optLong("stolenAmount", 0L),
                                timestampStr = logObj.optString("timestampStr", ""),
                                logType = logObj.optString("logType", "INPUT")
                            )
                        )
                    }
                }
            }

            // Parse extractedTarget
            var parsedTarget: GeminiParsedTarget? = null
            val targetObj = parsedJson.optJSONObject("extractedTarget")
            if (targetObj != null) {
                val ip = targetObj.optString("ip").takeIf { it.isNotBlank() && it != "null" }
                val name = targetObj.optString("name").takeIf { it.isNotBlank() && it != "null" }
                if (ip != null || name != null) {
                    val avLvl = targetObj.optInt("antivirusLvl", 0)
                    val spmLvl = targetObj.optInt("spamLvl", 0)
                    val rkLvl = targetObj.optInt("rootkitLvl", 0)
                    val fwAppLvl = targetObj.optInt("firewallAppLvl", 0)
                    val byLvl = targetObj.optInt("bypasserLvl", 0)
                    val pcLvl = targetObj.optInt("passwordCrackerLvl", 0)
                    val peLvl = targetObj.optInt("passwordEncryptorLvl", 0)
                    val pxLvl = targetObj.optInt("proxyLvl", 0)
                    val trLvl = targetObj.optInt("traceLvl", 0)
                    val kgLvl = targetObj.optInt("keygenLvl", 0)
                    val siphLvl = targetObj.optInt("siphonLvl", 0)
                    val hasApps = avLvl > 0 || spmLvl > 0 || rkLvl > 0 || fwAppLvl > 0 || byLvl > 0 || pcLvl > 0 || peLvl > 0 || pxLvl > 0 || trLvl > 0 || kgLvl > 0 || siphLvl > 0

                    parsedTarget = GeminiParsedTarget(
                        ip = ip,
                        name = name,
                        level = targetObj.optInt("level", 1),
                        fw = targetObj.optInt("fw", 0),
                        enc = targetObj.optInt("enc", 0),
                        wallet = targetObj.optString("wallet").takeIf { it.isNotBlank() && it != "null" },
                        crew = targetObj.optString("crew").takeIf { it.isNotBlank() && it != "null" },
                        stolenCrypto = targetObj.optLong("stolenCrypto", 0L),
                        rep = targetObj.optInt("rep", 0),
                        score = targetObj.optLong("score", 0L),
                        antivirusLvl = avLvl,
                        spamLvl = spmLvl,
                        rootkitLvl = rkLvl,
                        firewallAppLvl = fwAppLvl,
                        bypasserLvl = byLvl,
                        passwordCrackerLvl = pcLvl,
                        passwordEncryptorLvl = peLvl,
                        proxyLvl = pxLvl,
                        traceLvl = trLvl,
                        keygenLvl = kgLvl,
                        siphonLvl = siphLvl,
                        appsParsed = hasApps
                    )
                }
            }

            GeminiExtractionResult(
                screenshotType = screenshotType,
                summary = summary,
                extractedLogs = logsList,
                extractedTarget = parsedTarget,
                rawExtractedText = rawExtractedText,
                isSuccess = true
            )
        } catch (e: Exception) {
            GeminiExtractionResult(
                screenshotType = "UNKNOWN",
                summary = "Failed to parse structured response from Gemini: ${e.message}",
                extractedLogs = emptyList(),
                extractedTarget = null,
                rawExtractedText = responseJsonStr.take(500),
                isSuccess = false,
                errorMessage = "JSON parsing failure: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Converts extracted data into room TargetEntities and LogEntryEntities ready for storage.
     */
    fun formatForDatabaseStorage(
        result: GeminiExtractionResult,
        scope: DatabaseScope,
        contributor: String
    ): Pair<List<TargetEntity>, List<LogEntryEntity>> {
        val targets = mutableListOf<TargetEntity>()
        val logEntries = mutableListOf<LogEntryEntity>()

        // 1. Process explicit target
        result.extractedTarget?.let { pt ->
            val targetIp = pt.ip.takeIf { !it.isNullOrBlank() } ?: (if (!pt.name.isNullOrBlank()) "Pending_IP_${pt.name}" else "Host_${System.currentTimeMillis() % 1000}")
            targets.add(
                TargetEntity(
                    ip = targetIp,
                    name = pt.name ?: "GeminiHost",
                    level = if (pt.level > 0) pt.level else 1,
                    fw = pt.fw,
                    enc = pt.enc,
                    rep = pt.rep,
                    score = pt.score,
                    wallet = pt.wallet ?: "",
                    crew = pt.crew ?: "",
                    stolenCrypto = pt.stolenCrypto,
                    scope = scope,
                    contributor = contributor,
                    lastUpdated = System.currentTimeMillis(),
                    antivirusLvl = pt.antivirusLvl,
                    spamLvl = pt.spamLvl,
                    rootkitLvl = pt.rootkitLvl,
                    firewallAppLvl = pt.firewallAppLvl,
                    bypasserLvl = pt.bypasserLvl,
                    passwordCrackerLvl = pt.passwordCrackerLvl,
                    passwordEncryptorLvl = pt.passwordEncryptorLvl,
                    proxyLvl = pt.proxyLvl,
                    traceLvl = pt.traceLvl,
                    keygenLvl = pt.keygenLvl,
                    siphonLvl = pt.siphonLvl,
                    appsParsed = pt.appsParsed
                )
            )
        }

        // 2. Process logs
        val groupedByIp = result.extractedLogs.groupBy { it.ip }
        for ((ip, logs) in groupedByIp) {
            if (ip.isBlank()) continue
            val totalStolen = logs.sumOf { it.stolenAmount }
            val hitCount = logs.size
            val avg = if (hitCount > 0) totalStolen / hitCount else 0L
            val wallet = logs.mapNotNull { it.wallet }.firstOrNull() ?: ""

            // Add or merge target
            targets.add(
                TargetEntity(
                    ip = ip,
                    name = "Host_$ip",
                    level = 1,
                    fw = 0,
                    enc = 0,
                    wallet = wallet,
                    stolenCrypto = totalStolen,
                    hitCount = hitCount,
                    avgPerHit = avg,
                    scope = scope,
                    contributor = contributor,
                    lastUpdated = System.currentTimeMillis()
                )
            )

            for (log in logs) {
                logEntries.add(
                    LogEntryEntity(
                        logType = log.logType,
                        rawText = "IP: ${log.ip} | Crypto: ${log.stolenAmount} ₡ | Wallet: ${log.wallet ?: "--"} | Timestamp: ${log.timestampStr}",
                        parsedIp = log.ip,
                        parsedWallet = log.wallet,
                        parsedAmount = log.stolenAmount,
                        eventTimestamp = log.timestampStr,
                        scope = scope,
                        contributor = contributor
                    )
                )
            }
        }

        return Pair(targets, logEntries)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var resizedWidth = maxDimension
        var resizedHeight = maxDimension

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = ((resizedHeight.toFloat() / originalHeight.toFloat()) * originalWidth).toInt()
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight = ((resizedWidth.toFloat() / originalWidth.toFloat()) * originalHeight).toInt()
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension
            resizedWidth = maxDimension
        }

        return if (originalWidth > maxDimension || originalHeight > maxDimension) {
            Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false)
        } else {
            bitmap
        }
    }
}

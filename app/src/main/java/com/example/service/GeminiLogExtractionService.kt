package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.DatabaseScope
import com.example.data.model.LogEntryEntity
import com.example.data.model.TargetEntity
import com.example.parser.LogParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

data class GeminiParsedLog(
    val ip: String,
    val wallet: String? = null,
    val stolenAmount: Long = 0L,
    val timestampStr: String = "",
    val logType: String = "INPUT"
)

data class GeminiParsedTarget(
    val ip: String? = null,
    val name: String? = null,
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
    val errorMessage: String? = null,
    val usedEngine: String = "GEMINI_API" // "gemini-2.5-flash", "gemini-1.5-flash", "ML_KIT_LOCAL", etc.
)

object GeminiLogExtractionService {

    // Models prioritized in requested order, with cascading fallbacks to eliminate 404s
    private val CANDIDATE_MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-1.5-flash",
        "gemini-flash-latest",
        "gemini-3.5-flash",
        "gemini-3.0-flash"
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    /**
     * Checks if a valid Gemini API key is configured in environment or BuildConfig.
     */
    fun hasValidApiKey(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Retrieves the Gemini API key from environment variable or BuildConfig.
     */
    fun getApiKey(): String {
        return try {
            val envKey = System.getenv("GEMINI_API_KEY") ?: ""
            if (envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY") {
                return envKey
            }
            val buildConfigKey = BuildConfig.GEMINI_API_KEY
            if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
                return buildConfigKey
            }
            ""
        } catch (_: Exception) {
            try {
                System.getenv("GEMINI_API_KEY") ?: ""
            } catch (_: Exception) {
                ""
            }
        }
    }

    /**
     * Process an image URI using the Gemini multimodal Vision API or local Google ML Kit fallback.
     */
    suspend fun processImageUri(context: Context, imageUri: Uri): GeminiExtractionResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext processWithLocalMlKitFallbackUri(context, imageUri)

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
     * Process a bitmap with Gemini Vision API, automatically falling back to Google ML Kit Text Recognition.
     */
    suspend fun processBitmap(bitmap: Bitmap): GeminiExtractionResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()

        // 1. If API key is present, attempt Gemini Multimodal Vision API across candidate models
        if (hasValidApiKey()) {
            for (model in CANDIDATE_MODELS) {
                val apiResult = callGeminiMultimodalApi(bitmap, apiKey, model)
                if (apiResult.isSuccess && (apiResult.extractedLogs.isNotEmpty() || apiResult.extractedTarget != null)) {
                    return@withContext apiResult
                }
            }
        }

        // 2. Local Fallback: Google ML Kit Text Recognition on device
        val localResult = processWithLocalMlKit(bitmap)
        if (localResult.isSuccess && (localResult.extractedLogs.isNotEmpty() || localResult.extractedTarget != null)) {
            return@withContext localResult
        }

        return@withContext localResult
    }

    /**
     * Executes multimodal Gemini API call for a specific model name.
     */
    private fun callGeminiMultimodalApi(bitmap: Bitmap, apiKey: String, modelName: String): GeminiExtractionResult {
        return try {
            val scaledBitmap = scaleBitmapDown(bitmap, maxDimension = 1536)
            val base64Image = bitmapToBase64(scaledBitmap)

            val prompt = """
                You are an expert OCR and intelligence extractor for Hack EX cyber attack logs and target dossiers.
                Analyze this screenshot carefully.
                Extract all textual and structured data from this screenshot and output ONLY a JSON object with this exact structure:
                {
                  "screenshotType": "LOGS" | "PROFILE" | "APPS" | "UNKNOWN",
                  "summary": "Short 1-2 sentence description of what was extracted",
                  "rawExtractedText": "Full reconstructed text extracted from the screenshot",
                  "extractedLogs": [
                    {
                      "ip": "string",
                      "wallet": "string or null",
                      "stolenAmount": 0,
                      "timestampStr": "string",
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
                Do not wrap in markdown quotes if possible, output pure JSON.
            """.trimIndent()

            val jsonPayload = JSONObject().apply {
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()

                partsArray.put(JSONObject().put("text", prompt))

                val inlineDataObj = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                }
                partsArray.put(JSONObject().put("inlineData", inlineDataObj))

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                }
                put("generationConfig", genConfig)
            }

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = jsonPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "Gemini API request with $modelName returned HTTP ${response.code}",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "HTTP ${response.code}: ${responseBody?.take(150)}",
                    usedEngine = modelName
                )
            }

            val parsed = parseGeminiApiResponse(responseBody)
            parsed.copy(usedEngine = modelName)
        } catch (e: Exception) {
            GeminiExtractionResult(
                screenshotType = "UNKNOWN",
                summary = "Network exception calling Gemini API ($modelName)",
                extractedLogs = emptyList(),
                extractedTarget = null,
                rawExtractedText = "",
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Network connection error",
                usedEngine = modelName
            )
        }
    }

    /**
     * Local OCR Engine: Google ML Kit Text Recognition fallback.
     * Extracts text on-device without needing internet or API keys, and runs domain heuristic parser.
     */
    suspend fun processWithLocalMlKit(bitmap: Bitmap): GeminiExtractionResult = withContext(Dispatchers.IO) {
        try {
            val rawText = runMlKitTextRecognition(bitmap)
            if (rawText.isBlank()) {
                return@withContext GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "Local ML Kit OCR completed: no text detected in screenshot.",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "No text detected by Google ML Kit.",
                    usedEngine = "ML_KIT_LOCAL"
                )
            }

            parseLocalExtractedText(rawText)
        } catch (e: Exception) {
            GeminiExtractionResult(
                screenshotType = "UNKNOWN",
                summary = "Local ML Kit OCR exception: ${e.message}",
                extractedLogs = emptyList(),
                extractedTarget = null,
                rawExtractedText = "",
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "ML Kit execution error",
                usedEngine = "ML_KIT_LOCAL"
            )
        }
    }

    private suspend fun processWithLocalMlKitFallbackUri(context: Context, imageUri: Uri): GeminiExtractionResult = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val rawText = suspendCancellableCoroutine<String> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText -> continuation.resume(visionText.text ?: "") }
                    .addOnFailureListener { continuation.resume("") }
            }
            if (rawText.isNotBlank()) {
                parseLocalExtractedText(rawText)
            } else {
                GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "Local ML Kit OCR: empty text",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "Cannot extract text from image URI.",
                    usedEngine = "ML_KIT_LOCAL"
                )
            }
        } catch (e: Exception) {
            GeminiExtractionResult(
                screenshotType = "UNKNOWN",
                summary = "Error accessing image URI: ${e.message}",
                extractedLogs = emptyList(),
                extractedTarget = null,
                rawExtractedText = "",
                isSuccess = false,
                errorMessage = e.localizedMessage,
                usedEngine = "ML_KIT_LOCAL"
            )
        }
    }

    private suspend fun runMlKitTextRecognition(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text ?: "")
                }
                .addOnFailureListener {
                    continuation.resume("")
                }
        } catch (_: Exception) {
            continuation.resume("")
        }
    }

    /**
     * Parses raw extracted OCR text into structured targets, logs, wallets and apps.
     */
    fun parseLocalExtractedText(rawText: String): GeminiExtractionResult {
        // 1. Try LogParser first (for Hack EX 2 reverse chronological logs)
        val parseResult = LogParser.parseLogs(rawText, DatabaseScope.INTERNAL, contributor = "MLKit_OCR")
        val parsedLogs = mutableListOf<GeminiParsedLog>()

        for (raid in parseResult.raidLogs) {
            parsedLogs.add(
                GeminiParsedLog(
                    ip = raid.ip,
                    wallet = raid.wallet.takeIf { it.isNotBlank() },
                    stolenAmount = raid.stolenAmount,
                    timestampStr = raid.timestampStr,
                    logType = "INPUT"
                )
            )
        }

        // 2. Extract standalone IPs and Wallets
        val ipPattern = Pattern.compile("""\b(?:[0-9]{1,3}\.){3}[0-9]{1,3}\b""")
        val ipMatcher = ipPattern.matcher(rawText)
        val allIps = mutableListOf<String>()
        while (ipMatcher.find()) {
            val foundIp = ipMatcher.group()
            if (!foundIp.startsWith("0.") && !foundIp.startsWith("127.") && !foundIp.contains("xxx")) {
                allIps.add(foundIp)
            }
        }

        val walletPattern = Pattern.compile("""\b(hx[a-zA-Z0-9\.]{6,}|0x[a-zA-Z0-9\.]{6,}|[a-zA-Z0-9]{4,}\.{3}[a-zA-Z0-9]{4,})\b""")
        val walletMatcher = walletPattern.matcher(rawText)
        val allWallets = mutableListOf<String>()
        while (walletMatcher.find()) {
            allWallets.add(walletMatcher.group())
        }

        // Level, FW, ENC, Rep, Crypto patterns
        val levelMatch = Regex("""(?:Level|LVL|Lvl)\s*[:#]?\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawText)
        val fwMatch = Regex("""(?:FW|Firewall)\s*[:#]?\s*(?:Lvl\s*)?(\d+)""", RegexOption.IGNORE_CASE).find(rawText)
        val encMatch = Regex("""(?:ENC|Encryption)\s*[:#]?\s*(?:Lvl\s*)?(\d+)""", RegexOption.IGNORE_CASE).find(rawText)
        val repMatch = Regex("""(?:Rep|Reputation)\s*[:#]?\s*([0-9,]+)""", RegexOption.IGNORE_CASE).find(rawText)
        val cryptoMatch = Regex("""(?:Crypto|Stole|Amount)\s*[:#]?\s*([0-9,]+)""", RegexOption.IGNORE_CASE).find(rawText)

        // App levels
        val avMatch = Regex("""(?:Antivirus|AV)\s*[:#]?\s*(?:v|lvl)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawText)
        val spamMatch = Regex("""(?:Spam)\s*[:#]?\s*(?:v|lvl)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawText)
        val rkMatch = Regex("""(?:Rootkit)\s*[:#]?\s*(?:v|lvl)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawText)
        val bypasserMatch = Regex("""(?:Bypasser)\s*[:#]?\s*(?:v|lvl)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawText)
        val pcMatch = Regex("""(?:Password Cracker|Cracker)\s*[:#]?\s*(?:v|lvl)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawText)
        val peMatch = Regex("""(?:Password Encryptor|Encryptor)\s*[:#]?\s*(?:v|lvl)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(rawText)

        // Pair unmatched IPs to detected wallets
        if (parsedLogs.isEmpty() && allIps.isNotEmpty()) {
            for (i in allIps.indices) {
                val ip = allIps[i]
                val wallet = allWallets.getOrNull(i)
                val amount = cryptoMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: 0L
                parsedLogs.add(
                    GeminiParsedLog(
                        ip = ip,
                        wallet = wallet,
                        stolenAmount = amount,
                        timestampStr = "00-00 00:00",
                        logType = "INPUT"
                    )
                )
            }
        }

        val primaryIp = allIps.firstOrNull() ?: parseResult.targets.firstOrNull()?.ip
        val primaryWallet = allWallets.firstOrNull() ?: parseResult.targets.firstOrNull()?.wallet

        val detectedTarget = if (primaryIp != null || primaryWallet != null || levelMatch != null) {
            val totalCrypto = cryptoMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
                ?: parseResult.targets.sumOf { it.stolenCrypto }

            GeminiParsedTarget(
                ip = primaryIp,
                name = primaryIp?.let { "Host-$it" } ?: "Target_Node",
                level = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                fw = fwMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                enc = encMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                wallet = primaryWallet,
                stolenCrypto = totalCrypto,
                rep = repMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0,
                antivirusLvl = avMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                spamLvl = spamMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                rootkitLvl = rkMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                bypasserLvl = bypasserMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                passwordCrackerLvl = pcMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                passwordEncryptorLvl = peMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                appsParsed = avMatch != null || bypasserMatch != null || pcMatch != null || peMatch != null
            )
        } else null

        val isLogsType = parsedLogs.isNotEmpty() || rawText.contains("Accessed device", ignoreCase = true) || rawText.contains("Stole", ignoreCase = true)
        val screenshotType = when {
            isLogsType -> "LOGS"
            detectedTarget?.appsParsed == true -> "APPS"
            detectedTarget != null -> "PROFILE"
            else -> "UNKNOWN"
        }

        val summary = "[LOCAL_OCR] Extracted ${parsedLogs.size} logs, target: ${detectedTarget?.ip ?: "None"} via Google ML Kit Text Recognition."

        return GeminiExtractionResult(
            screenshotType = screenshotType,
            summary = summary,
            extractedLogs = parsedLogs,
            extractedTarget = detectedTarget,
            rawExtractedText = rawText,
            isSuccess = parsedLogs.isNotEmpty() || detectedTarget != null || rawText.isNotBlank(),
            errorMessage = null,
            usedEngine = "ML_KIT_LOCAL"
        )
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

            val cleanJson = textContent.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsedJson = JSONObject(cleanJson)
            val screenshotType = parsedJson.optString("screenshotType", "UNKNOWN")
            val summary = parsedJson.optString("summary", "Extraction completed via Gemini Vision")
            val rawExtractedText = parsedJson.optString("rawExtractedText", "")

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
                isSuccess = true,
                usedEngine = "GEMINI_API"
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
                    name = pt.name ?: "Target-$targetIp",
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
                    name = "Target-$ip",
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

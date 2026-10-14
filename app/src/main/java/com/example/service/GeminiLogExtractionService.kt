package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.data.model.DatabaseScope
import com.example.data.model.LogEntryEntity
import com.example.data.model.TargetEntity
import com.example.parser.ImagePreprocessor
import com.example.parser.LogParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
    val hotWallet: Long = 0L,
    val coldStorageCurrent: Long = 0L,
    val coldStorageMax: Long = 0L,
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

    // Models prioritized in requested order, placing gemini-1.5-flash first for complex software vision screens
    private val CANDIDATE_MODELS = listOf(
        "gemini-1.5-flash",
        "gemini-flash-latest",
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-3.0-flash"
    )

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
            val envKey = System.getenv("GEMINI_API_KEY") ?: System.getenv("API_KEY") ?: ""
            if (envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY") {
                return envKey
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Retrieves the Groq API key from environment variable or System property.
     */
    fun getGroqApiKey(): String {
        return try {
            val envKey = System.getenv("GROQ_API_KEY") ?: System.getProperty("GROQ_API_KEY") ?: ""
            if (envKey.isNotBlank() && envKey != "MY_GROQ_API_KEY") {
                return envKey
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Retrieves the OpenRouter API key from environment variable or System property.
     */
    fun getOpenRouterApiKey(): String {
        return try {
            val envKey = System.getenv("OPENROUTER_API_KEY") ?: System.getProperty("OPENROUTER_API_KEY") ?: ""
            if (envKey.isNotBlank() && envKey != "MY_OPENROUTER_API_KEY") {
                return envKey
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Process an image URI using the Multimodal Fallback Chain (Gemini -> Groq -> OpenRouter -> ML Kit).
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
     * Process a bitmap using the Multimodal Fallback Chain:
     * Step 1 (Primary): Google Gemini API (gemini-1.5-flash / gemini-2.0-flash)
     * Step 2 (Backup 1): Groq API (llama-3.2-11b-vision-instruct)
     * Step 3 (Backup 2): OpenRouter API (meta-llama/llama-3.2-11b-vision-instruct:free / google/gemini-2.0-flash-lite-001)
     * Step 4 (Last Fallback): Google ML Kit Text Recognition on device.
     */
    suspend fun processBitmap(bitmap: Bitmap): GeminiExtractionResult = withContext(Dispatchers.IO) {
        val geminiKey = getApiKey()
        val groqKey = getGroqApiKey()
        val openRouterKey = getOpenRouterApiKey()

        // 1. Immediate Negative Color Inversion + Adaptive Binarization
        val preprocessedBitmap = ImagePreprocessor.preprocessNegativeAdaptiveBinarization(bitmap)
        val scaledBitmap = scaleBitmapDown(preprocessedBitmap, maxDimension = 1536)
        val base64Image = bitmapToBase64(scaledBitmap)

        // Step 0 (Primary) -> Official Firebase AI Logic (Gemini 3.5 Flash)
        try {
            val aiLogicResult = callFirebaseAiLogicMultimodal(preprocessedBitmap)
            if (aiLogicResult != null && aiLogicResult.isSuccess && (aiLogicResult.extractedLogs.isNotEmpty() || aiLogicResult.extractedTarget != null)) {
                val verifiedResult = verifyAndRescanIfIncomplete(bitmap, aiLogicResult)
                if (preprocessedBitmap != bitmap) preprocessedBitmap.recycle()
                return@withContext verifiedResult
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[VISION_FALLBACK] Firebase AI Logic skipped or failed: ${e.message}")
        }

        // Step 1 (Fallback) -> Direct Gemini API (if key present)
        if (geminiKey.isNotBlank() && geminiKey != "MY_GEMINI_API_KEY") {
            for (model in CANDIDATE_MODELS) {
                val apiResult = callGeminiMultimodalApi(preprocessedBitmap, geminiKey, model)
                if (apiResult.isSuccess && (apiResult.extractedLogs.isNotEmpty() || apiResult.extractedTarget != null)) {
                    val verifiedResult = verifyAndRescanIfIncomplete(bitmap, apiResult)
                    if (preprocessedBitmap != bitmap) preprocessedBitmap.recycle()
                    return@withContext verifiedResult
                } else if (apiResult.errorMessage?.contains("429") == true || apiResult.errorMessage?.contains("503") == true || apiResult.errorMessage?.contains("Quota") == true) {
                    println("[VISION_FALLBACK] Gemini Limit -> Swapping to Groq")
                    break
                }
            }
        }

        // Step 2 (Backup 1) -> Groq API
        if (groqKey.isNotBlank()) {
            println("[VISION_FALLBACK] Executing Groq Vision API (llama-3.2-11b-vision-instruct)")
            val groqResult = callOpenAiCompatibleVisionApi(
                endpointUrl = "https://api.groq.com/openai/v1/chat/completions",
                apiKey = groqKey,
                modelName = "llama-3.2-11b-vision-instruct",
                base64Image = base64Image,
                providerName = "GROQ"
            )
            if (groqResult.isSuccess && (groqResult.extractedLogs.isNotEmpty() || groqResult.extractedTarget != null)) {
                val verifiedResult = verifyAndRescanIfIncomplete(bitmap, groqResult)
                if (preprocessedBitmap != bitmap) preprocessedBitmap.recycle()
                return@withContext verifiedResult
            } else {
                println("[VISION_FALLBACK] Groq Limit/Error -> Swapping to OpenRouter")
            }
        }

        // Step 3 (Backup 2) -> OpenRouter API
        if (openRouterKey.isNotBlank()) {
            println("[VISION_FALLBACK] Executing OpenRouter Vision API")
            for (model in listOf("meta-llama/llama-3.2-11b-vision-instruct:free", "google/gemini-2.0-flash-lite-001")) {
                val openRouterResult = callOpenAiCompatibleVisionApi(
                    endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                    apiKey = openRouterKey,
                    modelName = model,
                    base64Image = base64Image,
                    providerName = "OPENROUTER",
                    extraHeaders = mapOf(
                        "HTTP-Referer" to "https://aistudio.google.com",
                        "X-Title" to "Crypt0 Cr3w Central"
                    )
                )
                if (openRouterResult.isSuccess && (openRouterResult.extractedLogs.isNotEmpty() || openRouterResult.extractedTarget != null)) {
                    val verifiedResult = verifyAndRescanIfIncomplete(bitmap, openRouterResult)
                    if (preprocessedBitmap != bitmap) preprocessedBitmap.recycle()
                    return@withContext verifiedResult
                }
            }
        }

        // Step 4 (Last Fallback) -> Google ML Kit Text Recognition on device
        println("[VISION_FALLBACK] AI APIs Unavailable -> Swapping to Local ML Kit")
        val localResult = processWithLocalMlKit(preprocessedBitmap)
        val finalResult = verifyAndRescanIfIncomplete(bitmap, localResult)
        if (preprocessedBitmap != bitmap) preprocessedBitmap.recycle()
        return@withContext finalResult
    }

    /**
     * Integrity verification: Checks if extracted software items meet the 11-app matrix.
     * If incomplete, crops the software ROI and performs an aggressive contrast rescan.
     */
    private suspend fun verifyAndRescanIfIncomplete(
        originalBitmap: Bitmap,
        initialResult: GeminiExtractionResult
    ): GeminiExtractionResult {
        if (initialResult.screenshotType != "APPS" || initialResult.extractedTarget == null) {
            return initialResult
        }

        val target = initialResult.extractedTarget
        fun countConfirmed(t: GeminiParsedTarget): Int = listOf(
            t.antivirusLvl, t.spamLvl, t.rootkitLvl, t.firewallAppLvl,
            t.bypasserLvl, t.passwordCrackerLvl, t.passwordEncryptorLvl,
            t.proxyLvl, t.traceLvl, t.keygenLvl, t.siphonLvl
        ).count { it in 1..50 }

        val currentCount = countConfirmed(target)
        if (currentCount >= 11) {
            return initialResult
        }

        // Integrity verification triggered: Less than 11 software apps confirmed!
        return try {
            val roiBitmap = ImagePreprocessor.cropSoftwareRoi(originalBitmap)
            val aggressiveBitmap = ImagePreprocessor.preprocessAggressiveContrastBinarization(roiBitmap)
            val rescanRawText = runMlKitTextRecognition(aggressiveBitmap)

            // 11 Equidistant Strips Scanning Pass:
            val strips = ImagePreprocessor.crop11EquidistantStrips(roiBitmap)
            val stripLevels = IntArray(11)
            for (i in 0 until 11) {
                val stripBmp = ImagePreprocessor.preprocessAggressiveContrastBinarization(strips[i])
                val stripText = runMlKitTextRecognition(stripBmp)
                if (stripBmp != strips[i]) stripBmp.recycle()
                strips[i].recycle()
                val lvl = com.example.parser.OcrParser.extractLevelFromTextToRight(stripText)
                    ?: com.example.parser.OcrParser.extractLevelFromLine(stripText)
                    ?: 0
                stripLevels[i] = if (lvl in 1..50) lvl else 0
            }

            if (aggressiveBitmap != roiBitmap) aggressiveBitmap.recycle()
            if (roiBitmap != originalBitmap) roiBitmap.recycle()

            val rescanAppsResult = com.example.parser.OcrParser.parseAppsMatrix(rescanRawText)
            fun mergeLvl(cur: Int, rescan: Int, strip: Int): Int =
                if (cur in 1..50) cur else if (rescan in 1..50) rescan else if (strip in 1..50) strip else 0

            val updatedTarget = target.copy(
                antivirusLvl = mergeLvl(target.antivirusLvl, rescanAppsResult.antivirusLvl, stripLevels[0]),
                spamLvl = mergeLvl(target.spamLvl, rescanAppsResult.spamLvl, stripLevels[1]),
                rootkitLvl = mergeLvl(target.rootkitLvl, rescanAppsResult.rootkitLvl, stripLevels[2]),
                firewallAppLvl = mergeLvl(target.firewallAppLvl, rescanAppsResult.firewallLvl, stripLevels[3]),
                bypasserLvl = mergeLvl(target.bypasserLvl, rescanAppsResult.bypasserLvl, stripLevels[4]),
                passwordCrackerLvl = mergeLvl(target.passwordCrackerLvl, rescanAppsResult.passwordCrackerLvl, stripLevels[5]),
                passwordEncryptorLvl = mergeLvl(target.passwordEncryptorLvl, rescanAppsResult.passwordEncryptorLvl, stripLevels[6]),
                proxyLvl = mergeLvl(target.proxyLvl, rescanAppsResult.proxyLvl, stripLevels[7]),
                traceLvl = mergeLvl(target.traceLvl, rescanAppsResult.traceLvl, stripLevels[8]),
                keygenLvl = mergeLvl(target.keygenLvl, rescanAppsResult.keygenLvl, stripLevels[9]),
                siphonLvl = mergeLvl(target.siphonLvl, rescanAppsResult.siphonLvl, stripLevels[10]),
                appsParsed = true
            )

            val newCount = countConfirmed(updatedTarget)
            val recovered = newCount - currentCount

            val newSummary = if (newCount == 11) {
                "[NEURAL_VISION_PRO] Verified complete 11/11 software matrix for '${updatedTarget.name}' (Recovered $recovered via aggressive ROI rescan)."
            } else {
                "[NEURAL_VISION_PRO] Confirmed $newCount/11 software levels for '${updatedTarget.name}' (Recovered $recovered via aggressive ROI rescan)."
            }

            initialResult.copy(
                summary = newSummary,
                extractedTarget = updatedTarget,
                rawExtractedText = initialResult.rawExtractedText + "\n[ROI RESCAN]\n" + rescanRawText
            )
        } catch (_: Exception) {
            initialResult
        }
    }

    fun buildVisionPrompt(): String = """
        You are an expert Computer Vision OCR and intelligence extractor for Hack EX screens (Target Profiles, Installed Software Matrices, Crypto Wallets, Attack Logs).
        Analyze this screenshot carefully to classify and extract strictly required fields.

        CLASSIFICATION INSTRUCTIONS:
        1. "APPS": Screenshot contains "// APPS", "Installed Software", or software list (Antivirus, Spam, Rootkit, Firewall, Bypasser, Password Cracker, Password Encryptor, Proxy, Trace, Keygen, Siphon).
           - Target Username: Extract dynamic name from header preceding "installed software".
           - Extract ONLY the numeric level (LVL) for each of the 11 tools: Antivirus, Spam, Rootkit, Firewall, Bypasser, Password Cracker, Password Encryptor, Proxy, Trace, Keygen, Siphon.
           - OMIT delta numbers (such as -4, +5, -38, =) and secondary labels (such as "DEVICE-BOUND").

        2. "PROFILE": Screenshot depicts target profile & system info screen.
           - Username: Dynamic string following '>' symbol in profile header.
           - Crew Tag: Bracketed tag if present (e.g., "[TAG]").
           - Level: Integer after "LVL".
           - Reputation: Integer after "REP" (strip commas/spaces).
           - Score: Integer before "SCORE".
           - System Info (Critical fields only):
             * IP Address: Standard IPv4 address or masked ("XXX.XXX.XXX.XXX").
             * Firewall Level: Integer after "Firewall" / "Lv.".
             * Encryptor Level: Integer after "Encryptor" / "Lv.".
           - OMIT/IGNORE completely: Device, Network, Lifetime, and XP Progress.

        3. "WALLET": Screenshot contains "// CRYPTO WALLET", "WALLET ADDRESS", "HOT WALLET", "COLD STORAGE".
           - Username: Dynamic target name preceding "'s wallet".
           - Wallet Address: Alphanumeric address string under "WALLET ADDRESS".
           - Hot Wallet: Active crypto balance integer.
           - Cold Storage: Current & Max pair integers under "COLD STORAGE".

        4. "LOGS": Screenshot contains attack logs with timestamps, IPs, and crypto amounts.

        Output ONLY a JSON object with this exact structure:
        {
          "screenshotType": "LOGS" | "PROFILE" | "APPS" | "WALLET" | "UNKNOWN",
          "summary": "Short description of extracted data",
          "rawExtractedText": "Reconstructed text from screenshot",
          "extractedLogs": [],
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
            "hotWallet": 0,
            "coldStorageCurrent": 0,
            "coldStorageMax": 0,
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
            "siphonLvl": 0,
            "software_matrix": [
              {
                "software_name": "string",
                "level": 0
              }
            ]
          }
        }
        Do not wrap in markdown quotes if possible, output pure JSON.
    """.trimIndent()

    /**
     * Executes multimodal Gemini API call for a specific model name.
     */
    private fun callGeminiMultimodalApi(bitmap: Bitmap, apiKey: String, modelName: String): GeminiExtractionResult {
        return try {
            val scaledBitmap = scaleBitmapDown(bitmap, maxDimension = 1536)
            val base64Image = bitmapToBase64(scaledBitmap)

            val prompt = buildVisionPrompt()

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
            val url = URL(requestUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.doOutput = true

            conn.outputStream.use { os ->
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (responseCode !in 200..299 || responseBody.isBlank()) {
                return GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "Gemini API request with $modelName returned HTTP $responseCode",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "HTTP $responseCode: ${responseBody.take(150)}",
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
     * Executes OpenAI-compatible Multimodal Chat Completions API call (for Groq and OpenRouter).
     */
    private fun callOpenAiCompatibleVisionApi(
        endpointUrl: String,
        apiKey: String,
        modelName: String,
        base64Image: String,
        providerName: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): GeminiExtractionResult {
        return try {
            val prompt = buildVisionPrompt()

            val jsonPayload = JSONObject().apply {
                put("model", modelName)
                val messages = JSONArray()
                val userMessage = JSONObject().apply {
                    put("role", "user")
                    val contentArray = JSONArray()
                    contentArray.put(JSONObject().apply {
                        put("type", "text")
                        put("text", prompt)
                    })
                    contentArray.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().apply {
                            put("url", "data:image/jpeg;base64,$base64Image")
                        })
                    })
                    put("content", contentArray)
                }
                messages.put(userMessage)
                put("messages", messages)
                put("temperature", 0.1)
                put("response_format", JSONObject().put("type", "json_object"))
            }

            val url = URL(endpointUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            for ((k, v) in extraHeaders) {
                conn.setRequestProperty(k, v)
            }
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            conn.doOutput = true

            conn.outputStream.use { os ->
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val responseBody = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (responseCode !in 200..299 || responseBody.isBlank()) {
                return GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "$providerName request with $modelName returned HTTP $responseCode",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "HTTP $responseCode: ${responseBody.take(150)}",
                    usedEngine = providerName
                )
            }

            val contentJson = parseOpenAiMessageContent(responseBody)
            val parsed = parseGeminiApiResponse(contentJson)
            parsed.copy(usedEngine = "$providerName/$modelName")
        } catch (e: Exception) {
            GeminiExtractionResult(
                screenshotType = "UNKNOWN",
                summary = "Network exception calling $providerName ($modelName)",
                extractedLogs = emptyList(),
                extractedTarget = null,
                rawExtractedText = "",
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Network connection error",
                usedEngine = providerName
            )
        }
    }

    private fun parseOpenAiMessageContent(responseBody: String): String {
        return try {
            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices") ?: return responseBody
            if (choices.length() == 0) return responseBody
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message") ?: return responseBody
            val content = message.optString("content", "")
            if (content.isNotBlank()) content.trim() else responseBody
        } catch (_: Exception) {
            responseBody
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

    private fun extractAppLevelFromText(rawText: String, primaryName: String, altNames: List<String> = emptyList()): Int {
        val candidates = listOf(primaryName) + altNames
        for (name in candidates) {
            val regex = Regex("""(?i)\b${Regex.escape(name)}\b[^\n\r]*?(?:LVL|Lvl|Level|v|#)?\s*[:#\-]?\s*(\d+)""")
            val match = regex.find(rawText)
            if (match != null) {
                val lvl = match.groupValues[1].toIntOrNull()
                if (lvl != null && lvl in 1..50) return lvl
            }
        }
        return 0
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

        // Check if screenshot is an APPS Matrix capture
        val appsKeywords = listOf("APPS", "SOFTWARE", "APPLICATIONS", "INSTALLED SOFTWARE", "INSTALLED", "PROGRAMS")
        val specificApps = listOf("ANTIVIRUS", "BYPASSER", "ROOTKIT", "PASSWORD CRACKER", "KEYGEN", "SIPHON", "SPAM", "FIREWALL", "ENCRYPTOR", "PROXY", "TRACE")
        val hasAppsHeader = appsKeywords.any { rawText.contains(it, ignoreCase = true) }
        val specificAppCount = specificApps.count { rawText.contains(it, ignoreCase = true) }
        val hasLogAccessEntries = parsedLogs.isNotEmpty() || rawText.contains("Accessed device", ignoreCase = true) || rawText.contains("Stole", ignoreCase = true)
        val isAppsScreenshot = !hasLogAccessEntries && (hasAppsHeader || specificAppCount >= 2)

        if (isAppsScreenshot) {
            val appsResult = com.example.parser.OcrParser.parseAppsMatrix(rawText)
            val extractedCount = appsResult.getExtractedCount()

            // Robust multi-tier fallback for account identity:
            val resolvedName = when {
                appsResult.accountName.isNotBlank() && !appsResult.accountName.equals("Target", ignoreCase = true) -> appsResult.accountName
                appsResult.ip.isNotBlank() -> "Target_${appsResult.ip}"
                allIps.isNotEmpty() -> "Target_${allIps.first()}"
                allWallets.isNotEmpty() -> "Target_${allWallets.first().take(8)}"
                else -> "Target_Node_${(rawText.hashCode() and 0xFFFF).toString(16).uppercase()}"
            }
            val finalIp = if (appsResult.ip.isNotBlank()) appsResult.ip else allIps.firstOrNull()

            if (extractedCount > 0) {
                val missing = appsResult.getMissingApps()
                val summaryText = if (extractedCount == 11) {
                    "[LOCAL_OCR] Validated complete 11/11 software matrix for '$resolvedName'."
                } else {
                    "[LOCAL_OCR] Confirmed $extractedCount/11 software levels for '$resolvedName' (Pending: [${missing.joinToString(", ")}]). Ingesting confirmed software."
                }
                val target = GeminiParsedTarget(
                    ip = finalIp,
                    name = resolvedName,
                    crew = appsResult.crew.takeIf { it.isNotBlank() },
                    level = 1,
                    fw = if (appsResult.firewallLvl > 0) appsResult.firewallLvl else 1,
                    enc = if (appsResult.passwordEncryptorLvl > 0) appsResult.passwordEncryptorLvl else 1,
                    wallet = allWallets.firstOrNull(),
                    antivirusLvl = appsResult.antivirusLvl,
                    spamLvl = appsResult.spamLvl,
                    rootkitLvl = appsResult.rootkitLvl,
                    firewallAppLvl = appsResult.firewallLvl,
                    bypasserLvl = appsResult.bypasserLvl,
                    passwordCrackerLvl = appsResult.passwordCrackerLvl,
                    passwordEncryptorLvl = appsResult.passwordEncryptorLvl,
                    proxyLvl = appsResult.proxyLvl,
                    traceLvl = appsResult.traceLvl,
                    keygenLvl = appsResult.keygenLvl,
                    siphonLvl = appsResult.siphonLvl,
                    appsParsed = true
                )
                return GeminiExtractionResult(
                    screenshotType = "APPS",
                    summary = summaryText,
                    extractedLogs = emptyList(),
                    extractedTarget = target,
                    rawExtractedText = rawText,
                    isSuccess = true,
                    errorMessage = null,
                    usedEngine = "ML_KIT_LOCAL"
                )
            }
        }

        // App levels extraction for all 11 Hack EX software
        fun getAppLvl(name: String, alts: List<String> = emptyList()): Int =
            extractAppLevelFromText(rawText, name, alts)

        val avLvl = getAppLvl("Antivirus", listOf("AV", "Anti-Virus"))
        val spamLvl = getAppLvl("Spam", listOf("SpamBot"))
        val rkLvl = getAppLvl("Rootkit", listOf("Root-Kit"))
        val fwAppLvl = getAppLvl("Firewall", listOf("FW", "Firewall App"))
        val bypasserLvl = getAppLvl("Bypasser", listOf("Bypass"))
        val pcLvl = getAppLvl("Password Cracker", listOf("PW Cracker", "Cracker"))
        val peLvl = getAppLvl("Password Encryptor", listOf("PW Encryptor", "Encryptor"))
        val proxyLvl = getAppLvl("Proxy")
        val traceLvl = getAppLvl("Trace", listOf("Tracer"))
        val keygenLvl = getAppLvl("Keygen", listOf("Key-Gen"))
        val siphonLvl = getAppLvl("Siphon", listOf("Crypto Siphon"))

        val hasAnyAppLvl = avLvl > 0 || spamLvl > 0 || rkLvl > 0 || fwAppLvl > 0 || bypasserLvl > 0 ||
                           pcLvl > 0 || peLvl > 0 || proxyLvl > 0 || traceLvl > 0 || keygenLvl > 0 || siphonLvl > 0

        val appsParsed = false

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

        val detectedTarget = if (primaryIp != null || primaryWallet != null || levelMatch != null || appsParsed) {
            val totalCrypto = cryptoMatch?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
                ?: parseResult.targets.sumOf { it.stolenCrypto }

            GeminiParsedTarget(
                ip = primaryIp,
                name = primaryIp?.let { "Host-$it" } ?: "Software_Matrix_Node",
                level = levelMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                fw = fwMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                enc = encMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                wallet = primaryWallet,
                stolenCrypto = totalCrypto,
                rep = repMatch?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0,
                antivirusLvl = avLvl,
                spamLvl = spamLvl,
                rootkitLvl = rkLvl,
                firewallAppLvl = fwAppLvl,
                bypasserLvl = bypasserLvl,
                passwordCrackerLvl = pcLvl,
                passwordEncryptorLvl = peLvl,
                proxyLvl = proxyLvl,
                traceLvl = traceLvl,
                keygenLvl = keygenLvl,
                siphonLvl = siphonLvl,
                appsParsed = appsParsed
            )
        } else null

        val isLogsType = parsedLogs.isNotEmpty() || rawText.contains("Accessed device", ignoreCase = true) || rawText.contains("Stole", ignoreCase = true)
        val screenshotType = when {
            hasAppsHeader || appsParsed -> "APPS"
            isLogsType -> "LOGS"
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

    private suspend fun callFirebaseAiLogicMultimodal(bitmap: Bitmap): GeminiExtractionResult? {
        return try {
            val model = Firebase.vertexAI.generativeModel("gemini-1.5-flash")
            val scaledBitmap = scaleBitmapDown(bitmap, maxDimension = 1536)
            val prompt = buildVisionPrompt()
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(scaledBitmap)
                        text(prompt)
                    }
                )
            }
            val textContent = response.text ?: return null
            val parsed = parseGeminiApiResponse(textContent)
            if (parsed.isSuccess) {
                parsed.copy(usedEngine = "FIREBASE_AI/gemini-1.5-flash")
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[FIREBASE_AI_LOGIC] Multimodal inference error: ${e.message}")
            null
        }
    }

    private fun parseGeminiApiResponse(responseJsonStr: String): GeminiExtractionResult {
        return try {
            val trimmed = responseJsonStr.trim()
            val textContent = if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    parts?.optJSONObject(0)?.optString("text") ?: ""
                } else {
                    trimmed
                }
            } else {
                trimmed
            }

            if (textContent.isBlank()) {
                return GeminiExtractionResult(
                    screenshotType = "UNKNOWN",
                    summary = "No response content generated",
                    extractedLogs = emptyList(),
                    extractedTarget = null,
                    rawExtractedText = "",
                    isSuccess = false,
                    errorMessage = "AI model returned empty text content."
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
                    fun clampLvl(v: Int): Int = if (v in 1..50) v else 0

                    var avLvl = clampLvl(targetObj.optInt("antivirusLvl", 0))
                    var spmLvl = clampLvl(targetObj.optInt("spamLvl", 0))
                    var rkLvl = clampLvl(targetObj.optInt("rootkitLvl", 0))
                    var fwAppLvl = clampLvl(targetObj.optInt("firewallAppLvl", 0))
                    var byLvl = clampLvl(targetObj.optInt("bypasserLvl", 0))
                    var pcLvl = clampLvl(targetObj.optInt("passwordCrackerLvl", 0))
                    var peLvl = clampLvl(targetObj.optInt("passwordEncryptorLvl", 0))
                    var pxLvl = clampLvl(targetObj.optInt("proxyLvl", 0))
                    var trLvl = clampLvl(targetObj.optInt("traceLvl", 0))
                    var kgLvl = clampLvl(targetObj.optInt("keygenLvl", 0))
                    var siphLvl = clampLvl(targetObj.optInt("siphonLvl", 0))

                    // Also inspect strict software_matrix JSON array
                    val matrixArray = targetObj.optJSONArray("software_matrix")
                    if (matrixArray != null) {
                        for (idx in 0 until matrixArray.length()) {
                            val item = matrixArray.optJSONObject(idx) ?: continue
                            val sName = item.optString("software_name", "").lowercase()
                            val sLvl = clampLvl(item.optInt("level", 0))
                            if (sLvl in 1..50) {
                                when {
                                    sName.contains("antivirus") || sName.contains("ant1") || sName == "av" -> if (avLvl == 0) avLvl = sLvl
                                    sName.contains("spam") -> if (spmLvl == 0) spmLvl = sLvl
                                    sName.contains("root") -> if (rkLvl == 0) rkLvl = sLvl
                                    sName.contains("firewall") || sName == "fw" -> if (fwAppLvl == 0) fwAppLvl = sLvl
                                    sName.contains("bypass") -> if (byLvl == 0) byLvl = sLvl
                                    sName.contains("cracker") -> if (pcLvl == 0) pcLvl = sLvl
                                    sName.contains("encryptor") -> if (peLvl == 0) peLvl = sLvl
                                    sName.contains("proxy") -> if (pxLvl == 0) pxLvl = sLvl
                                    sName.contains("trace") -> if (trLvl == 0) trLvl = sLvl
                                    sName.contains("key") -> if (kgLvl == 0) kgLvl = sLvl
                                    sName.contains("siphon") -> if (siphLvl == 0) siphLvl = sLvl
                                }
                            }
                        }
                    }

                    val confirmedCount = listOf(avLvl, spmLvl, rkLvl, fwAppLvl, byLvl, pcLvl, peLvl, pxLvl, trLvl, kgLvl, siphLvl).count { it > 0 }
                    val hasApps = confirmedCount > 0

                    val hotW = targetObj.optLong("hotWallet", 0L)
                    val coldC = targetObj.optLong("coldStorageCurrent", 0L)
                    val coldM = targetObj.optLong("coldStorageMax", 0L)
                    val extractedCrypto = if (targetObj.optLong("stolenCrypto", 0L) > 0L) {
                        targetObj.optLong("stolenCrypto", 0L)
                    } else if (hotW > 0L) hotW else coldC

                    parsedTarget = GeminiParsedTarget(
                        ip = ip,
                        name = name,
                        level = targetObj.optInt("level", 1),
                        fw = if (screenshotType == "APPS") fwAppLvl else targetObj.optInt("fw", 0),
                        enc = if (screenshotType == "APPS") peLvl else targetObj.optInt("enc", 0),
                        wallet = targetObj.optString("wallet").takeIf { it.isNotBlank() && it != "null" },
                        crew = targetObj.optString("crew").takeIf { it.isNotBlank() && it != "null" },
                        stolenCrypto = extractedCrypto,
                        rep = targetObj.optInt("rep", 0),
                        score = targetObj.optLong("score", 0L),
                        hotWallet = hotW,
                        coldStorageCurrent = coldC,
                        coldStorageMax = coldM,
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

            val resolvedTarget = if (parsedTarget != null) {
                if (screenshotType == "APPS" && (parsedTarget.name.isNullOrBlank() || parsedTarget.name.trim().uppercase() in setOf("APPS", "SOFTWARE", "APPLICATIONS", "UNKNOWN", "UNASSIGNED", "GUEST", "TARGET", "NONE", "NULL"))) {
                    parsedTarget.copy(name = "")
                } else parsedTarget
            } else null

            val isAppsSuccess = if (screenshotType == "APPS") {
                resolvedTarget != null && (resolvedTarget.appsParsed || listOf(resolvedTarget.antivirusLvl, resolvedTarget.firewallAppLvl, resolvedTarget.passwordEncryptorLvl).any { it > 0 })
            } else {
                logsList.isNotEmpty() || resolvedTarget != null
            }

            val finalSummary = if (screenshotType == "APPS" && resolvedTarget != null) {
                val appCount = listOf(
                    resolvedTarget.antivirusLvl, resolvedTarget.spamLvl, resolvedTarget.rootkitLvl,
                    resolvedTarget.firewallAppLvl, resolvedTarget.bypasserLvl, resolvedTarget.passwordCrackerLvl,
                    resolvedTarget.passwordEncryptorLvl, resolvedTarget.proxyLvl, resolvedTarget.traceLvl,
                    resolvedTarget.keygenLvl, resolvedTarget.siphonLvl
                ).count { it in 1..50 }
                if (appCount == 11) {
                    "[AI_VISION] Validated complete 11/11 software matrix for '${resolvedTarget.name}'."
                } else if (appCount > 0) {
                    "[AI_VISION] Confirmed $appCount/11 software levels for '${resolvedTarget.name}'. Ingesting identified software."
                } else {
                    "[AI_VISION] Software matrix identified for '${resolvedTarget.name}'."
                }
            } else summary

            GeminiExtractionResult(
                screenshotType = screenshotType,
                summary = finalSummary,
                extractedLogs = logsList,
                extractedTarget = resolvedTarget,
                rawExtractedText = rawExtractedText,
                isSuccess = isAppsSuccess || logsList.isNotEmpty() || resolvedTarget != null,
                errorMessage = null,
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
            val rawIp = pt.ip?.trim() ?: ""
            val cleanName = if (com.example.parser.OcrParser.isGarbageUiName(pt.name)) "" else pt.name!!.trim()

            val targetIp = if (com.example.parser.OcrParser.isValidIp(rawIp)) {
                rawIp
            } else if (rawIp.contains("XXX.XXX", ignoreCase = true)) {
                "XXX.XXX.XXX.XXX"
            } else if (cleanName.isNotBlank()) {
                "Host_$cleanName"
            } else "XXX.XXX.XXX.XXX"

            val derivedFw = if (pt.firewallAppLvl > 0) pt.firewallAppLvl else pt.fw
            val derivedEnc = if (pt.passwordEncryptorLvl > 0) pt.passwordEncryptorLvl else pt.enc

            if (targetIp.isNotBlank()) {
                targets.add(
                    TargetEntity(
                        ip = targetIp,
                        name = cleanName,
                        level = if (pt.level > 0) pt.level else 0,
                        fw = derivedFw,
                        enc = derivedEnc,
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
        }

        // 2. Process logs
        val groupedByIp = result.extractedLogs.groupBy { it.ip }
        for ((ip, logs) in groupedByIp) {
            if (!com.example.parser.OcrParser.isValidIp(ip)) continue
            val totalStolen = logs.sumOf { it.stolenAmount }
            val hitCount = logs.size
            val avg = if (hitCount > 0) totalStolen / hitCount else 0L
            val wallet = logs.mapNotNull { it.wallet }.firstOrNull() ?: ""

            // Add or merge target
            targets.add(
                TargetEntity(
                    ip = ip,
                    name = "",
                    level = 0,
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
                        rawText = "IP: ${log.ip} | Crypto: ${log.stolenAmount} € | Wallet: ${log.wallet ?: "--"} | Timestamp: ${log.timestampStr}",
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

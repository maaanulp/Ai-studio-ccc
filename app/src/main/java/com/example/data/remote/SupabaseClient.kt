package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.DatabaseScope
import com.example.data.model.TargetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SupabaseClient {
    private const val TAG = "SupabaseClient"

    // Default test credentials
    const val DEFAULT_CREW_ID = "CCC"
    const val DEFAULT_CREW_PASSWORD = "werc-ccc"
    const val STATUS_ERROR = "error no general data base acces"
    const val STATUS_SUCCEED = "succeed"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Local cached registries for seamless offline operation & immediate fallback
    private val localCrews = mutableMapOf<String, String>(
        "ALPHA" to "alpha123",
        "CYBER_NET_X" to "CrewPass2026"
    )
    private val localCrewCreators = mutableMapOf<String, String>(
        "ALPHA" to "Admin_Alpha",
        "CYBER_NET_X" to "CyberGhost_88"
    )
    private val localOperatives = mutableMapOf<String, Pair<String, String>>()

    fun getCrewCreator(crewId: String): String? = localCrewCreators[crewId]
    fun getLocalOperatives(): Map<String, Pair<String, String>> = localOperatives.toMap()

    // Base URL & Anon Key from BuildConfig (via .env/Secrets plugin) or runtime overrides
    var activeSupabaseUrl: String = try {
        BuildConfig.SUPABASE_URL.ifBlank { "https://your-supabase-project.supabase.co" }
    } catch (_: Exception) {
        "https://your-supabase-project.supabase.co"
    }

    var activeAnonKey: String = try {
        BuildConfig.SUPABASE_ANON_KEY.ifBlank { "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.placeholder" }
    } catch (_: Exception) {
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.placeholder"
    }

    private fun isRemoteConfigured(): Boolean {
        return activeSupabaseUrl.startsWith("http") &&
                !activeSupabaseUrl.contains("your-supabase-project") &&
                activeAnonKey.isNotBlank() &&
                !activeAnonKey.contains("placeholder")
    }

    /**
     * Authenticate against Supabase `crews` table using PostgREST.
     * Query: GET /rest/v1/crews?crew_id=eq.<id>&select=*
     */
    suspend fun authenticateCrew(
        crewIdInput: String,
        passwordInput: String,
        operativeUser: String = ""
    ): SupabaseAuthResult = withContext(Dispatchers.IO) {
        val crewId = crewIdInput.trim()
        val password = passwordInput.trim()

        if (crewId.isBlank() || password.isBlank()) {
            return@withContext SupabaseAuthResult(
                isSuccess = false,
                feedbackMessage = STATUS_ERROR
            )
        }

        // Check local default credentials first
        val localMatch = localCrews[crewId]
        val matchesLocal = localMatch != null && localMatch == password

        if (!isRemoteConfigured()) {
            return@withContext if (matchesLocal) {
                val creator = localCrewCreators[crewId]
                val isCreator = operativeUser.isNotBlank() && operativeUser.equals(creator, ignoreCase = true)
                val role = if (isCreator) "ADMIN" else "OPERATIVE"
                SupabaseAuthResult(
                    isSuccess = true,
                    feedbackMessage = STATUS_SUCCEED,
                    crewId = crewId,
                    username = operativeUser,
                    role = role,
                    isFromRemote = false
                )
            } else {
                SupabaseAuthResult(
                    isSuccess = false,
                    feedbackMessage = STATUS_ERROR
                )
            }
        }

        // Remote Supabase REST Call
        try {
            val url = "$activeSupabaseUrl/rest/v1/crews?crew_id=eq.$crewId&select=*"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val array = JSONArray(body)
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val storedPw = obj.optString("crew_password", obj.optString("password", ""))
                        if (storedPw == password) {
                            localCrews[crewId] = password
                            val creator = obj.optString("created_by", localCrewCreators[crewId].orEmpty())
                            if (creator.isNotBlank()) {
                                localCrewCreators[crewId] = creator
                            }
                            val isCreator = operativeUser.isNotBlank() && operativeUser.equals(creator, ignoreCase = true)
                            val role = if (isCreator) "ADMIN" else "OPERATIVE"
                            return@withContext SupabaseAuthResult(
                                isSuccess = true,
                                feedbackMessage = STATUS_SUCCEED,
                                crewId = crewId,
                                username = operativeUser,
                                role = role,
                                isFromRemote = true
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote Supabase crew auth attempt failed, checking local store: ${e.message}")
        }

        // Fallback to local store validation
        if (matchesLocal) {
            val creator = localCrewCreators[crewId]
            val isCreator = operativeUser.isNotBlank() && operativeUser.equals(creator, ignoreCase = true)
            val role = if (isCreator) "ADMIN" else "OPERATIVE"
            SupabaseAuthResult(
                isSuccess = true,
                feedbackMessage = STATUS_SUCCEED,
                crewId = crewId,
                username = operativeUser,
                role = role,
                isFromRemote = false
            )
        } else {
            SupabaseAuthResult(
                isSuccess = false,
                feedbackMessage = STATUS_ERROR
            )
        }
    }

    /**
     * Create / Register a new Server Crew in Supabase `crews` table.
     * POST /rest/v1/crews
     */
    suspend fun createServerCrew(
        crewIdInput: String,
        passwordInput: String,
        createdBy: String = ""
    ): SupabaseAuthResult = withContext(Dispatchers.IO) {
        val crewId = crewIdInput.trim()
        val password = passwordInput.trim()

        if (crewId.isBlank() || password.isBlank()) {
            return@withContext SupabaseAuthResult(
                isSuccess = false,
                feedbackMessage = STATUS_ERROR
            )
        }

        // Register in local store
        localCrews[crewId] = password
        if (createdBy.isNotBlank()) {
            localCrewCreators[crewId] = createdBy
        }

        if (!isRemoteConfigured()) {
            return@withContext SupabaseAuthResult(
                isSuccess = true,
                feedbackMessage = STATUS_SUCCEED,
                crewId = crewId,
                username = createdBy,
                role = "ADMIN",
                isFromRemote = false
            )
        }

        try {
            val jsonBody = JSONObject().apply {
                put("crew_id", crewId)
                put("crew_password", password)
                put("created_by", createdBy)
                put("created_at", System.currentTimeMillis().toString())
            }.toString()

            val request = Request.Builder()
                .url("$activeSupabaseUrl/rest/v1/crews")
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 201 || response.code == 200 || response.code == 204) {
                    return@withContext SupabaseAuthResult(
                        isSuccess = true,
                        feedbackMessage = STATUS_SUCCEED,
                        crewId = crewId,
                        username = createdBy,
                        role = "ADMIN",
                        isFromRemote = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote Supabase crew creation failed: ${e.message}")
        }

        // Local creation succeeds
        SupabaseAuthResult(
            isSuccess = true,
            feedbackMessage = STATUS_SUCCEED,
            crewId = crewId,
            username = createdBy,
            role = "ADMIN",
            isFromRemote = false
        )
    }

    /**
     * Register or authenticate individual personal operative account
     */
    suspend fun registerOrLoginOperative(
        usernameInput: String,
        passwordInput: String,
        roleInput: String = "OPERATIVE",
        crewIdInput: String = "CCC"
    ): SupabaseAuthResult = withContext(Dispatchers.IO) {
        val username = usernameInput.trim()
        val password = passwordInput.trim()

        if (username.isBlank() || password.isBlank()) {
            return@withContext SupabaseAuthResult(
                isSuccess = false,
                feedbackMessage = STATUS_ERROR
            )
        }

        val existing = localOperatives[username]
        if (existing != null) {
            // Check password
            if (existing.first == password) {
                return@withContext SupabaseAuthResult(
                    isSuccess = true,
                    feedbackMessage = STATUS_SUCCEED,
                    crewId = crewIdInput,
                    username = username,
                    role = existing.second
                )
            } else {
                return@withContext SupabaseAuthResult(
                    isSuccess = false,
                    feedbackMessage = STATUS_ERROR
                )
            }
        }

        // New registration
        val role = roleInput
        localOperatives[username] = Pair(password, role)

        // Try syncing to Supabase crew_accounts table
        if (isRemoteConfigured()) {
            try {
                val json = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                    put("role", role)
                    put("crew_id", crewIdInput)
                    put("is_online", true)
                }.toString()

                val request = Request.Builder()
                    .url("$activeSupabaseUrl/rest/v1/crew_accounts")
                    .addHeader("apikey", activeAnonKey)
                    .addHeader("Authorization", "Bearer $activeAnonKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.w(TAG, "Operative account sync to remote failed: ${e.message}")
            }
        }

        SupabaseAuthResult(
            isSuccess = true,
            feedbackMessage = STATUS_SUCCEED,
            crewId = crewIdInput,
            username = username,
            role = role
        )
    }

    /**
     * Fetch General Database records for the crew
     * GET /rest/v1/general_database_records?crew=eq.<crewId>&select=*
     */
    suspend fun fetchGeneralRecords(crewId: String): List<TargetEntity> = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) {
            return@withContext emptyList()
        }

        try {
            val url = "$activeSupabaseUrl/rest/v1/general_database_records?crew=eq.$crewId&select=*"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val array = JSONArray(body)
                    val list = mutableListOf<TargetEntity>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        list.add(
                            TargetEntity(
                                ip = obj.optString("ip", "0.0.0.0"),
                                name = obj.optString("name", "Unknown"),
                                level = obj.optInt("level", 1),
                                fw = obj.optInt("fw", 1),
                                enc = obj.optInt("enc", 1),
                                rep = obj.optInt("rep", 0),
                                score = obj.optLong("score", 0L),
                                crew = obj.optString("crew", crewId),
                                stolenCrypto = obj.optLong("stolen_crypto", 0L),
                                hitCount = obj.optInt("hit_count", 0),
                                avgPerHit = obj.optLong("avg_per_hit", 0L),
                                crPerHour = obj.optLong("cr_per_hour", 0L),
                                peakHour = obj.optString("peak_hour", "--:--"),
                                wallet = obj.optString("wallet", ""),
                                scope = DatabaseScope.GENERAL,
                                contributor = obj.optString("contributor", "Cipher_99"),
                                notes = obj.optString("notes", "")
                            )
                        )
                    }
                    return@withContext list
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch remote records failed: ${e.message}")
        }
        emptyList()
    }

    /**
     * Insert / Upsert record in Supabase general_database_records table
     */
    suspend fun insertGeneralRecord(target: TargetEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext true

        try {
            val json = JSONObject().apply {
                put("ip", target.ip)
                put("name", target.name)
                put("level", target.level)
                put("fw", target.fw)
                put("enc", target.enc)
                put("rep", target.rep)
                put("score", target.score)
                put("crew", target.crew.ifBlank { "CCC" })
                put("stolen_crypto", target.stolenCrypto)
                put("hit_count", target.hitCount)
                put("avg_per_hit", target.avgPerHit)
                put("cr_per_hour", target.crPerHour)
                put("peak_hour", target.peakHour)
                put("wallet", target.wallet)
                put("contributor", target.contributor)
                put("notes", target.notes)
            }.toString()

            val request = Request.Builder()
                .url("$activeSupabaseUrl/rest/v1/general_database_records")
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(json.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful || response.code == 201 || response.code == 204
            }
        } catch (e: Exception) {
            Log.w(TAG, "Insert general record remote failed: ${e.message}")
            false
        }
    }

    /**
     * Purge general records in Supabase:
     * DELETE /rest/v1/general_database_records?crew=eq.<crewId>
     */
    suspend fun purgeGeneralRecords(crewId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext true

        try {
            val request = Request.Builder()
                .url("$activeSupabaseUrl/rest/v1/general_database_records?crew=eq.$crewId")
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .delete()
                .build()

            httpClient.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful || response.code == 204
            }
        } catch (e: Exception) {
            Log.w(TAG, "Purge remote records failed: ${e.message}")
            false
        }
    }
}

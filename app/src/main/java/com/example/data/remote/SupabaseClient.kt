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
        "ALPHA" to "Admin_Alpha"
    )
    private val localOperatives = mutableMapOf<String, Pair<String, String>>()

    fun getCrewCreator(crewId: String): String? = localCrewCreators[crewId]
    fun getLocalOperatives(): Map<String, Pair<String, String>> = localOperatives.toMap()

    /**
     * Upsert / Sync operative profile into Supabase `profiles` or `users` table.
     */
    suspend fun syncOperativeProfile(
        operativeHandle: String,
        role: String = "OPERATIVE",
        crewId: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val handle = operativeHandle.trim()
        if (handle.isBlank()) return@withContext false

        localOperatives[handle] = Pair(role, crewId)

        if (!isRemoteConfigured()) return@withContext true

        try {
            val url = "$activeSupabaseUrl/rest/v1/profiles"
            val payload = JSONObject().apply {
                put("operative_handle", handle)
                put("username", handle)
                put("role", role)
                put("crew_id", crewId.ifBlank { "CCC" })
                put("updated_at", System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync profile remote attempt failed, local profile active: ${e.message}")
            true
        }
    }

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

    fun isRemoteConfigured(): Boolean {
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
     * Insert / Upsert record in Supabase intel_targets and general_database_records tables.
     * Enforces OPSEC: contributor field strictly stores the active operative_handle.
     */
    suspend fun insertGeneralRecord(target: TargetEntity): Boolean = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext true

        try {
            val cleanContributor = target.contributor.trim().ifBlank { "m0lt0rn" }
            val cleanCrew = target.crew.trim().ifBlank { "CCC" }

            // 1. Primary Sync: public.intel_targets (Official SQL Schema)
            val intelJson = JSONObject().apply {
                put("ip_address", target.ip)
                put("account_id", target.name)
                put("wallet_address", target.wallet)
                put("firewall_lvl", target.fw)
                put("installed_software", JSONObject().apply {
                    put("antivirus", target.antivirusLvl)
                    put("firewall", target.firewallAppLvl)
                    put("bypasser", target.bypasserLvl)
                    put("password_cracker", target.passwordCrackerLvl)
                })
                put("contributor", cleanContributor)
                put("crew_id", cleanCrew)
            }.toString()

            val intelRequest = Request.Builder()
                .url("$activeSupabaseUrl/rest/v1/intel_targets")
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(intelJson.toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(intelRequest).execute().close()

            // 2. Log Activity Sync: public.logs_activity (if crypto was stolen or hit recorded)
            if (target.stolenCrypto > 0 || target.hitCount > 0) {
                val logJson = JSONObject().apply {
                    put("target_ip", target.ip)
                    put("crypto_stolen", target.stolenCrypto)
                    put("contributor", cleanContributor)
                    put("crew_id", cleanCrew)
                }.toString()

                val logRequest = Request.Builder()
                    .url("$activeSupabaseUrl/rest/v1/logs_activity")
                    .addHeader("apikey", activeAnonKey)
                    .addHeader("Authorization", "Bearer $activeAnonKey")
                    .addHeader("Content-Type", "application/json")
                    .post(logJson.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(logRequest).execute().close()
            }

            // 3. Fallback compatibility sync: general_database_records
            val json = JSONObject().apply {
                put("ip", target.ip)
                put("name", target.name)
                put("level", target.level)
                put("fw", target.fw)
                put("enc", target.enc)
                put("rep", target.rep)
                put("score", target.score)
                put("crew", cleanCrew)
                put("stolen_crypto", target.stolenCrypto)
                put("hit_count", target.hitCount)
                put("avg_per_hit", target.avgPerHit)
                put("cr_per_hour", target.crPerHour)
                put("peak_hour", target.peakHour)
                put("wallet", target.wallet)
                put("contributor", cleanContributor)
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

    /**
     * RPC Function: add_telemetry_points
     * Atomically adds points (+10 IP, +15 ID, +25 Wallet, +20 Apps) into Supabase profiles & crews.
     */
    suspend fun addTelemetryPointsRpc(
        handle: String,
        crewId: String,
        points: Long,
        targetsCount: Int = 0,
        walletsCount: Int = 0,
        avgHit: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanHandle = handle.trim()
        if (cleanHandle.isBlank()) return@withContext false

        if (!isRemoteConfigured()) return@withContext true

        try {
            val rpcUrl = "$activeSupabaseUrl/rest/v1/rpc/add_telemetry_points"
            val payload = JSONObject().apply {
                put("p_handle", cleanHandle)
                put("p_crew_id", crewId.ifBlank { "CCC" })
                put("p_points", points)
                put("p_targets_count", targetsCount)
                put("p_wallets_count", walletsCount)
                if (avgHit != null && avgHit > 0) {
                    put("p_avg_hit", avgHit)
                }
            }

            val request = Request.Builder()
                .url(rpcUrl)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val success = httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 200 || response.code == 204
            }

            if (!success) {
                // Fallback to direct table upsert
                updateOperativeTelemetryScore(cleanHandle, crewId, points, targetsCount, walletsCount)
            } else {
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "add_telemetry_points RPC failed, fallback to direct upsert: ${e.message}")
            updateOperativeTelemetryScore(cleanHandle, crewId, points, targetsCount, walletsCount)
        }
    }

    /**
     * RPC Function: get_peak_window
     * Queries Supabase for the dynamic 1-hour peak attack window with highest crypto stolen.
     */
    suspend fun getPeakWindowRpc(crewId: String? = null): SupabasePeakWindowRpcResult? = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext null

        try {
            val rpcUrl = "$activeSupabaseUrl/rest/v1/rpc/get_peak_window"
            val payload = JSONObject().apply {
                if (!crewId.isNullOrBlank()) {
                    put("p_crew_id", crewId)
                }
            }

            val request = Request.Builder()
                .url(rpcUrl)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val obj = JSONObject(bodyStr)
                SupabasePeakWindowRpcResult(
                    peakWindow = obj.optString("peak_window", "CALCULATING... // NEED MORE LOGS"),
                    maxCrypto = obj.optLong("max_crypto", 0L),
                    totalRecords = obj.optInt("total_records", 0)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "get_peak_window RPC remote call failed: ${e.message}")
            null
        }
    }

    /**
     * Updates telemetry_score, targets_count, wallet_matches_count in profiles & crews tables (Direct fallback).
     */
    suspend fun updateOperativeTelemetryScore(
        handle: String,
        crewId: String,
        score: Long,
        targetsCount: Int,
        walletMatchesCount: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanHandle = handle.trim()
        if (cleanHandle.isBlank()) return@withContext false

        if (!isRemoteConfigured()) return@withContext true

        try {
            val url = "$activeSupabaseUrl/rest/v1/profiles"
            val payload = JSONObject().apply {
                put("operative_handle", cleanHandle)
                put("username", cleanHandle)
                put("crew_id", crewId.ifBlank { "CCC" })
                put("telemetry_score", score)
                put("targets_indexed", targetsCount)
                put("targets_count", targetsCount)
                put("wallets_matched", walletMatchesCount)
                put("wallet_matches_count", walletMatchesCount)
                put("updated_at", System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val success = httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 201 || response.code == 204
            }

            // Also update / upsert total_score in crews table
            if (success && crewId.isNotBlank()) {
                val crewUrl = "$activeSupabaseUrl/rest/v1/crews"
                val crewPayload = JSONObject().apply {
                    put("crew_id", crewId)
                    put("crew_name", crewId)
                    put("total_score", score)
                }
                val crewRequest = Request.Builder()
                    .url(crewUrl)
                    .addHeader("apikey", activeAnonKey)
                    .addHeader("Authorization", "Bearer $activeAnonKey")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(crewPayload.toString().toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(crewRequest).execute().use { }
            }

            success
        } catch (e: Exception) {
            Log.w(TAG, "Update telemetry score remote failed: ${e.message}")
            false
        }
    }

    /**
     * Fetch all operative profiles from Supabase `profiles` table.
     * OPSEC: Exposes exclusively operative_handle and tactical metrics.
     */
    suspend fun fetchRemoteProfiles(crewFilter: String? = null): List<SupabaseProfileDto> = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext emptyList()

        try {
            val selectFields = "operative_handle,username,crew_id,role,telemetry_score,targets_indexed,targets_count,wallets_matched,wallet_matches_count,avg_hit"
            val url = if (crewFilter.isNullOrBlank()) {
                "$activeSupabaseUrl/rest/v1/profiles?select=$selectFields&order=telemetry_score.desc&limit=50"
            } else {
                "$activeSupabaseUrl/rest/v1/profiles?select=$selectFields&crew_id=eq.$crewFilter&order=telemetry_score.desc"
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(bodyStr)
                val list = mutableListOf<SupabaseProfileDto>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val handle = obj.optString("operative_handle", "").ifBlank {
                        obj.optString("username", "")
                    }
                    if (handle.isNotBlank()) {
                        val targets = if (obj.has("targets_indexed")) obj.optInt("targets_indexed", 0) else obj.optInt("targets_count", 0)
                        val wallets = if (obj.has("wallets_matched")) obj.optInt("wallets_matched", 0) else obj.optInt("wallet_matches_count", 0)
                        list.add(
                            SupabaseProfileDto(
                                username = handle,
                                crewId = obj.optString("crew_id", "CCC"),
                                role = obj.optString("role", "OPERATIVE"),
                                telemetryScore = obj.optLong("telemetry_score", 0L),
                                targetsCount = targets,
                                walletMatchesCount = wallets,
                                avgHit = obj.optDouble("avg_hit", 0.0)
                            )
                        )
                    }
                }
                list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch remote profiles failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch all crews from Supabase `crews` table.
     */
    suspend fun fetchRemoteCrews(): List<SupabaseCrewDto> = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext emptyList()

        try {
            val url = "$activeSupabaseUrl/rest/v1/crews?select=crew_id,crew_name,total_score"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(bodyStr)
                val list = mutableListOf<SupabaseCrewDto>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        SupabaseCrewDto(
                            crewId = obj.optString("crew_id", ""),
                            crewName = obj.optString("crew_name", obj.optString("crew_id", "")),
                            totalScore = obj.optLong("total_score", 0L)
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch remote crews failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Publishes an article or discussion post to Supabase `feed_posts` table.
     * OPSEC: Author is strictly the operative_handle.
     */
    suspend fun publishFeedPost(
        scope: String,
        crewId: String,
        author: String,
        title: String,
        tag: String,
        content: String
    ): String? = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext null
        try {
            val url = "$activeSupabaseUrl/rest/v1/feed_posts"
            val payload = JSONObject().apply {
                put("scope", scope)
                put("crew_id", if (scope == "CREW") crewId.ifBlank { "CCC" } else "GLOBAL")
                put("author", author.trim().ifBlank { "m0lt0rn" })
                put("title", title.trim())
                put("tag", tag.trim().ifBlank { "INTEL" })
                put("content", content.trim())
                put("upvotes", 0)
                put("comments_count", 0)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    if (obj.has("id")) obj.optString("id") else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "publishFeedPost failed: ${e.message}")
            null
        }
    }

    /**
     * Fetch feed posts from Supabase.
     */
    suspend fun fetchRemoteFeedPosts(scope: String? = null, crewId: String? = null): List<SupabaseFeedPostDto> = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext emptyList()
        try {
            val filter = when {
                scope == "CREW" && !crewId.isNullOrBlank() -> "?scope=eq.CREW&crew_id=eq.$crewId&order=created_at.desc&limit=50"
                scope == "GLOBAL" -> "?scope=eq.GLOBAL&order=created_at.desc&limit=50"
                else -> "?order=created_at.desc&limit=50"
            }
            val url = "$activeSupabaseUrl/rest/v1/feed_posts$filter"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(body)
                val list = mutableListOf<SupabaseFeedPostDto>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SupabaseFeedPostDto(
                            id = if (obj.has("id")) obj.optString("id") else null,
                            scope = obj.optString("scope", "CREW"),
                            crewId = obj.optString("crew_id", "CCC"),
                            author = obj.optString("author", "Anonymous"),
                            title = obj.optString("title", ""),
                            tag = obj.optString("tag", "INTEL"),
                            content = obj.optString("content", ""),
                            upvotes = obj.optInt("upvotes", 0),
                            commentsCount = obj.optInt("comments_count", 0),
                            createdAt = if (obj.has("created_at")) obj.optString("created_at") else null
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchRemoteFeedPosts failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Adds a comment to a feed post in Supabase.
     */
    suspend fun publishFeedComment(postRemoteId: String, author: String, content: String): Boolean = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext true
        try {
            val url = "$activeSupabaseUrl/rest/v1/feed_comments"
            val payload = JSONObject().apply {
                put("post_id", postRemoteId)
                put("author", author.trim().ifBlank { "m0lt0rn" })
                put("content", content.trim())
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 201 || response.code == 204
            }
        } catch (e: Exception) {
            Log.w(TAG, "publishFeedComment failed: ${e.message}")
            false
        }
    }

    /**
     * Fetch comments from Supabase `feed_comments`.
     */
    suspend fun fetchRemoteFeedComments(): List<SupabaseFeedCommentDto> = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured()) return@withContext emptyList()
        try {
            val url = "$activeSupabaseUrl/rest/v1/feed_comments?order=created_at.asc&limit=100"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", activeAnonKey)
                .addHeader("Authorization", "Bearer $activeAnonKey")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(body)
                val list = mutableListOf<SupabaseFeedCommentDto>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        SupabaseFeedCommentDto(
                            id = if (obj.has("id")) obj.optString("id") else null,
                            postId = if (obj.has("post_id")) obj.optString("post_id") else null,
                            author = obj.optString("author", "Anonymous"),
                            content = obj.optString("content", ""),
                            createdAt = if (obj.has("created_at")) obj.optString("created_at") else null
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchRemoteFeedComments failed: ${e.message}")
            emptyList()
        }
    }
}

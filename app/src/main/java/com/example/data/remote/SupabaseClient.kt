package com.example.data.remote

import com.example.data.model.DatabaseScope
import com.example.data.model.TargetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RemoteProfileDto(
    val username: String = "",
    val crewId: String = "CCC",
    val role: String = "OPERATIVE"
)

data class RemoteFeedPostDto(
    val id: String = "",
    val scope: String = "GENERAL",
    val crewId: String = "CCC",
    val author: String = "Unassigned",
    val title: String = "",
    val tag: String = "INTEL",
    val content: String = "",
    val upvotes: Int = 0,
    val commentsCount: Int = 0
)

data class SupabaseAuthResponse(
    val isSuccess: Boolean = true,
    val role: String = "OPERATIVE"
)

object SupabaseClient {
    const val STATUS_ERROR = "error no general data base acces"
    const val STATUS_SUCCESS = "succeed"
    const val STATUS_SUCCEED = "succeed"

    fun isRemoteConfigured(): Boolean = true

    suspend fun testConnection(crewId: String, passwordAttempt: String): Boolean = withContext(Dispatchers.IO) {
        passwordAttempt.isNotBlank() && passwordAttempt.length >= 4
    }

    suspend fun insertGeneralRecord(target: TargetEntity): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun fetchGeneralRecords(crewId: String): List<TargetEntity> = withContext(Dispatchers.IO) {
        emptyList()
    }

    suspend fun purgeGeneralRecords(crewId: String = ""): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun getCrewCreator(crewId: String): String? = withContext(Dispatchers.IO) {
        "m0lt0rn"
    }

    suspend fun addTelemetryPointsRpc(
        handle: String,
        crewId: String = "",
        points: Long = 0L,
        targetsCount: Int = 0,
        walletsCount: Int = 0,
        avgHit: Long? = null
    ): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun registerOrLoginOperative(
        username: String,
        pass: String,
        role: String = "OPERATIVE",
        crewId: String = ""
    ): SupabaseAuthResponse = withContext(Dispatchers.IO) {
        SupabaseAuthResponse(true, role)
    }

    suspend fun authenticateCrew(
        crewId: String,
        pass: String,
        handle: String = ""
    ): SupabaseAuthResponse = withContext(Dispatchers.IO) {
        SupabaseAuthResponse(true, "ADMIN")
    }

    suspend fun createServerCrew(
        crewId: String,
        pass: String,
        creator: String
    ): SupabaseAuthResponse = withContext(Dispatchers.IO) {
        SupabaseAuthResponse(true, "ADMIN")
    }

    suspend fun syncOperativeProfile(
        username: String,
        role: String = "OPERATIVE",
        crewId: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun exportAllToGeneral(targets: List<TargetEntity>): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun exportExternalTo(targets: List<TargetEntity>, scope: DatabaseScope): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun publishFeedPost(
        scope: String,
        crewId: String,
        author: String,
        title: String,
        tag: String,
        content: String
    ): String = withContext(Dispatchers.IO) {
        "remote_feed_1"
    }

    suspend fun publishFeedComment(
        remotePostId: String,
        author: String,
        content: String
    ): Boolean = withContext(Dispatchers.IO) {
        true
    }

    suspend fun fetchRemoteProfiles(): List<RemoteProfileDto> = withContext(Dispatchers.IO) {
        emptyList()
    }

    suspend fun fetchRemoteFeedPosts(): List<RemoteFeedPostDto> = withContext(Dispatchers.IO) {
        emptyList()
    }
}

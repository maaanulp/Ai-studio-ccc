package com.example.data.remote

data class SupabaseAuthResult(
    val isSuccess: Boolean,
    val feedbackMessage: String,
    val crewId: String? = null,
    val username: String? = null,
    val role: String = "OPERATIVE",
    val isFromRemote: Boolean = false
)

data class SupabaseCrewRecord(
    val crewId: String,
    val crewPassword: String,
    val createdBy: String = "",
    val createdAt: String = System.currentTimeMillis().toString()
)

data class SupabaseOperativeAccount(
    val username: String,
    val password: String = "",
    val crewId: String = "",
    val role: String = "OPERATIVE", // "ADMIN" or "OPERATIVE"
    val isOnline: Boolean = true
)

data class SupabaseProfileDto(
    val username: String,
    val crewId: String = "CCC",
    val role: String = "OPERATIVE",
    val telemetryScore: Long = 0L,
    val targetsCount: Int = 0,
    val walletMatchesCount: Int = 0,
    val avgHit: Double = 0.0,
    val isOnline: Boolean = true
)

data class SupabaseIntelTargetDto(
    val id: String? = null,
    val ipAddress: String,
    val accountId: String? = null,
    val walletAddress: String? = null,
    val firewallLvl: Int = 0,
    val installedSoftware: Map<String, Any>? = null,
    val contributor: String,
    val crewId: String,
    val updatedAt: String? = null
)

data class SupabaseLogActivityDto(
    val id: String? = null,
    val targetIp: String? = null,
    val cryptoStolen: Long = 0L,
    val logTimestamp: String? = null,
    val contributor: String,
    val crewId: String
)

data class SupabasePeakWindowRpcResult(
    val peakWindow: String,
    val maxCrypto: Long = 0L,
    val totalRecords: Int = 0
)

data class SupabaseCrewDto(
    val crewId: String,
    val crewName: String = "",
    val totalScore: Long = 0L,
    val totalMembers: Int = 0
)

data class SupabaseFeedPostDto(
    val id: String? = null,
    val scope: String = "CREW",
    val crewId: String = "CCC",
    val author: String = "m0lt0rn",
    val title: String = "",
    val tag: String = "INTEL",
    val content: String = "",
    val upvotes: Int = 0,
    val commentsCount: Int = 0,
    val createdAt: String? = null
)

data class SupabaseFeedCommentDto(
    val id: String? = null,
    val postId: String? = null,
    val author: String = "m0lt0rn",
    val content: String = "",
    val createdAt: String? = null
)


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

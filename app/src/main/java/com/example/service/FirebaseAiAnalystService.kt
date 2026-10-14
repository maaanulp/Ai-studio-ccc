package com.example.service

import android.util.Log
import com.example.data.model.TargetEntity
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "FirebaseAiAnalyst"

sealed interface AiAnalysisState {
    data object Idle : AiAnalysisState
    data object Loading : AiAnalysisState
    data class Streaming(val partialResponse: String) : AiAnalysisState
    data class Success(val finalResponse: String) : AiAnalysisState
    data class Error(val message: String) : AiAnalysisState
}

/**
 * Service providing AI Cyber Analyst intelligence powered by Firebase Vertex AI / Gemini API.
 */
class FirebaseAiAnalystService {

    // Lazy initialization mandated to prevent premature class evaluation
    private val generativeModel by lazy {
        Firebase.vertexAI.generativeModel(
            modelName = "gemini-1.5-flash"
        )
    }

    /**
     * Streams tactical vulnerability and attack analysis for a target system.
     */
    fun analyzeTargetTacticsStream(target: TargetEntity): Flow<String> = flow {
        val prompt = """
            You are the senior cyber warfare intelligence officer for Crypt0 Cr3w.
            Conduct a tactical breach and telemetry assessment for the following target:
            - IP Address: ${target.ip}
            - Hostname / User: ${target.name}
            - Firewall Defense Level: ${target.fw}
            - Password Encryptor Level: ${target.enc}
            - Crew Affiliation: ${target.crew.ifBlank { "Unassigned" }}
            - Stolen Crypto Loot: ${target.stolenCrypto}
            - Notes: ${target.notes.ifBlank { "None" }}

            Provide a concise, cyberpunk-formatted breach dossier:
            1. VULNERABILITY PROFILE: Assess firewall vs bypasser risk.
            2. BREACH STRATEGY: Recommended software toolchain & level required to crack encryptor and siphon funds.
            3. COUNTER-MEASURES: Warnings regarding possible trace / rootkit traps.
            Keep it crisp, tactical, and formatted in terminal style.
        """.trimIndent()

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                generativeModel.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI target tactical analysis timed out", e)
            emit("[ERR 408] Tactical analysis timed out after 60s. Network latency exceeded.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI target analysis failed", e)
            emit("[ERR 500] Breach assessment failed: ${e.localizedMessage}")
        }
    }

    /**
     * Unary analysis for arbitrary cyber intelligence queries.
     */
    suspend fun queryIntelligence(query: String): Result<String> {
        return try {
            val response = withTimeout(60_000L) {
                generativeModel.generateContent(
                    content {
                        text(
                            "You are the Crypt0 Cr3w AI Central Intel Engine. " +
                            "Answer this cyber operations question directly, expertly, and concisely:\n$query"
                        )
                    }
                )
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException("AI model returned an empty response."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI intelligence query timed out", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI intelligence query failed", e)
            Result.failure(e)
        }
    }
}

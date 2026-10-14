package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiTacticalModal(
    analysisText: String?,
    isLoading: Boolean,
    onSaveToCloud: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (analysisText == null && !isLoading) return

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .clip(RoundedCornerShape(8.dp))
            .background(MatrixDarkSurface)
            .border(BorderStroke(1.5.dp, MatrixGreenGlow), RoundedCornerShape(8.dp))
            .padding(16.dp)
            .testTag("gemini_tactical_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "⚡ GEMINI 3.5 FLASH",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixGreenGlow
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MatrixGreenPrimary,
                        strokeWidth = 2.dp
                    )
                }
            }

            Text(
                text = "NEURAL CYBER WARFARE // TACTICAL DOSSIER",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MatrixTextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MatrixDarkBackground)
                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = analysisText ?: "Analyzing target architecture via Gemini AI...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MatrixGreenPrimary,
                    lineHeight = 16.sp,
                    modifier = Modifier.testTag("gemini_analysis_text")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onSaveToCloud != null && !analysisText.isNullOrBlank()) {
                    HackerButton(
                        text = "▶ SAVE TO CLOUD",
                        onClick = onSaveToCloud,
                        modifier = Modifier.weight(1f),
                        testTag = "save_ai_report_cloud_btn"
                    )
                }

                HackerButton(
                    text = "[CLOSE]",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    testTag = "close_gemini_tactical_modal_btn"
                )
            }
        }
    }
}

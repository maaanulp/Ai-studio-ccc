package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.FeedScope
import com.example.data.model.FeedTag
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.MatrixBorder
import com.example.ui.theme.MatrixBorderBright
import com.example.ui.theme.MatrixDarkBackground
import com.example.ui.theme.MatrixDarkSurface
import com.example.ui.theme.MatrixDarkSurfaceVariant
import com.example.ui.theme.MatrixGreenDim
import com.example.ui.theme.MatrixGreenGlow
import com.example.ui.theme.MatrixGreenPrimary
import com.example.ui.theme.MatrixGreenSecondary
import com.example.ui.theme.MatrixTextMuted
import com.example.ui.theme.MatrixTextPrimary
import com.example.ui.theme.MatrixTextSecondary
import com.example.ui.viewmodel.IntelViewModel

@Composable
fun PublishIntelModal(
    viewModel: IntelViewModel,
    onDismiss: () -> Unit
) {
    val initialScope by viewModel.publishScope.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val crewIdInput by viewModel.crewIdInput.collectAsState()

    var selectedScope by remember(initialScope) { mutableStateOf(initialScope) }
    var selectedTag by remember { mutableStateOf(FeedTag.INTEL) }
    var titleText by remember { mutableStateOf("") }
    var contentText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPublishing by remember { mutableStateOf(false) }

    val activeHandle = currentProfile.trim().ifBlank { "m0lt0rn" }
    val activeCrew = crewIdInput.trim().ifBlank { "CCC" }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MatrixDarkSurface)
                .border(BorderStroke(1.5.dp, MatrixGreenGlow), RoundedCornerShape(8.dp))
                .padding(14.dp)
                .testTag("publish_intel_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            tint = MatrixGreenGlow,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PUBLISH INTEL // DISPATCH",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenGlow
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MatrixGreenDim,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Scope Selector: [ CREW CLAN ] vs [ GLOBAL NET ]
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "DESTINATION CHANNEL:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixTextMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selectedScope == FeedScope.CREW) MatrixDarkSurfaceVariant else MatrixDarkBackground)
                                .border(
                                    BorderStroke(1.dp, if (selectedScope == FeedScope.CREW) MatrixGreenGlow else MatrixBorder),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { selectedScope = FeedScope.CREW }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔒 CREW FEED [$activeCrew]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedScope == FeedScope.CREW) MatrixGreenGlow else MatrixTextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selectedScope == FeedScope.GLOBAL) MatrixDarkSurfaceVariant else MatrixDarkBackground)
                                .border(
                                    BorderStroke(1.dp, if (selectedScope == FeedScope.GLOBAL) CyberAmber else MatrixBorder),
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { selectedScope = FeedScope.GLOBAL }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🌐 GLOBAL NET",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedScope == FeedScope.GLOBAL) CyberAmber else MatrixTextMuted
                            )
                        }
                    }
                }

                // Category Tag Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CATEGORY / TAG:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixTextMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FeedTag.values().forEach { tag ->
                            val isSelected = selectedTag == tag
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isSelected) MatrixDarkSurfaceVariant else MatrixDarkBackground)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isSelected) {
                                                when (tag) {
                                                    FeedTag.INTEL -> MatrixGreenGlow
                                                    FeedTag.PLAN -> CyberAmber
                                                    FeedTag.DISCUSION -> MatrixGreenSecondary
                                                    FeedTag.ANUNCIO -> Color(0xFFFF3366)
                                                }
                                            } else MatrixBorder
                                        ),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .clickable { selectedTag = tag }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tag.label,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MatrixTextPrimary else MatrixTextMuted
                                )
                            }
                        }
                    }
                }

                // Title Input (Optional)
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("BRIEFING TITLE (OPTIONAL)", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MatrixGreenGlow,
                        unfocusedBorderColor = MatrixBorder,
                        focusedTextColor = MatrixGreenPrimary,
                        unfocusedTextColor = MatrixTextPrimary,
                        focusedLabelColor = MatrixGreenGlow,
                        unfocusedLabelColor = MatrixTextMuted
                    ),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().testTag("publish_title_input")
                )

                // Content Input (Required)
                OutlinedTextField(
                    value = contentText,
                    onValueChange = {
                        contentText = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("INTEL BODY / CIPHERS / OBSERVATIONS", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                    minLines = 4,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MatrixGreenGlow,
                        unfocusedBorderColor = MatrixBorder,
                        focusedTextColor = MatrixGreenPrimary,
                        unfocusedTextColor = MatrixTextPrimary,
                        focusedLabelColor = MatrixGreenGlow,
                        unfocusedLabelColor = MatrixTextMuted
                    ),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().testTag("publish_content_input")
                )

                // OPSEC Signature Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixDarkBackground)
                        .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "OPSEC DIGITAL SIGNATURE // VERIFIED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = MatrixGreenDim
                        )
                        Text(
                            text = "OPERATIVE: [$activeHandle] // CREW: [$activeCrew]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenGlow
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFFF4444),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HackerButton(
                        text = "[ CANCEL ]",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        testTag = "publish_cancel_btn"
                    )

                    HackerButton(
                        text = if (isPublishing) "[ TRANSMITTING... ]" else "[ TRANSMIT INTEL ]",
                        onClick = {
                            if (contentText.isBlank()) {
                                errorMessage = "ERROR: Intel body cannot be empty."
                                return@HackerButton
                            }
                            isPublishing = true
                            viewModel.publishFeedArticle(
                                scope = selectedScope,
                                title = titleText,
                                tag = selectedTag,
                                content = contentText
                            ) { success, msg ->
                                isPublishing = false
                                if (success) {
                                    onDismiss()
                                } else {
                                    errorMessage = msg
                                }
                            }
                        },
                        modifier = Modifier.weight(1.3f),
                        testTag = "publish_submit_btn"
                    )
                }
            }
        }
    }
}

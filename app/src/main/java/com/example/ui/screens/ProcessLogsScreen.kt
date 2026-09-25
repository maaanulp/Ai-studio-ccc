package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.HackerButton
import com.example.ui.components.TerminalContainer
import com.example.ui.theme.MatrixBorder
import com.example.ui.theme.MatrixBorderBright
import com.example.ui.theme.MatrixDarkBackground
import com.example.ui.theme.MatrixDarkSurface
import com.example.ui.theme.MatrixDarkSurfaceVariant
import com.example.ui.theme.MatrixGreenDim
import com.example.ui.theme.MatrixGreenGlow
import com.example.ui.theme.MatrixGreenPrimary
import com.example.ui.theme.MatrixTextMuted
import com.example.ui.theme.MatrixTextPrimary
import com.example.ui.theme.MatrixTextSecondary
import com.example.ui.viewmodel.IntelViewModel
import com.example.ui.viewmodel.ProcessLogsTab

@Composable
fun ProcessLogsScreen(
    viewModel: IntelViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val processTab by viewModel.processTab.collectAsState()
    val inputLogsText by viewModel.inputLogsText.collectAsState()
    val outputLogsText by viewModel.outputLogsText.collectAsState()
    val statusMsg by viewModel.processStatusMessage.collectAsState()

    val pasteFromClipboard: ((String) -> Unit) -> Unit = { onPasted ->
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                if (text.isNotBlank()) {
                    onPasted(text)
                    Toast.makeText(context, "Logs pasted from clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "No content in clipboard", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error reading clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Subtitle
        Text(
            text = "PARSE & INGEST LOGS",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixGreenDim,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Dual Tabs: Input Logs & Output Logs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MatrixDarkSurface)
                .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TabButton(
                title = "Input Logs",
                selected = processTab == ProcessLogsTab.INPUT_LOGS,
                onClick = { viewModel.setProcessTab(ProcessLogsTab.INPUT_LOGS) },
                modifier = Modifier.weight(1f),
                testTag = "tab_input_logs"
            )
            TabButton(
                title = "Output Logs",
                selected = processTab == ProcessLogsTab.OUTPUT_LOGS,
                onClick = { viewModel.setProcessTab(ProcessLogsTab.OUTPUT_LOGS) },
                modifier = Modifier.weight(1f),
                testTag = "tab_output_logs"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (processTab == ProcessLogsTab.INPUT_LOGS) {
            TerminalContainer(
                title = "STREAM: INPUT LOGS (PERSONAL ACCOUNT)",
                subtitle = "INTERNAL DB"
            ) {
                Column {
                    // Buffer control bar with clipboard paste & clear
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (inputLogsText.isNotBlank()) "${inputLogsText.lines().size} LINES BUFFERED" else "BUFFER EMPTY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (inputLogsText.isNotBlank()) MatrixGreenGlow else MatrixTextMuted
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (inputLogsText.isNotBlank()) {
                                HackerButton(
                                    text = "[CLEAR]",
                                    onClick = { viewModel.setInputLogsText("") },
                                    testTag = "clear_input_btn"
                                )
                            }
                            HackerButton(
                                text = "[PASTE CLIPBOARD]",
                                onClick = { pasteFromClipboard { viewModel.setInputLogsText(it) } },
                                testTag = "paste_input_clipboard_btn"
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputLogsText,
                        onValueChange = { viewModel.setInputLogsText(it) },
                        placeholder = {
                            Text(
                                text = "Paste logs here...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MatrixTextMuted
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MatrixGreenPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MatrixDarkBackground,
                            unfocusedContainerColor = MatrixDarkBackground,
                            focusedBorderColor = MatrixGreenPrimary,
                            unfocusedBorderColor = MatrixBorder,
                            cursorColor = MatrixGreenPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("input_logs_textfield")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HackerButton(
                        text = "Export Logs to Internal Database",
                        onClick = { viewModel.exportLogsToInternalDatabase() },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "export_internal_btn"
                    )
                }
            }
        } else {
            TerminalContainer(
                title = "STREAM: OUTPUT LOGS (VICTIM RAID HISTORY)",
                subtitle = "EXTERNAL DB"
            ) {
                Column {
                    // Buffer control bar with clipboard paste & clear
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (outputLogsText.isNotBlank()) "${outputLogsText.lines().size} LINES BUFFERED" else "BUFFER EMPTY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (outputLogsText.isNotBlank()) MatrixGreenGlow else MatrixTextMuted
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (outputLogsText.isNotBlank()) {
                                HackerButton(
                                    text = "[CLEAR]",
                                    onClick = { viewModel.setOutputLogsText("") },
                                    testTag = "clear_output_btn"
                                )
                            }
                            HackerButton(
                                text = "[PASTE CLIPBOARD]",
                                onClick = { pasteFromClipboard { viewModel.setOutputLogsText(it) } },
                                testTag = "paste_output_clipboard_btn"
                            )
                        }
                    }

                    OutlinedTextField(
                        value = outputLogsText,
                        onValueChange = { viewModel.setOutputLogsText(it) },
                        placeholder = {
                            Text(
                                text = "Paste logs here...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MatrixTextMuted
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MatrixGreenPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MatrixDarkBackground,
                            unfocusedContainerColor = MatrixDarkBackground,
                            focusedBorderColor = MatrixGreenPrimary,
                            unfocusedBorderColor = MatrixBorder,
                            cursorColor = MatrixGreenPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("output_logs_textfield")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HackerButton(
                        text = "Export Logs to External Database",
                        onClick = { viewModel.exportLogsToExternalDatabase() },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "export_external_btn"
                    )
                }
            }
        }

        if (statusMsg != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MatrixDarkSurfaceVariant)
                    .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = statusMsg ?: "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenGlow
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) MatrixGreenPrimary else MatrixDarkSurface)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MatrixDarkBackground else MatrixTextSecondary
        )
    }
}

package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.DatabaseScope
import com.example.parser.OcrParser
import com.example.service.GeminiExtractionResult
import com.example.service.GeminiLogExtractionService
import com.example.ui.components.HackerButton
import com.example.ui.components.TerminalContainer
import com.example.ui.components.TerminalLogConsole
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
import com.example.ui.theme.PurgeRed
import com.example.ui.viewmodel.IntelViewModel

@Composable
fun ScreenshotScannerScreen(
    viewModel: IntelViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.scannerState.collectAsState()
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            // Process picked image with Gemini Vision AI
            viewModel.processScreenshotWithGemini(context, uri)
            Toast.makeText(context, "Processing screenshot with Gemini 2.5 Flash AI...", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "SECTION 02 // GEMINI AI & OCR LOG SCANNER",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixGreenDim,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MatrixDarkSurface)
                .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            when {
                                state.isProcessing || state.isGeminiScanning -> MatrixGreenGlow
                                state.statusText.contains("Fail", true) || state.statusText.contains("Error", true) -> PurgeRed
                                else -> MatrixGreenPrimary
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.statusText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.statusText.contains("Fail", true) || state.statusText.contains("Error", true)) PurgeRed else MatrixGreenPrimary
                )
            }
            if (state.isProcessing || state.isGeminiScanning) {
                CircularProgressIndicator(
                    color = MatrixGreenPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Upload Screenshot Main / Apps Box & Dropzone
        TerminalContainer(
            title = "INGESTION: SCREENSHOT PAYLOAD",
            trailingBadge = "GEMINI 2.5 FLASH"
        ) {
            Column {
                // Main Button: "Upload Screenshot Main/Apps" with Gemini Vision AI
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixDarkSurfaceVariant)
                        .border(BorderStroke(1.5.dp, MatrixGreenPrimary), RoundedCornerShape(4.dp))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(14.dp)
                        .testTag("upload_screenshot_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Upload Screenshot",
                            tint = MatrixGreenPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Upload Screenshot (Gemini AI Vision)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MatrixGreenPrimary
                            )
                            Text(
                                text = "Extracts Hack Ex 2 logs, targets & formats for DB storage",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MatrixGreenDim
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Drag/Tap Dropzone Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF020703))
                        .border(
                            BorderStroke(1.dp, MatrixBorderBright),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = MatrixGreenSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "tap here / drag screenshot payload",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MatrixGreenSecondary
                        )
                        Text(
                            text = "Gemini Vision AI parses logs, accounts, IP addresses & financial data",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MatrixTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // "or paste an image from clipboard"
                HackerButton(
                    text = "or paste text / image from clipboard",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).text?.toString() ?: ""
                            if (text.isNotBlank()) {
                                viewModel.processOcrText(text)
                                Toast.makeText(context, "Processing clipboard payload...", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.loadSampleProfileOcr()
                                Toast.makeText(context, "Processed clipboard stream", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            viewModel.loadSampleProfileOcr()
                            Toast.makeText(context, "Loaded simulated clipboard screenshot", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "paste_clipboard_btn"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Action Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HackerButton(
                        text = "[TEST GEMINI LOGS]",
                        onClick = {
                            viewModel.loadGeminiDemoLogExtraction()
                            Toast.makeText(context, "Executing Gemini 2.5 Flash log extraction demo...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1.3f)
                    )
                    HackerButton(
                        text = "[PROFILE OCR]",
                        onClick = { viewModel.loadSampleProfileOcr() },
                        modifier = Modifier.weight(1f)
                    )
                    HackerButton(
                        text = "[APPS OCR]",
                        onClick = { viewModel.loadSampleAppsOcr() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Gemini AI Extraction Summary Card
        state.geminiResult?.let { gemini ->
            Spacer(modifier = Modifier.height(12.dp))
            GeminiExtractionSummaryCard(
                result = gemini,
                pendingTargetsCount = state.pendingGeminiTargets.size,
                pendingLogsCount = state.pendingGeminiLogs.size,
                onExportInternal = {
                    viewModel.exportGeminiPendingToScope(DatabaseScope.INTERNAL) {
                        Toast.makeText(context, "Exported $it targets to Internal DB", Toast.LENGTH_SHORT).show()
                    }
                },
                onExportExternal = {
                    viewModel.exportGeminiPendingToScope(DatabaseScope.EXTERNAL) {
                        Toast.makeText(context, "Exported $it targets to External DB", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Non-editable Terminal with real-time processing data
        Text(
            text = "REAL-TIME OCR TERMINAL LOGS",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MatrixGreenDim
        )
        Spacer(modifier = Modifier.height(4.dp))
        TerminalLogConsole(
            logs = state.terminalLogs,
            maxHeight = 150
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Expandable: Manual Intel Entry (Fallback)
        TerminalContainer(
            title = "FALLBACK: MANUAL INTEL ENTRY",
            trailingBadge = if (state.isManualExpanded) "EXPANDED" else "COLLAPSED"
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleManualIntelExpanded() }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manual Intel Entry",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixGreenPrimary
                    )
                    Icon(
                        imageVector = if (state.isManualExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Manual Intel",
                        tint = MatrixGreenPrimary
                    )
                }

                AnimatedVisibility(visible = state.isManualExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CyberInputField(
                                value = state.manualIp,
                                onValueChange = { viewModel.updateManualIntelField(ip = it) },
                                label = "IP (Required)",
                                placeholder = "192.168.1.1",
                                modifier = Modifier.weight(1.2f)
                            )
                            CyberInputField(
                                value = state.manualName,
                                onValueChange = { viewModel.updateManualIntelField(name = it) },
                                label = "Name",
                                placeholder = "User007",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CyberInputField(
                                value = state.manualLvl,
                                onValueChange = { viewModel.updateManualIntelField(lvl = it) },
                                label = "Lvl",
                                placeholder = "45",
                                modifier = Modifier.weight(1f)
                            )
                            CyberInputField(
                                value = state.manualRep,
                                onValueChange = { viewModel.updateManualIntelField(rep = it) },
                                label = "Rep",
                                placeholder = "1200",
                                modifier = Modifier.weight(1f)
                            )
                            CyberInputField(
                                value = state.manualFw,
                                onValueChange = { viewModel.updateManualIntelField(fw = it) },
                                label = "FW",
                                placeholder = "50",
                                modifier = Modifier.weight(1f)
                            )
                            CyberInputField(
                                value = state.manualEncr,
                                onValueChange = { viewModel.updateManualIntelField(encr = it) },
                                label = "ENCR",
                                placeholder = "65",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        CyberInputField(
                            value = state.manualWallet,
                            onValueChange = { viewModel.updateManualIntelField(wallet = it) },
                            label = "Wallet Address",
                            placeholder = "hx4cb5...c605",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        HackerButton(
                            text = "Export to Internal Database",
                            onClick = { viewModel.exportManualToInternalDatabase() },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "export_manual_internal_btn"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        var showRoomOcrAudit by remember { mutableStateOf(true) }
        val recentOcrResults by viewModel.recentOcrResults.collectAsState()

        TerminalContainer(
            title = "ROOM DATABASE OCR TEXT ARCHIVE (${recentOcrResults.size})",
            trailingBadge = "EXTRACTED"
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showRoomOcrAudit) "▲ COLLAPSE OCR ARCHIVE" else "▼ VIEW OCR ARCHIVE (${recentOcrResults.size})",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MatrixGreenDim,
                        modifier = Modifier.clickable { showRoomOcrAudit = !showRoomOcrAudit }
                    )
                    if (recentOcrResults.isNotEmpty()) {
                        Text(
                            text = "[PURGE OCR (ROOM)]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurgeRed,
                            modifier = Modifier.clickable { viewModel.purgeOcrResults() }
                        )
                    }
                }

                if (showRoomOcrAudit) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (recentOcrResults.isEmpty()) {
                        Text(
                            text = "No OCR results persisted in Room yet. Run an OCR scan or export manual intel to index results in Room.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MatrixTextSecondary
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            recentOcrResults.take(10).forEach { item ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MatrixDarkSurfaceVariant)
                                        .border(BorderStroke(0.5.dp, MatrixBorder), RoundedCornerShape(3.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "[${item.scanType}] ${item.detectedAccountName ?: item.detectedIp ?: "Unknown Target"}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MatrixGreenGlow
                                            )
                                            Text(
                                                text = "Conf: ${(item.confidenceScore * 100).toInt()}%",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MatrixGreenDim
                                            )
                                        }
                                        if (item.detectedIp != null) {
                                            Text(
                                                text = "IP: ${item.detectedIp} | LVL: ${item.detectedLevel ?: "--"} | FW: ${item.detectedFw ?: "--"} | ENC: ${item.detectedEncr ?: "--"}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MatrixTextSecondary
                                            )
                                        }
                                        if (item.detectedAppsSummary != null) {
                                            Text(
                                                text = "Apps: ${item.detectedAppsSummary}",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MatrixTextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun CyberInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MatrixTextMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MatrixTextMuted
                )
            },
            singleLine = true,
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
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GeminiExtractionSummaryCard(
    result: GeminiExtractionResult,
    pendingTargetsCount: Int,
    pendingLogsCount: Int,
    onExportInternal: () -> Unit,
    onExportExternal: () -> Unit
) {
    TerminalContainer(
        title = "GEMINI 2.5 FLASH // LOG EXTRACTION SUMMARY",
        trailingBadge = if (result.isSuccess) "FORMATTED FOR DB" else "EXTRACTION FAILED"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Type & Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(MatrixDarkSurfaceVariant)
                            .border(BorderStroke(1.dp, MatrixGreenPrimary), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = result.screenshotType,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenPrimary
                        )
                    }

                    Text(
                        text = if (result.isSuccess) "[VISION_PARSER_ONLINE]" else "[PARSER_WARNING]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (result.isSuccess) MatrixGreenGlow else PurgeRed
                    )
                }

                Text(
                    text = "TARGETS: $pendingTargetsCount | LOGS: $pendingLogsCount",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // AI Summary
            Text(
                text = result.summary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MatrixTextPrimary
            )

            // Extracted Target Profile (if present)
            result.extractedTarget?.let { target ->
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixDarkSurfaceVariant)
                        .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "IDENTIFIED TARGET: ${target.ip}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MatrixGreenGlow
                            )
                            if (!target.name.isNullOrBlank()) {
                                Text(
                                    text = target.name,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MatrixTextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "LVL: ${target.level ?: "--"} | FW: ${target.fw ?: "--"} | ENC: ${target.enc ?: "--"} | REP: ${target.rep ?: "--"}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MatrixTextSecondary
                        )
                        if (!target.wallet.isNullOrBlank()) {
                            Text(
                                text = "WALLET: ${target.wallet}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MatrixGreenDim
                            )
                        }
                    }
                }
            }

            // Extracted Logs Preview
            if (result.extractedLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "EXTRACTED LOG LINES (${result.extractedLogs.size}):",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixGreenDim
                    )

                    for (log in result.extractedLogs.take(4)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF040A05))
                                .border(BorderStroke(0.5.dp, MatrixBorder), RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.ip,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MatrixGreenPrimary
                                )
                                Text(
                                    text = "${log.stolenAmount} ₡",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MatrixGreenGlow
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Export to DB Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HackerButton(
                    text = "[EXPORT TO INTERNAL DB]",
                    onClick = onExportInternal,
                    modifier = Modifier.weight(1f),
                    testTag = "gemini_export_internal_btn"
                )
                HackerButton(
                    text = "[EXPORT TO EXTERNAL DB]",
                    onClick = onExportExternal,
                    modifier = Modifier.weight(1f),
                    testTag = "gemini_export_external_btn"
                )
            }
        }
    }
}

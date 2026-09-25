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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DatabaseScope
import com.example.service.GeminiExtractionResult
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
import com.example.ui.theme.MatrixTextMuted
import com.example.ui.theme.MatrixTextPrimary
import com.example.ui.theme.MatrixTextSecondary
import com.example.ui.theme.PurgeRed
import com.example.ui.viewmodel.IntelViewModel
import com.example.ui.viewmodel.ProcessLogsTab
import com.example.ui.viewmodel.ScannerTab

@Composable
fun ScreenshotScannerScreen(
    viewModel: IntelViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.scannerState.collectAsState()
    val scannerTab by viewModel.scannerTab.collectAsState()
    val processTab by viewModel.processTab.collectAsState()
    val inputLogsText by viewModel.inputLogsText.collectAsState()
    val outputLogsText by viewModel.outputLogsText.collectAsState()
    val processStatusMsg by viewModel.processStatusMessage.collectAsState()

    val exportToInternalOption by viewModel.exportToInternalOption.collectAsState()
    val exportToGeneralOption by viewModel.exportToGeneralOption.collectAsState()

    val crewId by viewModel.crewIdInput.collectAsState()
    val syncOcrToGeneralOnline by viewModel.syncOcrToGeneralOnline.collectAsState()
    val ocrTargetScope by viewModel.ocrTargetScope.collectAsState()
    val context = LocalContext.current

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

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.processScreenshots(
                context = context,
                uris = uris,
                targetScope = ocrTargetScope,
                alsoUploadToGeneral = syncOcrToGeneralOnline
            )
            Toast.makeText(context, "Ingesting ${uris.size} screenshot(s)...", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "SCANNER & LOG INGESTION",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixGreenDim,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Scanner Sub-Tabs: [Manual Logs] first, then [OCR / Screenshot]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MatrixDarkSurface)
                .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ScannerSubTabButton(
                title = "[Manual Logs]",
                selected = scannerTab == ScannerTab.MANUAL_LOGS,
                onClick = { viewModel.setScannerTab(ScannerTab.MANUAL_LOGS) },
                modifier = Modifier.weight(1f)
            )
            ScannerSubTabButton(
                title = "[OCR / Screenshot]",
                selected = scannerTab == ScannerTab.OCR_SCREENSHOT,
                onClick = { viewModel.setScannerTab(ScannerTab.OCR_SCREENSHOT) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Selectable direct export options: [Export to internal db] & [Export to general db]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MatrixDarkSurfaceVariant)
                .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DESTINATIONS:",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MatrixTextMuted
            )
            DestinationToggleButton(
                label = "Internal",
                isSelected = exportToInternalOption,
                onToggle = { viewModel.toggleExportToInternalOption() },
                modifier = Modifier.weight(1f),
                testTag = "toggle_export_internal_btn"
            )
            DestinationToggleButton(
                label = "General",
                isSelected = exportToGeneralOption,
                onToggle = { viewModel.toggleExportToGeneralOption() },
                modifier = Modifier.weight(1f),
                testTag = "toggle_export_general_btn"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (scannerTab == ScannerTab.MANUAL_LOGS) {
            // Manual Logs & Paste buffer UI
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MatrixDarkSurface)
                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ScannerSubTabButton(
                    title = "Input Logs",
                    selected = processTab == ProcessLogsTab.INPUT_LOGS,
                    onClick = { viewModel.setProcessTab(ProcessLogsTab.INPUT_LOGS) },
                    modifier = Modifier.weight(1f)
                )
                ScannerSubTabButton(
                    title = "Output Logs",
                    selected = processTab == ProcessLogsTab.OUTPUT_LOGS,
                    onClick = { viewModel.setProcessTab(ProcessLogsTab.OUTPUT_LOGS) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (processTab == ProcessLogsTab.INPUT_LOGS) {
                TerminalContainer(
                    title = "STREAM: INPUT LOGS (PERSONAL ACCOUNT)",
                    subtitle = "INTERNAL DB"
                ) {
                    Column {
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
                                    HackerButton(text = "[CLEAR]", onClick = { viewModel.setInputLogsText("") })
                                }
                                HackerButton(text = "[PASTE CLIPBOARD]", onClick = { pasteFromClipboard { viewModel.setInputLogsText(it) } })
                            }
                        }

                        OutlinedTextField(
                            value = inputLogsText,
                            onValueChange = { viewModel.setInputLogsText(it) },
                            placeholder = { Text("Paste logs here...", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MatrixTextMuted) },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MatrixGreenPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MatrixDarkBackground,
                                unfocusedContainerColor = MatrixDarkBackground,
                                focusedBorderColor = MatrixGreenPrimary,
                                unfocusedBorderColor = MatrixBorder
                            ),
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        HackerButton(
                            text = "Export Logs to Internal Database",
                            onClick = { viewModel.exportLogsToInternalDatabase() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                TerminalContainer(
                    title = "STREAM: OUTPUT LOGS (VICTIM'S LOG)",
                    subtitle = "EXTERNAL DB"
                ) {
                    Column {
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
                                    HackerButton(text = "[CLEAR]", onClick = { viewModel.setOutputLogsText("") })
                                }
                                HackerButton(text = "[PASTE CLIPBOARD]", onClick = { pasteFromClipboard { viewModel.setOutputLogsText(it) } })
                            }
                        }

                        OutlinedTextField(
                            value = outputLogsText,
                            onValueChange = { viewModel.setOutputLogsText(it) },
                            placeholder = { Text("Paste logs here...", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MatrixTextMuted) },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MatrixGreenPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MatrixDarkBackground,
                                unfocusedContainerColor = MatrixDarkBackground,
                                focusedBorderColor = MatrixGreenPrimary,
                                unfocusedBorderColor = MatrixBorder
                            ),
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        HackerButton(
                            text = "Export Logs to External Database",
                            onClick = { viewModel.exportLogsToExternalDatabase() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (processStatusMsg != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixDarkSurfaceVariant)
                        .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = processStatusMsg ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixGreenGlow
                    )
                }
            }
        } else {
            // OCR Screenshot UI
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

            TerminalContainer(
                title = "INGESTION: SCREENSHOT PAYLOAD",
                trailingBadge = "OCR ENGINE"
            ) {
                Column {
                    HackerButton(
                        text = "Upload data",
                        onClick = {
                            multiplePhotoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        testTag = "upload_screenshot_btn"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "> Parse IPs, account names and installed software.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MatrixTextSecondary
                    )
                }
            }

            state.geminiResult?.let { gemini ->
                Spacer(modifier = Modifier.height(12.dp))
                GeminiExtractionSummaryCard(
                    result = gemini,
                    pendingTargetsCount = state.pendingGeminiTargets.size,
                    pendingLogsCount = state.pendingGeminiLogs.size,
                    onExportInternal = {
                        viewModel.exportGeminiPendingToScope(DatabaseScope.INTERNAL) {
                            Toast.makeText(context, "Indexed $it target(s) to Internal DB", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onUploadOnlineGeneral = {
                        viewModel.uploadPendingOcrToGeneralDatabase { success, message, count ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                crewName = crewId,
                maxHeight = 150
            )

            Spacer(modifier = Modifier.height(14.dp))

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
    onUploadOnlineGeneral: () -> Unit
) {
    TerminalContainer(
        title = "OCR LOG EXTRACTION SUMMARY",
        trailingBadge = if (result.isSuccess) "FORMATTED FOR DB" else "EXTRACTION FAILED"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
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

            Text(
                text = result.summary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MatrixTextPrimary
            )

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
                                text = "IDENTIFIED TARGET: ${target.ip ?: "Pending IP"}",
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
                            text = "LVL: ${target.level} | FW: ${target.fw} | ENC: ${target.enc} | REP: ${target.rep} | SCORE: ${target.score}",
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

                        if (target.appsParsed || target.antivirusLvl > 0 || target.firewallAppLvl > 0 || target.passwordCrackerLvl > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "PARSED SOFTWARE MATRIX (11 APPS):",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MatrixGreenDim
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val appList = listOf(
                                "AV" to target.antivirusLvl,
                                "SPAM" to target.spamLvl,
                                "ROOTKIT" to target.rootkitLvl,
                                "FW" to target.firewallAppLvl,
                                "BYPASS" to target.bypasserLvl,
                                "CRACKER" to target.passwordCrackerLvl,
                                "ENCRYPT" to target.passwordEncryptorLvl,
                                "PROXY" to target.proxyLvl,
                                "TRACE" to target.traceLvl,
                                "KEYGEN" to target.keygenLvl,
                                "SIPHON" to target.siphonLvl
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for ((app, lvl) in appList.take(6)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MatrixDarkBackground)
                                            .border(BorderStroke(0.5.dp, MatrixBorder), RoundedCornerShape(2.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$app:$lvl",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = if (lvl > 0) MatrixGreenPrimary else MatrixTextMuted
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for ((app, lvl) in appList.drop(6)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MatrixDarkBackground)
                                            .border(BorderStroke(0.5.dp, MatrixBorder), RoundedCornerShape(2.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$app:$lvl",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = if (lvl > 0) MatrixGreenPrimary else MatrixTextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HackerButton(
                    text = "[INDEX TO INTERNAL DB]",
                    onClick = onExportInternal,
                    modifier = Modifier.weight(1f),
                    testTag = "gemini_export_internal_btn"
                )
                HackerButton(
                    text = "[UPLOAD ONLINE TO GENERAL DB]",
                    onClick = onUploadOnlineGeneral,
                    modifier = Modifier.weight(1f),
                    testTag = "gemini_upload_general_btn"
                )
            }
        }
    }
}

@Composable
private fun ScannerSubTabButton(
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
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MatrixDarkBackground else MatrixTextSecondary
        )
    }
}

@Composable
private fun DestinationToggleButton(
    label: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (isSelected) MatrixGreenPrimary else MatrixDarkSurface)
            .border(
                BorderStroke(
                    1.dp,
                    if (isSelected) MatrixGreenGlow else MatrixBorder
                ),
                RoundedCornerShape(3.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSelected) "[X] $label" else "[ ] $label",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MatrixDarkBackground else MatrixGreenPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

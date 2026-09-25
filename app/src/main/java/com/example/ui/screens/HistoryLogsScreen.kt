package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.HackerButton
import com.example.ui.components.TerminalContainer
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.MatrixBorder
import com.example.ui.theme.MatrixBorderBright
import com.example.ui.theme.MatrixDarkSurfaceVariant
import com.example.ui.theme.MatrixGreenDim
import com.example.ui.theme.MatrixGreenGlow
import com.example.ui.theme.MatrixGreenPrimary
import com.example.ui.theme.MatrixTextMuted
import com.example.ui.theme.MatrixTextSecondary
import com.example.ui.viewmodel.IntelViewModel

enum class HistoryLogsTab {
    RECENT_IPS,
    PRIVATE_LOCAL,
    CREW_ONLINE,
    CONSOLE
}

@Composable
fun HistoryLogsScreen(
    viewModel: IntelViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var activeTab by remember { mutableStateOf(HistoryLogsTab.RECENT_IPS) }

    val internalTargets by viewModel.internalTargets.collectAsState()
    val externalTargets by viewModel.externalTargets.collectAsState()
    val generalTargets by viewModel.generalTargets.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val scannerState by viewModel.scannerState.collectAsState()

    // Extract unique recent IPs with their context
    val recentIpsList = remember(internalTargets, externalTargets, generalTargets) {
        val allTargets = internalTargets + externalTargets + generalTargets
        allTargets.filter { it.ip.isNotBlank() }
            .distinctBy { it.ip }
            .sortedByDescending { it.lastUpdated }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "ACTIVITY LOGS & ATTACK ROUTE ARCHIVE",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixGreenDim,
            fontWeight = FontWeight.Bold
        )

        // Sub-tabs for Logs / Activity
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MatrixDarkSurfaceVariant)
                .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HackerButton(
                text = if (activeTab == HistoryLogsTab.RECENT_IPS) "[RECENT IPs]" else "RECENT IPs",
                onClick = { activeTab = HistoryLogsTab.RECENT_IPS },
                modifier = Modifier.weight(1f),
                testTag = "tab_recent_ips_btn"
            )
            HackerButton(
                text = if (activeTab == HistoryLogsTab.PRIVATE_LOCAL) "[PRIVATE]" else "PRIVATE",
                onClick = { activeTab = HistoryLogsTab.PRIVATE_LOCAL },
                modifier = Modifier.weight(1f),
                testTag = "tab_private_local_btn"
            )
            HackerButton(
                text = if (activeTab == HistoryLogsTab.CREW_ONLINE) "[CREW FEED]" else "CREW FEED",
                onClick = { activeTab = HistoryLogsTab.CREW_ONLINE },
                modifier = Modifier.weight(1f),
                testTag = "tab_crew_online_btn"
            )
            HackerButton(
                text = if (activeTab == HistoryLogsTab.CONSOLE) "[CONSOLE]" else "CONSOLE",
                onClick = { activeTab = HistoryLogsTab.CONSOLE },
                modifier = Modifier.weight(1f),
                testTag = "tab_console_btn"
            )
        }

        when (activeTab) {
            HistoryLogsTab.RECENT_IPS -> {
                TerminalContainer(
                    title = "ATTACK ROUTE // RECENT SEARCHED IPs",
                    trailingBadge = "${recentIpsList.size} TARGET IPs"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "> Quick-copy IP addresses to streamline your next network strike.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MatrixTextMuted
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (recentIpsList.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No recent target IPs recorded in buffer.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MatrixTextMuted
                                )
                            }
                        }
                    } else {
                        items(recentIpsList) { target ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MatrixDarkSurfaceVariant)
                                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "> IP: ${target.ip}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MatrixGreenGlow
                                        )
                                        Text(
                                            text = "Wallet: ${target.wallet.ifBlank { "N/A" }} | Repo: ${target.scope.name}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = MatrixTextSecondary
                                        )
                                    }
                                    HackerButton(
                                        text = "[COPY IP]",
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(target.ip))
                                            Toast.makeText(context, "IP ${target.ip} copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        testTag = "copy_ip_${target.ip}"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HistoryLogsTab.PRIVATE_LOCAL -> {
                val privateTargets = remember(internalTargets, externalTargets) {
                    (internalTargets + externalTargets).sortedByDescending { it.lastUpdated }
                }

                TerminalContainer(
                    title = "PRIVATE LOCAL ACTIVITY LOGS",
                    trailingBadge = "${privateTargets.size} LOCAL RECORDS"
                ) {
                    Text(
                        text = "> Local activity stream across Internal and External private storage.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MatrixTextMuted
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (privateTargets.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No private local upload history recorded.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MatrixTextMuted
                                )
                            }
                        }
                    } else {
                        items(privateTargets) { target ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MatrixDarkSurfaceVariant)
                                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "[${target.scope.name}] IP: ${target.ip.ifBlank { "N/A" }}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MatrixGreenPrimary
                                        )
                                        Text(
                                            text = "Crypto: ${target.stolenCrypto} ₡",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberAmber
                                        )
                                    }
                                    Text(
                                        text = "FW: Lvl ${target.fw} | ENC: Lvl ${target.enc} | Rep: ${target.rep}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MatrixTextSecondary
                                    )
                                    Text(
                                        text = "Uploaded by: ${target.contributor.ifBlank { "You" }}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = MatrixGreenDim
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HistoryLogsTab.CREW_ONLINE -> {
                TerminalContainer(
                    title = "CREW REAL-TIME ONLINE STREAM",
                    subtitle = "SOCIAL FEED",
                    trailingBadge = "${generalTargets.size} ONLINE INGESTS"
                ) {
                    Text(
                        text = "> Real-time log feed of targets uploaded by crew operatives to shared DB.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MatrixGreenGlow
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (generalTargets.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No online crew activity recorded yet.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MatrixTextMuted
                                )
                            }
                        }
                    } else {
                        items(generalTargets.sortedByDescending { it.lastUpdated }) { target ->
                            val contributorTag = if (target.contributor.isBlank()) "Anonymous_Operative" else target.contributor
                            val isMe = contributorTag.equals(currentProfile, ignoreCase = true)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MatrixDarkSurfaceVariant)
                                    .border(
                                        BorderStroke(1.dp, if (isMe) MatrixGreenGlow else MatrixBorderBright),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⚡ OPERATOR [$contributorTag] ${if (isMe) "(YOU)" else ""}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMe) MatrixGreenGlow else CyberAmber
                                        )
                                        Text(
                                            text = "SHARED GENERAL DB",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.sp,
                                            color = MatrixTextMuted
                                        )
                                    }
                                    Text(
                                        text = "> Ingested IP: ${target.ip} | Wallet: ${target.wallet.ifBlank { "N/A" }}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MatrixGreenPrimary
                                    )
                                    Text(
                                        text = "FW: Lvl ${target.fw} | ENC: Lvl ${target.enc} | Stolen: ${target.stolenCrypto} ₡",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MatrixTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HistoryLogsTab.CONSOLE -> {
                TerminalContainer(
                    title = "SYSTEM TERMINAL LOG CONSOLE",
                    trailingBadge = "${scannerState.terminalLogs.size} EVENTS"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "> Real-time system console events, parser status & network sync logs.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MatrixTextMuted
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (scannerState.terminalLogs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Terminal console is clean. Awaiting operations.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MatrixTextMuted
                                )
                            }
                        }
                    } else {
                        items(scannerState.terminalLogs) { logLine ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(MatrixDarkSurfaceVariant)
                                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = logLine,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = when {
                                        logLine.contains("SUCCESS", true) || logLine.contains("COMPLETE", true) -> MatrixGreenGlow
                                        logLine.contains("ERROR", true) || logLine.contains("WARN", true) -> CyberAmber
                                        else -> MatrixGreenPrimary
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CrewAccountEntity
import com.example.data.model.TargetEntity
import com.example.ui.components.TerminalContainer
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
fun OperationalMetricsScreen(
    viewModel: IntelViewModel,
    modifier: Modifier = Modifier
) {
    val internalTargets by viewModel.internalTargets.collectAsState()
    val externalTargets by viewModel.externalTargets.collectAsState()
    val generalTargets by viewModel.generalTargets.collectAsState()
    val crewAccounts by viewModel.crewAccounts.collectAsState()
    val expandedContributor by viewModel.expandedContributor.collectAsState()

    // Combined unique targets for stats
    val allTargets = (internalTargets + externalTargets + generalTargets).distinctBy { it.ip }

    val totalHits = allTargets.sumOf { it.hitCount }
    val totalStolen = allTargets.sumOf { it.stolenCrypto }
    val avgPerHit = if (totalHits > 0) totalStolen / totalHits else 0L
    val targetsCount = allTargets.size

    // Determine Peak Window
    val peakHours = allTargets.map { it.peakHour }.filter { it != "--:--" }
    val peakWindow = if (peakHours.isNotEmpty()) {
        val mostCommonHour = peakHours.groupBy { it }.maxByOrNull { it.value.size }?.key ?: "11:00"
        "$mostCommonHour - ${calculateNextHour(mostCommonHour)}"
    } else {
        "11:00 - 12:00"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "OPERATIONAL METRICS & CREW TELEMETRY",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixGreenDim,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3 Key Metrics Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                title = "Avg/hit",
                value = "$avgPerHit ₡",
                subtext = "crypto per hit",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Targets",
                value = targetsCount.toString(),
                subtext = "indexed IPs",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Peak window",
                value = peakWindow,
                subtext = "optimal attack",
                modifier = Modifier.weight(1.3f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Contributors Overview
        TerminalContainer(
            title = "ONLINE BRANCH: CONTRIBUTORS OVERVIEW",
            trailingBadge = "CREW SYNC"
        ) {
            Column {
                Text(
                    text = "CONTRIBUTORS OVERVIEW (${crewAccounts.size} REGISTERED):",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenGlow
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live telemetry of registered crew members. View contributors with online status, total contributed targets, and drill into target dossiers.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MatrixTextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                CrewContributorsList(
                    crewAccounts = crewAccounts,
                    allTargets = allTargets,
                    expandedContributor = expandedContributor,
                    onToggleExpand = { viewModel.toggleContributorExpansion(it) },
                    onSelectTarget = { viewModel.selectTargetForDossier(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MatrixDarkSurface)
            .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(6.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixGreenDim,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MatrixGreenPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtext,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = MatrixTextMuted
        )
    }
}

@Composable
private fun CrewContributorsList(
    crewAccounts: List<CrewAccountEntity>,
    allTargets: List<TargetEntity>,
    expandedContributor: String?,
    onToggleExpand: (String) -> Unit,
    onSelectTarget: (TargetEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (acc in crewAccounts) {
            val userTargets = allTargets.filter {
                it.contributor.equals(acc.username, ignoreCase = true)
            }
            val isExpanded = expandedContributor == acc.username

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MatrixDarkSurfaceVariant)
                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand(acc.username) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Green circle for online status indicator as mandated
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (acc.isOnline) MatrixGreenPrimary else MatrixGreenDim)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[${acc.username}]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (acc.isOnline) "ONLINE" else "OFFLINE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = if (acc.isOnline) MatrixGreenGlow else MatrixTextMuted
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${userTargets.size} IPs",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MatrixTextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand contributor",
                            tint = MatrixGreenPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Expandable IP List
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF020703))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (userTargets.isEmpty()) {
                            Text(
                                text = "No IPs contributed yet by ${acc.username}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MatrixTextMuted,
                                modifier = Modifier.padding(4.dp)
                            )
                        } else {
                            for (t in userTargets) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MatrixDarkSurfaceVariant)
                                        .clickable { onSelectTarget(t) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = t.ip,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MatrixGreenPrimary
                                        )
                                        Text(
                                            text = "Account: ${t.name.ifBlank { "Unidentified" }}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = MatrixTextSecondary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${t.stolenCrypto} ₡",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MatrixGreenGlow
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = "Open Target",
                                            tint = MatrixGreenDim,
                                            modifier = Modifier.size(14.dp)
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
}

private fun calculateNextHour(hourStr: String): String {
    val h = hourStr.substringBefore(":").toIntOrNull() ?: 11
    val nextH = (h + 1) % 24
    return String.format("%02d:00", nextH)
}

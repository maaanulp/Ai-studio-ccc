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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
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
import com.example.data.model.TargetEntity
import com.example.ui.components.HackerButton
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
import com.example.ui.viewmodel.CrewRankStats
import com.example.ui.viewmodel.IntelViewModel
import com.example.ui.viewmodel.MetricsSubTab
import com.example.ui.viewmodel.OperativeRankStats
import com.example.ui.viewmodel.PeakWindowInfo

@Composable
fun OperationalMetricsScreen(
    viewModel: IntelViewModel,
    modifier: Modifier = Modifier
) {
    val internalTargets by viewModel.internalTargets.collectAsState()
    val externalTargets by viewModel.externalTargets.collectAsState()
    val generalTargets by viewModel.generalTargets.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()

    val metricsSubTab by viewModel.metricsSubTab.collectAsState()
    val operativeRankStats by viewModel.operativeRankStats.collectAsState()
    val crewRankStats by viewModel.crewRankStats.collectAsState()
    val myOperativeStats by viewModel.myOperativeStats.collectAsState()
    val expandedContributor by viewModel.expandedContributor.collectAsState()
    val peakWindowInfo by viewModel.peakWindowInfo.collectAsState()

    // Combined unique targets for stats
    val allTargets = (internalTargets + externalTargets + generalTargets).distinctBy { it.ip }

    val totalHits = allTargets.sumOf { it.hitCount }
    val totalStolen = allTargets.sumOf { it.stolenCrypto }
    val avgPerHit = if (totalHits > 0) totalStolen / totalHits else 0L

    val activeHandle = currentProfile.trim().ifBlank { "m0lt0rn" }

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

        // 1. Reorganized OPERATIVE PERFORMANCE Top Card with 2x2 Key Financial & Tactical Indicators
        PersonalTelemetryCard(
            activeHandle = activeHandle,
            myStats = myOperativeStats,
            avgPerHit = avgPerHit
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Predictive PEAK WINDOW Card (Dynamic analytics engine based on log timestamps & crypto density)
        PredictivePeakWindowCard(
            peakWindowInfo = peakWindowInfo
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Crew Telemetry Leaderboards Section
        TerminalContainer(
            title = "CREW TELEMETRY LEADERBOARD",
            trailingBadge = "RANKINGS"
        ) {
            Column {
                // Sub-Tab Navigation Bar: [ MY CREW ], [ CREWS RANK ], [ GLOBAL TOP ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixDarkSurfaceVariant)
                        .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HackerButton(
                        text = "[ MY CREW ]",
                        onClick = { viewModel.setMetricsSubTab(MetricsSubTab.MY_CREW) },
                        modifier = Modifier.weight(1f),
                        testTag = "subtab_my_crew_btn"
                    )
                    HackerButton(
                        text = "[ CREWS RANK ]",
                        onClick = { viewModel.setMetricsSubTab(MetricsSubTab.CREWS_RANK) },
                        modifier = Modifier.weight(1f),
                        testTag = "subtab_crews_rank_btn"
                    )
                    HackerButton(
                        text = "[ GLOBAL TOP ]",
                        onClick = { viewModel.setMetricsSubTab(MetricsSubTab.GLOBAL_TOP) },
                        modifier = Modifier.weight(1f),
                        testTag = "subtab_global_top_btn"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (metricsSubTab) {
                    MetricsSubTab.MY_CREW -> {
                        val activeCrewId = myOperativeStats?.crewId ?: "CCC"
                        val myCrewOperatives = operativeRankStats.filter {
                            it.crewId.equals(activeCrewId, ignoreCase = true)
                        }.sortedByDescending { it.totalPts }

                        MyCrewLeaderboard(
                            crewId = activeCrewId,
                            operatives = myCrewOperatives,
                            activeHandle = activeHandle,
                            allTargets = allTargets,
                            expandedContributor = expandedContributor,
                            onToggleExpand = { viewModel.toggleContributorExpansion(it) },
                            onSelectTarget = { viewModel.selectTargetForDossier(it) }
                        )
                    }

                    MetricsSubTab.CREWS_RANK -> {
                        CrewsRankLeaderboard(
                            crews = crewRankStats,
                            activeCrewId = myOperativeStats?.crewId ?: "CCC"
                        )
                    }

                    MetricsSubTab.GLOBAL_TOP -> {
                        GlobalTopLeaderboard(
                            operatives = operativeRankStats,
                            activeHandle = activeHandle
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PersonalTelemetryCard(
    activeHandle: String,
    myStats: OperativeRankStats?,
    avgPerHit: Long
) {
    val totalPts = myStats?.totalPts ?: 35L
    val rankText = "#${myStats?.globalRank ?: 1} GLOBAL // #${myStats?.crewRank ?: 1} CREW"
    val targetsCount = myStats?.targetsIndexed ?: 1
    val walletsCount = myStats?.walletMatches ?: 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MatrixDarkSurface)
            .border(BorderStroke(1.dp, MatrixGreenGlow), RoundedCornerShape(6.dp))
            .padding(12.dp)
            .testTag("personal_telemetry_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MatrixGreenPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "OPERATIVE PERFORMANCE: [$activeHandle]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenPrimary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(MatrixDarkBackground)
                    .border(BorderStroke(1.dp, CyberAmber), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = CyberAmber,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = rankText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberAmber
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2x2 Grid of Key Financial & Tactical Indicators
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Row 1: TELEMETRY SCORE + AVG CRYPTO / HIT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixDarkBackground)
                        .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "TELEMETRY SCORE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MatrixTextMuted
                        )
                        Text(
                            text = "$totalPts PTS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenGlow
                        )
                        Text(
                            text = "contributions score",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = MatrixTextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixDarkBackground)
                        .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "AVG CRYPTO / HIT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MatrixTextMuted
                        )
                        Text(
                            text = "$avgPerHit ₡",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenPrimary
                        )
                        Text(
                            text = "crypto per hit",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = MatrixTextSecondary
                        )
                    }
                }
            }

            // Row 2: INDEXED TARGETS + WALLETS LINKED
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixDarkBackground)
                        .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "INDEXED TARGETS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MatrixTextMuted
                        )
                        Text(
                            text = "$targetsCount Targets",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenPrimary
                        )
                        Text(
                            text = "registered IPs",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = MatrixTextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixDarkBackground)
                        .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "WALLETS LINKED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MatrixTextMuted
                        )
                        Text(
                            text = "$walletsCount Matches",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenSecondary
                        )
                        Text(
                            text = "wallet matches",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = MatrixTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictivePeakWindowCard(
    peakWindowInfo: PeakWindowInfo
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MatrixDarkSurface)
            .border(BorderStroke(1.dp, CyberAmber.copy(alpha = 0.8f)), RoundedCornerShape(6.dp))
            .padding(12.dp)
            .testTag("predictive_peak_window_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PREDICTIVE PEAK WINDOW",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberAmber
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(MatrixDarkBackground)
                    .border(BorderStroke(1.dp, CyberAmber), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "ANALYTICS ENGINE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberAmber
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = peakWindowInfo.windowFormatted,
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (peakWindowInfo.isCalculating) MatrixTextMuted else MatrixGreenGlow
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = if (peakWindowInfo.isCalculating) {
                "CALCULATING... // NEED MORE LOGS"
            } else {
                "optimal attack window (highest avg crypto available)"
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = MatrixTextSecondary
        )
    }
}

@Composable
private fun MyCrewLeaderboard(
    crewId: String,
    operatives: List<OperativeRankStats>,
    activeHandle: String,
    allTargets: List<TargetEntity>,
    expandedContributor: String?,
    onToggleExpand: (String) -> Unit,
    onSelectTarget: (TargetEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MY CREW LEADERBOARD // CREW [$crewId]",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MatrixGreenGlow
            )
            Text(
                text = "${operatives.size} MEMBERS",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MatrixTextMuted
            )
        }

        for (op in operatives) {
            val isMe = op.handle.equals(activeHandle, ignoreCase = true)
            val userTargets = allTargets.filter {
                it.contributor.equals(op.handle, ignoreCase = true)
            }
            val isExpanded = expandedContributor == op.handle

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isMe) MatrixDarkSurfaceVariant else MatrixDarkSurface)
                    .border(
                        BorderStroke(1.dp, if (isMe) MatrixGreenGlow else MatrixBorder),
                        RoundedCornerShape(4.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand(op.handle) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Position badge #1, #2, #3
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (op.crewRank == 1) CyberAmber else MatrixDarkBackground)
                                .border(BorderStroke(1.dp, MatrixBorderBright), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#${op.crewRank}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (op.crewRank == 1) Color.Black else MatrixGreenPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (op.isOnline) MatrixGreenPrimary else MatrixGreenDim)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "[${op.handle}]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMe) MatrixGreenGlow else MatrixTextPrimary
                        )

                        if (isMe) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(YOU)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MatrixGreenGlow
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${op.totalPts} PTS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MatrixGreenGlow
                            )
                            Text(
                                text = "${op.targetsIndexed} IPs | ${op.walletMatches} Wallets",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = MatrixTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

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
                                text = "No targets contributed yet by ${op.handle}",
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
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
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

@Composable
private fun CrewsRankLeaderboard(
    crews: List<CrewRankStats>,
    activeCrewId: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GLOBAL CREWS RANKINGS // ALL CLANS",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = CyberAmber
            )
            Text(
                text = "${crews.size} CREWS",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MatrixTextMuted
            )
        }

        for (crew in crews) {
            val isMyCrew = crew.crewId.equals(activeCrewId, ignoreCase = true)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isMyCrew) MatrixDarkSurfaceVariant else MatrixDarkSurface)
                    .border(
                        BorderStroke(1.dp, if (isMyCrew) CyberAmber else MatrixBorder),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (crew.globalRank == 1) CyberAmber else MatrixDarkBackground)
                            .border(BorderStroke(1.dp, MatrixBorderBright), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${crew.globalRank}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (crew.globalRank == 1) Color.Black else MatrixGreenPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = if (isMyCrew) CyberAmber else MatrixGreenPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "[CREW ${crew.crewId}]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMyCrew) CyberAmber else MatrixTextPrimary
                            )
                        }
                        Text(
                            text = "${crew.totalMembers} Members | ${crew.totalTargets} IPs | ${crew.walletMatches} Matches",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MatrixTextMuted
                        )
                    }
                }

                Text(
                    text = "${crew.totalPts} PTS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMyCrew) CyberAmber else MatrixGreenGlow
                )
            }
        }
    }
}

@Composable
private fun GlobalTopLeaderboard(
    operatives: List<OperativeRankStats>,
    activeHandle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GLOBAL OPERATIVES LEADERBOARD // TOP INDIVIDUALS",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MatrixGreenPrimary
            )
            Text(
                text = "${operatives.size} OPERATIVES",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MatrixTextMuted
            )
        }

        for (op in operatives) {
            val isMe = op.handle.equals(activeHandle, ignoreCase = true)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isMe) MatrixDarkSurfaceVariant else MatrixDarkSurface)
                    .border(
                        BorderStroke(1.dp, if (isMe) MatrixGreenGlow else MatrixBorder),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (op.globalRank == 1) CyberAmber else MatrixDarkBackground)
                            .border(BorderStroke(1.dp, MatrixBorderBright), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${op.globalRank}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (op.globalRank == 1) Color.Black else MatrixGreenPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "[${op.handle}]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMe) MatrixGreenGlow else MatrixTextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${op.crewId})",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = MatrixTextMuted
                            )
                        }
                        Text(
                            text = "${op.targetsIndexed} IPs | ${op.walletMatches} Wallets",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MatrixTextSecondary
                        )
                    }
                }

                Text(
                    text = "${op.totalPts} PTS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenGlow
                )
            }
        }
    }
}

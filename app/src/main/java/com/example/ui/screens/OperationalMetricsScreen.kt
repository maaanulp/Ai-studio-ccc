package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.HackerButton
import com.example.ui.components.TerminalContainer
import com.example.ui.theme.*
import com.example.ui.viewmodel.IntelViewModel
import com.example.ui.viewmodel.MetricsSubTab
import com.example.ui.viewmodel.OperativeRankStats

@Composable
fun OperationalMetricsScreen(
    viewModel: IntelViewModel,
    modifier: Modifier = Modifier
) {
    val subTab by viewModel.metricsSubTab.collectAsState()
    val operativeStats by viewModel.operativeRankStats.collectAsState()
    val crewStats by viewModel.crewRankStats.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "OPERATIONAL METRICS & CREW TELEMETRY",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixGreenDim,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HackerButton(
                text = "CREW RANK",
                onClick = { viewModel.setMetricsSubTab(MetricsSubTab.CREW_RANK) },
                enabled = subTab == MetricsSubTab.CREW_RANK,
                modifier = Modifier.weight(1f)
            )
            HackerButton(
                text = "GLOBAL RANK",
                onClick = { viewModel.setMetricsSubTab(MetricsSubTab.GLOBAL_RANK) },
                enabled = subTab == MetricsSubTab.GLOBAL_RANK,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (subTab) {
            MetricsSubTab.CREW_RANK -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(operativeStats.filter { it.crewId.equals("CCC", ignoreCase = true) }) { op ->
                        val isMe = currentProfile.isNotBlank() && op.handle.equals(currentProfile, ignoreCase = true)
                        OperativeCard(op = op, isMe = isMe)
                    }
                }
            }
            MetricsSubTab.GLOBAL_RANK -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(operativeStats.sortedByDescending { it.totalPts }) { op ->
                        val isMe = currentProfile.isNotBlank() && op.handle.equals(currentProfile, ignoreCase = true)
                        OperativeCard(op = op, isMe = isMe)
                    }
                }
            }
        }
    }
}

@Composable
private fun OperativeCard(op: OperativeRankStats, isMe: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (isMe) MatrixDarkSurfaceVariant else MatrixDarkSurface)
            .border(BorderStroke(1.dp, if (isMe) MatrixGreenPrimary else MatrixBorder), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("#${op.crewRank} ${op.handle}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MatrixGreenPrimary)
                    if (isMe) {
                        Text("(YOU)", fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MatrixGreenGlow)
                    }
                }
                Text("Targets: ${op.targetsIndexed} | Wallets: ${op.walletMatches} | Apps: ${op.appsUpdated}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MatrixTextMuted)
            }
            Text("${op.totalPts} PTS", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MatrixGreenGlow)
        }
    }
}

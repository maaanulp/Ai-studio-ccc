package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TargetEntity
import com.example.ui.theme.CyberCyan
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetSpecsDialog(
    target: TargetEntity?,
    onDismiss: () -> Unit,
    onSyncToCloud: ((TargetEntity) -> Unit)? = null,
    onAnalyzeTactics: ((TargetEntity) -> Unit)? = null
) {
    if (target == null) return
    val context = LocalContext.current

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied $label: $text", Toast.LENGTH_SHORT).show()
    }

    val hasValidCustomName = target.name.isNotBlank() &&
            !target.name.equals(target.ip, ignoreCase = true) &&
            !target.name.equals("Target-${target.ip}", ignoreCase = true) &&
            !target.name.startsWith("Host-") &&
            !target.name.startsWith("Host_") &&
            !target.name.startsWith("Pending_IP_") &&
            !target.name.equals("UNASSIGNED TARGET", ignoreCase = true) &&
            !target.name.equals("UNASSIGNED", ignoreCase = true) &&
            !target.name.equals("Unknown", ignoreCase = true)

    val titleHeaderText = buildString {
        append(target.ip)
        if (hasValidCustomName) {
            append(" [${target.name}]")
        }
        if (target.crew.isNotBlank() && !target.crew.equals("Unknown", ignoreCase = true) && !target.crew.equals("None", ignoreCase = true) && !target.crew.equals("Unassigned", ignoreCase = true)) {
            append(" [${target.crew.uppercase()}]")
        }
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MatrixDarkSurface)
            .border(BorderStroke(1.5.dp, MatrixBorderBright), RoundedCornerShape(8.dp))
            .padding(14.dp)
            .testTag("target_specs_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Header: TARGET SPECS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TARGET SPECS // INTEL SPECIFICATIONS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MatrixGreenDim,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = titleHeaderText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenPrimary
                        )
                        if (target.rebootTag.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(com.example.ui.theme.PurgeRed.copy(alpha = 0.2f))
                                    .border(BorderStroke(1.dp, com.example.ui.theme.PurgeRed), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "[${target.rebootTag.uppercase()}] REBOOT",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = com.example.ui.theme.PurgeRedText
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MatrixGreenPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MatrixBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Quick Actions: Copy IP, Copy Wallet
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HackerButton(
                    text = "[COPY IP]",
                    onClick = { copyToClipboard("IP", target.ip) },
                    modifier = Modifier.weight(1f),
                    testTag = "specs_copy_ip_btn"
                )
                if (target.wallet.isNotBlank() && target.wallet != "N/A" && target.wallet != "Unknown") {
                    HackerButton(
                        text = "[COPY WALLET]",
                        onClick = { copyToClipboard("Wallet", target.wallet) },
                        modifier = Modifier.weight(1f),
                        testTag = "specs_copy_wallet_btn"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section 1: Target Account Metrics (Indexed By / Contributor located here)
            Text(
                text = "1. TARGET ACCOUNT METRICS",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MatrixGreenGlow
            )
            Spacer(modifier = Modifier.height(6.dp))

            InfoGrid(
                items = listOf(
                    "Name" to if (hasValidCustomName) target.name else "N/A",
                    "Indexed By" to target.contributor.ifBlank { "You" },
                    "Crew" to target.crew.ifBlank { "None / Lone" },
                    "Level" to if (target.level > 0) "LVL ${target.level}" else "N/A",
                    "Reputation" to target.rep.toString(),
                    "Score" to if (target.score > 0) target.score.toString() else "N/A",
                    "Database" to target.scope.name
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Section 2: Defense & Security Specs (Unverified FW ? / ENC ? with dim gray color)
            Text(
                text = "2. DEFENSE & SECURITY SPECS",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MatrixGreenGlow
            )
            Spacer(modifier = Modifier.height(6.dp))
            InfoGrid(
                items = listOf(
                    "Firewall (FW)" to if (target.fw > 0) "LVL ${target.fw}" else "FW ? (UNVERIFIED)",
                    "Encryptor (ENC)" to if (target.enc > 0) "LVL ${target.enc}" else "ENC ? (UNVERIFIED)",
                    "Wallet" to target.wallet.ifBlank { "Unknown" }
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Section 3: Financial & Raid Stats
            Text(
                text = "3. FINANCIAL & RAID STATS",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MatrixGreenGlow
            )
            Spacer(modifier = Modifier.height(6.dp))
            InfoGrid(
                items = listOf(
                    "Total Stolen" to "${target.stolenCrypto} Crypto",
                    "Total Hits" to target.hitCount.toString(),
                    "Avg / Hit" to "${target.avgPerHit} Crypto",
                    "CR / Hour" to "~${target.crPerHour} CR/h",
                    "Peak Attack Hour" to target.peakHour.ifBlank { "21:00" }
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Section 4: Software & Apps Matrix
            Text(
                text = "4. INSTALLED APPS / SOFTWARE MATRIX",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MatrixGreenGlow
            )
            Spacer(modifier = Modifier.height(6.dp))

            val apps = listOf(
                "Antivirus" to target.antivirusLvl,
                "Spam" to target.spamLvl,
                "Rootkit" to target.rootkitLvl,
                "Firewall" to target.firewallAppLvl.let { if (it == 0) target.fw else it },
                "Bypasser" to target.bypasserLvl,
                "Password Cracker" to target.passwordCrackerLvl,
                "Password Encryptor" to target.passwordEncryptorLvl.let { if (it == 0) target.enc else it },
                "Proxy" to target.proxyLvl,
                "Trace" to target.traceLvl,
                "Keygen" to target.keygenLvl,
                "Siphon" to target.siphonLvl
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MatrixDarkSurfaceVariant)
                    .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(4.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                apps.forEachIndexed { index, (appName, appLvl) ->
                    val hasLevel = appLvl > 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (hasLevel) MatrixDarkBackground else MatrixDarkSurface)
                            .border(
                                BorderStroke(0.5.dp, if (hasLevel) MatrixBorder else MatrixBorder.copy(alpha = 0.5f)),
                                RoundedCornerShape(3.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%02d.", index + 1),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasLevel) MatrixGreenDim else MatrixTextMuted,
                            modifier = Modifier.width(26.dp)
                        )
                        Text(
                            text = appName,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasLevel) MatrixTextPrimary else MatrixTextMuted,
                            modifier = Modifier.width(155.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "····································",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = MatrixBorder,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            overflow = TextOverflow.Clip
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (hasLevel) MatrixGreenDim.copy(alpha = 0.3f) else MatrixDarkSurfaceVariant)
                                .border(
                                    BorderStroke(1.dp, if (hasLevel) MatrixGreenPrimary else MatrixBorder),
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (hasLevel) "LVL $appLvl" else "[--]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasLevel) MatrixGreenGlow else MatrixTextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (onAnalyzeTactics != null) {
                HackerButton(
                    text = "⚡ GEMINI TACTICAL ASSESSMENT",
                    onClick = { onAnalyzeTactics(target) },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "gemini_tactical_btn"
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (onSyncToCloud != null) {
                HackerButton(
                    text = "▶ SYNC TO FIREBASE CLOUD",
                    onClick = {
                        onSyncToCloud(target)
                        Toast.makeText(context, "Syncing ${target.ip} to Firebase...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "sync_target_firebase_btn"
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            HackerButton(
                text = "[DISMISS TARGET SPECS]",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                testTag = "dismiss_target_specs_btn"
            )
        }
    }
}

// Backward-compatible alias
@Composable
fun TargetDossierDialog(
    target: TargetEntity?,
    onDismiss: () -> Unit,
    onGenerateReport: ((TargetEntity) -> Unit)? = null
) {
    TargetSpecsDialog(target = target, onDismiss = onDismiss)
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MatrixDarkSurfaceVariant)
            .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        items.forEach { (label, value) ->
            val isUnverified = value.contains("UNVERIFIED") || value.contains("?") || value == "N/A" || value == "Unknown"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MatrixTextMuted
                )
                Text(
                    text = value,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnverified) MatrixTextMuted else MatrixGreenPrimary
                )
            }
        }
    }
}

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TargetEntity
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
fun TargetDossierDialog(
    target: TargetEntity?,
    onDismiss: () -> Unit,
    onGenerateReport: ((TargetEntity) -> Unit)? = null
) {
    if (target == null) return
    val context = LocalContext.current

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied $label: $text", Toast.LENGTH_SHORT).show()
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MatrixDarkSurface)
            .border(BorderStroke(1.5.dp, MatrixBorderBright), RoundedCornerShape(8.dp))
            .padding(14.dp)
            .testTag("target_dossier_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DOSSIER // TARGET INTEL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MatrixGreenDim,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = target.ip,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixGreenPrimary
                    )
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
                    modifier = Modifier.weight(1f)
                )
                if (target.wallet.isNotBlank()) {
                    HackerButton(
                        text = "[COPY WALLET]",
                        onClick = { copyToClipboard("Wallet", target.wallet) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section: Target Profile
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
                    "Name" to target.name.ifBlank { "Unidentified" },
                    "Crew" to target.crew.ifBlank { "None / Lone" },
                    "Level" to target.level.toString(),
                    "Reputation" to target.rep.toString(),
                    "Score" to if (target.score > 0) target.score.toString() else "N/A",
                    "Database" to target.scope.name
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Section: Defenses
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
                    "Firewall (FW)" to "LVL ${target.fw}",
                    "Encryptor (ENC)" to "LVL ${target.enc}",
                    "Contributor" to target.contributor,
                    "Wallet" to target.wallet.ifBlank { "Unknown" }
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Section: Financial Performance
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
                    "Peak Attack Hour" to target.peakHour
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Section: Software & Apps Matrix
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
                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                apps.chunked(2).forEach { rowApps ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        for ((appName, appLvl) in rowApps) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = appName,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MatrixTextSecondary
                                )
                                Text(
                                    text = if (appLvl > 0) "LVL $appLvl" else "[--]",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (appLvl > 0) MatrixGreenPrimary else MatrixTextMuted,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HackerButton(
                text = "[GENERATE & SAVE INTEL REPORT (ROOM)]",
                onClick = {
                    onGenerateReport?.invoke(target)
                    Toast.makeText(context, "Intel Report saved to Room DB!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generate_intel_report_button")
            )
            Spacer(modifier = Modifier.height(8.dp))
            HackerButton(
                text = "[DISMISS DOSSIER]",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
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
                    color = MatrixGreenPrimary
                )
            }
        }
    }
}

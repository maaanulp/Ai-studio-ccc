package com.example.ui.screens

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.MatrixRainCanvas
import com.example.ui.components.TargetDossierDialog
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
import com.example.ui.viewmodel.AppSection
import com.example.ui.viewmodel.IntelViewModel

@Composable
fun MainScreen(viewModel: IntelViewModel) {
    val currentSection by viewModel.currentSection.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val selectedTarget by viewModel.selectedTargetForDossier.collectAsState()

    var showProfileDropdown by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(MatrixDarkBackground)) {
        // Animated Matrix Falling Rain in the background
        MatrixRainCanvas(
            modifier = Modifier.fillMaxSize(),
            alpha = 0.22f,
            fontSize = 28f
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Hacker Top App Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(MatrixDarkSurface.copy(alpha = 0.95f))
                        .border(BorderStroke(1.dp, MatrixBorder))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            // Mandatory exact title from brief
                            Text(
                                text = "Crypt0 Cr3w Central [CCC] by [m0lt0rn]",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MatrixGreenPrimary
                            )
                            Text(
                                text = "HACK EX 2 // INTEL ENGINE v4.2",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MatrixTextMuted
                            )
                        }

                        // Solo Player Profile Switcher
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MatrixDarkSurfaceVariant)
                                    .border(BorderStroke(1.dp, MatrixGreenDim), RoundedCornerShape(4.dp))
                                    .clickable { showProfileDropdown = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MatrixGreenPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "[$currentProfile]",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MatrixGreenPrimary
                                )
                            }

                            DropdownMenu(
                                expanded = showProfileDropdown,
                                onDismissRequest = { showProfileDropdown = false },
                                modifier = Modifier
                                    .background(MatrixDarkSurface)
                                    .border(BorderStroke(1.dp, MatrixBorderBright))
                            ) {
                                val profiles = listOf("m0lt0rn", "Cipher_99", "ZeroByte", "Ghost_Solo")
                                profiles.forEach { p ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (p == currentProfile) "• [$p] (Active)" else "[$p]",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                color = if (p == currentProfile) MatrixGreenGlow else MatrixTextSecondary
                                            )
                                        },
                                        onClick = {
                                            viewModel.switchProfile(p)
                                            showProfileDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                // Monospace Cyber Navigation Bar
                NavigationBar(
                    containerColor = MatrixDarkSurface.copy(alpha = 0.95f),
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(BorderStroke(1.dp, MatrixBorder))
                ) {
                    val navItems = listOf(
                        NavEntry(AppSection.PROCESS_LOGS, "Process Logs", Icons.Default.Terminal, "nav_process_logs"),
                        NavEntry(AppSection.SCREENSHOT_SCANNER, "Scanner", Icons.Default.CropFree, "nav_scanner"),
                        NavEntry(AppSection.OPERATIONAL_METRICS, "Metrics", Icons.Default.Analytics, "nav_metrics"),
                        NavEntry(AppSection.INTEL_DATABASE, "Intel DB", Icons.Default.Storage, "nav_intel_db")
                    )

                    navItems.forEach { item ->
                        val selected = currentSection == item.section
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.setSection(item.section) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MatrixDarkBackground,
                                selectedTextColor = MatrixGreenPrimary,
                                indicatorColor = MatrixGreenPrimary,
                                unselectedIconColor = MatrixTextMuted,
                                unselectedTextColor = MatrixTextMuted
                            ),
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Crossfade(targetState = currentSection, label = "SectionCrossfade") { section ->
                    when (section) {
                        AppSection.PROCESS_LOGS -> ProcessLogsScreen(viewModel = viewModel)
                        AppSection.SCREENSHOT_SCANNER -> ScreenshotScannerScreen(viewModel = viewModel)
                        AppSection.OPERATIONAL_METRICS -> OperationalMetricsScreen(viewModel = viewModel)
                        AppSection.INTEL_DATABASE -> IntelligenceDatabaseScreen(viewModel = viewModel)
                    }
                }
            }
        }

        // Target Intel Dossier Modal / Dialog (when clicked anywhere)
        TargetDossierDialog(
            target = selectedTarget,
            onDismiss = { viewModel.selectTargetForDossier(null) },
            onGenerateReport = { target -> viewModel.generateAndSaveIntelligenceReport(target) }
        )
    }
}

private data class NavEntry(
    val section: AppSection,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

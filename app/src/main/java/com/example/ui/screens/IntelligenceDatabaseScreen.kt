package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TargetEntity
import com.example.ui.components.HackerButton
import com.example.ui.components.HackerSearchField
import com.example.ui.components.RedPurgeButton
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
import com.example.ui.theme.PurgeRed
import com.example.ui.theme.PurgeRedContainer
import com.example.ui.theme.PurgeRedText
import com.example.ui.viewmodel.DatabaseTab
import com.example.ui.viewmodel.IntelViewModel
import com.example.ui.viewmodel.SortField

@Composable
fun IntelligenceDatabaseScreen(
    viewModel: IntelViewModel,
    modifier: Modifier = Modifier
) {
    val databaseTab by viewModel.databaseTab.collectAsState()
    val internalTargets by viewModel.internalTargets.collectAsState()
    val externalTargets by viewModel.externalTargets.collectAsState()
    val generalTargets by viewModel.generalTargets.collectAsState()

    val internalQuery by viewModel.internalSearchQuery.collectAsState()
    val externalQuery by viewModel.externalSearchQuery.collectAsState()
    val generalQuery by viewModel.generalSearchQuery.collectAsState()

    val sortField by viewModel.currentSort.collectAsState()
    val isGenAuthenticated by viewModel.isGeneralDbAuthenticated.collectAsState()
    val genAuthError by viewModel.generalDbAuthError.collectAsState()

    val crewId by viewModel.crewIdInput.collectAsState()
    val crewPassword by viewModel.crewPasswordInput.collectAsState()

    val context = LocalContext.current
    var showAdminPurgeDialog by remember { mutableStateOf(false) }
    var adminPasswordAttempt by remember { mutableStateOf("") }
    var isCrewLoginExpanded by remember { mutableStateOf(!isGenAuthenticated) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "SECTION 04 // INTELLIGENCE DATABASE",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MatrixGreenDim,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3 Tabs: Internal, External, General
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MatrixDarkSurface)
                .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DbTabButton(
                title = "INTERNAL",
                selected = databaseTab == DatabaseTab.INTERNAL,
                onClick = { viewModel.setDatabaseTab(DatabaseTab.INTERNAL) },
                modifier = Modifier.weight(1f),
                testTag = "tab_db_internal"
            )
            DbTabButton(
                title = "EXTERNAL",
                selected = databaseTab == DatabaseTab.EXTERNAL,
                onClick = { viewModel.setDatabaseTab(DatabaseTab.EXTERNAL) },
                modifier = Modifier.weight(1f),
                testTag = "tab_db_external"
            )
            DbTabButton(
                title = "GENERAL",
                selected = databaseTab == DatabaseTab.GENERAL,
                onClick = { viewModel.setDatabaseTab(DatabaseTab.GENERAL) },
                modifier = Modifier.weight(1f),
                testTag = "tab_db_general"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (databaseTab) {
            DatabaseTab.INTERNAL -> {
                // Reminder banner
                PrivacyReminderBanner(text = "internal database remains private")

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar
                HackerSearchField(
                    query = internalQuery,
                    onQueryChange = { viewModel.setInternalSearchQuery(it) },
                    placeholder = "Search IP, account, app or wallet",
                    testTag = "search_internal"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Action row: export all to general database
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HackerButton(
                        text = "export all to general database",
                        onClick = {
                            viewModel.exportAllToGeneralDatabase { count ->
                                Toast.makeText(context, "Exported $count targets to General Database", Toast.LENGTH_SHORT).show()
                            }
                        },
                        testTag = "export_all_general_btn"
                    )

                    Text(
                        text = "${internalTargets.size} Targets",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MatrixTextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Intel Data Grid Table
                IntelGridTable(
                    targets = internalTargets,
                    sortField = sortField,
                    onToggleFw = { viewModel.toggleSortFw() },
                    onToggleAvg = { viewModel.toggleSortAvg() },
                    onSelectTarget = { viewModel.selectTargetForDossier(it) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Red Purge Button at the bottom
                RedPurgeButton(
                    text = "purge internal database",
                    onPurgeConfirmed = {
                        viewModel.purgeInternalDatabase()
                        Toast.makeText(context, "Internal database purged", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    dialogTitle = "PURGE INTERNAL DATABASE",
                    dialogMessage = "Confirm wiping all personal internal target records and raid logs? This will clean your solo player register.",
                    testTag = "purge_internal_btn"
                )
            }

            DatabaseTab.EXTERNAL -> {
                // Reminder banner
                PrivacyReminderBanner(text = "external database remains private")

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar: "search IP or wallet"
                HackerSearchField(
                    query = externalQuery,
                    onQueryChange = { viewModel.setExternalSearchQuery(it) },
                    placeholder = "search IP or wallet",
                    testTag = "search_external"
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Dual export buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HackerButton(
                        text = "export to internal",
                        onClick = {
                            viewModel.exportExternalToInternal { count ->
                                Toast.makeText(context, "Exported $count targets to Internal Database", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HackerButton(
                        text = "export to general",
                        onClick = {
                            viewModel.exportExternalToGeneral { count ->
                                Toast.makeText(context, "Exported $count targets to General Database", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Grid
                IntelGridTable(
                    targets = externalTargets,
                    sortField = sortField,
                    onToggleFw = { viewModel.toggleSortFw() },
                    onToggleAvg = { viewModel.toggleSortAvg() },
                    onSelectTarget = { viewModel.selectTargetForDossier(it) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Red Purge Button at the bottom
                RedPurgeButton(
                    text = "purge external database",
                    onPurgeConfirmed = {
                        viewModel.purgeExternalDatabase()
                        Toast.makeText(context, "External database purged", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    dialogTitle = "PURGE EXTERNAL DATABASE",
                    dialogMessage = "Confirm wiping all external victim target records and wallet maps? Solo player local storage will be cleared.",
                    testTag = "purge_external_btn"
                )
            }

            DatabaseTab.GENERAL -> {
                // Online General Database Section
                TerminalContainer(
                    title = "CREW SHARED NETWORK // SUPABASE REALTIME",
                    trailingBadge = if (isGenAuthenticated) "succeed" else "DISCONNECTED"
                ) {
                    Column {
                        // Expandable Server Crew login / configuration
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCrewLoginExpanded = !isCrewLoginExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isGenAuthenticated) Icons.Default.CheckCircle else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isGenAuthenticated) MatrixGreenPrimary else CyberAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isGenAuthenticated) "CREW AUTHENTICATED: [succeed]" else "CREW AUTHENTICATION REQUIRED",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isGenAuthenticated) MatrixGreenPrimary else CyberAmber
                                )
                            }
                            Icon(
                                imageVector = if (isCrewLoginExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MatrixGreenPrimary
                            )
                        }

                        AnimatedVisibility(visible = isCrewLoginExpanded) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    text = "Each crew connects via a shared Crew ID and Crew Secret Access Key hosted via secure online relay.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MatrixTextSecondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = crewId,
                                        onValueChange = { viewModel.setCrewCredentials(it, crewPassword) },
                                        label = { Text("CREW ID", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MatrixGreenPrimary),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MatrixDarkBackground,
                                            unfocusedContainerColor = MatrixDarkBackground,
                                            focusedBorderColor = MatrixGreenPrimary,
                                            unfocusedBorderColor = MatrixBorder
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = crewPassword,
                                        onValueChange = { viewModel.setCrewCredentials(crewId, it) },
                                        label = { Text("CREW PASSWORD", fontFamily = FontFamily.Monospace, fontSize = 10.sp) },
                                        singleLine = true,
                                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MatrixGreenPrimary),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MatrixDarkBackground,
                                            unfocusedContainerColor = MatrixDarkBackground,
                                            focusedBorderColor = MatrixGreenPrimary,
                                            unfocusedBorderColor = MatrixBorder
                                        ),
                                        modifier = Modifier.weight(1.2f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    HackerButton(
                                        text = "[CONNECT / AUTH]",
                                        onClick = {
                                            viewModel.authenticateGeneralDatabase()
                                            if (viewModel.isGeneralDbAuthenticated.value) {
                                                isCrewLoginExpanded = false
                                                Toast.makeText(context, "Connected to General Database: Access Granted", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        testTag = "crew_connect_btn"
                                    )
                                    HackerButton(
                                        text = "[CREATE SERVER CREW]",
                                        onClick = {
                                            Toast.makeText(context, "Server Crew created with ID: $crewId. Share credentials with crew operatives.", Toast.LENGTH_LONG).show()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (!isGenAuthenticated) {
                    // Show required error state when not logged in
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MatrixDarkSurfaceVariant)
                            .border(BorderStroke(1.5.dp, CyberAmber), RoundedCornerShape(4.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = CyberAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "error no general data base acces",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Expand CREW AUTHENTICATION above with your Crew ID and Secret Access Key to decrypt shared operative database.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MatrixTextSecondary
                            )
                        }
                    }
                } else {
                    // Authenticated View: Search Bar + Grid + Purge Admin
                    HackerSearchField(
                        query = generalQuery,
                        onQueryChange = { viewModel.setGeneralSearchQuery(it) },
                        placeholder = "Search IP, account, app or wallet",
                        testTag = "search_general"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    IntelGridTable(
                        targets = generalTargets,
                        sortField = sortField,
                        onToggleFw = { viewModel.toggleSortFw() },
                        onToggleAvg = { viewModel.toggleSortAvg() },
                        onSelectTarget = { viewModel.selectTargetForDossier(it) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Purge General Database [admin] at the bottom right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        RedPurgeButton(
                            text = "purge general data base [admin]",
                            onPurgeConfirmed = { showAdminPurgeDialog = true },
                            dialogTitle = "ADMIN CREDENTIAL REQUIRED",
                            dialogMessage = "Enter Crew Admin authorization key to purge the shared crew repository.",
                            testTag = "purge_general_admin_btn"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAdminPurgeDialog) {
        AlertDialog(
            onDismissRequest = { showAdminPurgeDialog = false },
            containerColor = MatrixDarkSurface,
            title = {
                Text(
                    text = "PURGE GENERAL DATABASE [ADMIN]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurgeRed
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter Master Admin Access Key to confirm general wipeout:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MatrixTextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adminPasswordAttempt,
                        onValueChange = { adminPasswordAttempt = it },
                        singleLine = true,
                        placeholder = { Text("Admin Password", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = PurgeRedText),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MatrixDarkBackground,
                            unfocusedContainerColor = MatrixDarkBackground,
                            focusedBorderColor = PurgeRed,
                            unfocusedBorderColor = MatrixBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.purgeGeneralDatabase(adminPasswordAttempt) { success ->
                            if (success) {
                                showAdminPurgeDialog = false
                                adminPasswordAttempt = ""
                                Toast.makeText(context, "General database purged by Admin", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Access Denied: Invalid Admin Password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .background(PurgeRedContainer)
                        .border(BorderStroke(1.dp, PurgeRed), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = "[AUTHORIZE PURGE]",
                        fontFamily = FontFamily.Monospace,
                        color = PurgeRedText,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminPurgeDialog = false }) {
                    Text("[CANCEL]", fontFamily = FontFamily.Monospace, color = MatrixGreenPrimary)
                }
            }
        )
    }
}

@Composable
private fun PrivacyReminderBanner(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MatrixDarkSurface)
            .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MatrixGreenSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "NOTICE // $text",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MatrixGreenSecondary
            )
        }
    }
}

@Composable
private fun DbTabButton(
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

@Composable
fun IntelGridTable(
    targets: List<TargetEntity>,
    sortField: SortField,
    onToggleFw: () -> Unit,
    onToggleAvg: () -> Unit,
    onSelectTarget: (TargetEntity) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Control Bar: Target count and Sort indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MatrixGreenGlow)
                )
                Text(
                    text = "${targets.size} TARGETS REGISTERED",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenPrimary
                )
            }

            // Quick Sort Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SORT:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MatrixGreenDim
                )
                TableHeaderCellWithSort(
                    title = "FW",
                    width = 56,
                    isSortedDesc = sortField == SortField.FW_DESC,
                    isSortedAsc = sortField == SortField.FW_ASC,
                    onToggle = onToggleFw
                )
                TableHeaderCellWithSort(
                    title = "AVG",
                    width = 62,
                    isSortedDesc = sortField == SortField.AVG_DESC,
                    isSortedAsc = sortField == SortField.AVG_ASC,
                    onToggle = onToggleAvg
                )
            }
        }

        if (targets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MatrixDarkSurface)
                    .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[EMPTY INTEL REGISTRY] Export logs or input intel to populate grid.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MatrixTextMuted
                )
            }
        } else {
            // Visual Tactical Expandable Grid (100% width, No horizontal scroll needed)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                targets.forEach { target ->
                    ExpandableTargetRow(
                        target = target,
                        onSelectTarget = { onSelectTarget(target) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandableTargetRow(
    target: TargetEntity,
    onSelectTarget: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MatrixDarkSurface)
            .border(
                BorderStroke(
                    1.dp,
                    if (isExpanded) MatrixGreenDim else MatrixBorder
                ),
                RoundedCornerShape(6.dp)
            )
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Main Top Row: IP + Name | FW + ENC + Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // IP & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MatrixGreenGlow)
                    )
                    Text(
                        text = target.ip,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixGreenPrimary
                    )
                    if (target.name.isNotBlank()) {
                        Text(
                            text = "[${target.name}]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MatrixTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // FW & ENC Badges + Chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(MatrixDarkSurfaceVariant)
                            .border(BorderStroke(1.dp, MatrixGreenDim), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "FW ${target.fw}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixGreenPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(MatrixDarkSurfaceVariant)
                            .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ENC ${target.enc}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MatrixTextPrimary
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MatrixGreenPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Second Row: Wallet + Quick Stolen Metric
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Wallet
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "WALLET:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MatrixTextMuted
                    )
                    Text(
                        text = if (target.wallet.isNotBlank() && target.wallet != "Unknown") target.wallet else "N/A",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (target.wallet.isNotBlank() && target.wallet != "Unknown" && target.wallet != "N/A") MatrixGreenDim else MatrixTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Stolen preview
                Text(
                    text = "${target.stolenCrypto} ₡",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenGlow
                )
            }

            // Expandable details section
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    HorizontalDivider(
                        color = MatrixBorder,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Secondary Specs: LVL, REP, HITS, SCORE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TargetStatChip(label = "LVL", value = target.level.toString(), modifier = Modifier.weight(1f))
                        TargetStatChip(label = "REP", value = target.rep.toString(), modifier = Modifier.weight(1f))
                        TargetStatChip(label = "HITS", value = target.hitCount.toString(), modifier = Modifier.weight(1f))
                        TargetStatChip(label = "SCORE", value = target.score.toString(), modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Loot & Activity Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MatrixDarkSurfaceVariant.copy(alpha = 0.6f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TOTAL STOLEN",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = MatrixTextMuted
                            )
                            Text(
                                text = "${target.stolenCrypto} ₡",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MatrixGreenGlow
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "AVG / HIT: ${target.avgPerHit} ₡",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MatrixTextSecondary
                            )
                            Text(
                                text = "RATE: ~${target.crPerHour} ₡/h | PEAK: ${target.peakHour}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = MatrixGreenDim
                            )
                        }
                    }

                    if (target.crew.isNotBlank() && target.crew != "Unknown" && target.crew != "None") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "CREW AFFILIATION: [${target.crew}]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MatrixGreenPrimary
                        )
                    }

                    if (target.appsParsed) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "APPS INTEL: AV:${target.antivirusLvl} | SPAM:${target.spamLvl} | FW:${target.firewallAppLvl} | PROXY:${target.proxyLvl}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MatrixTextSecondary
                        )
                    } else if (target.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "NOTES: ${target.notes}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MatrixTextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HackerButton(
                            text = "[OPEN FULL DOSSIER]",
                            onClick = onSelectTarget,
                            modifier = Modifier.weight(1f),
                            testTag = "open_dossier_${target.ip}"
                        )

                        HackerButton(
                            text = "[COPY IP]",
                            onClick = {
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Target IP", target.ip)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "IP ${target.ip} copied", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "IP: ${target.ip}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(0.6f),
                            testTag = "copy_ip_${target.ip}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MatrixDarkSurfaceVariant)
            .border(
                BorderStroke(1.dp, if (isHighlighted) MatrixGreenDim else MatrixBorder),
                RoundedCornerShape(3.dp)
            )
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MatrixTextMuted
            )
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHighlighted) MatrixGreenPrimary else MatrixTextPrimary
            )
        }
    }
}

@Composable
private fun TableHeaderCellWithSort(
    title: String,
    width: Int,
    isSortedDesc: Boolean,
    isSortedAsc: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(width.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MatrixDarkSurface)
            .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(3.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MatrixGreenPrimary
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = when {
                isSortedDesc -> "▼"
                isSortedAsc -> "▲"
                else -> "▽"
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = if (isSortedDesc || isSortedAsc) MatrixGreenGlow else MatrixGreenDim
        )
    }
}


package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MatrixBorder
import com.example.ui.theme.MatrixBorderBright
import com.example.ui.theme.MatrixDarkBackground
import com.example.ui.theme.MatrixDarkSurface
import com.example.ui.theme.MatrixDarkSurfaceVariant
import com.example.ui.theme.MatrixGreenDim
import com.example.ui.theme.MatrixGreenPrimary
import com.example.ui.theme.MatrixTextMuted
import com.example.ui.theme.MatrixTextPrimary
import com.example.ui.theme.MatrixTextSecondary
import com.example.ui.theme.PurgeRed
import com.example.ui.theme.PurgeRedContainer
import com.example.ui.theme.PurgeRedDark
import com.example.ui.theme.PurgeRedText

@Composable
fun TerminalContainer(
    title: String,
    modifier: Modifier = Modifier,
    trailingBadge: String? = null,
    borderColor: Color = MatrixBorder,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MatrixDarkSurface.copy(alpha = 0.92f))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(6.dp))
    ) {
        // Terminal Window Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MatrixDarkSurfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MatrixGreenPrimary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "> $title",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenPrimary
                )
            }
            if (trailingBadge != null) {
                Text(
                    text = "[$trailingBadge]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MatrixTextSecondary
                )
            }
        }
        Box(modifier = Modifier.padding(10.dp)) {
            content()
        }
    }
}

@Composable
fun HackerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = ""
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) MatrixDarkSurfaceVariant else MatrixDarkBackground)
            .border(
                BorderStroke(
                    1.dp,
                    if (enabled) MatrixGreenPrimary else MatrixGreenDim
                ),
                RoundedCornerShape(4.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MatrixGreenPrimary else MatrixTextMuted
        )
    }
}

@Composable
fun RedPurgeButton(
    text: String,
    onPurgeConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    dialogTitle: String = "PURGE CONFIRMATION",
    dialogMessage: String = "WARNING: This will permanently wipe selected records from database. This action cannot be reversed.",
    testTag: String = "purge_button"
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(PurgeRedContainer)
            .border(BorderStroke(1.5.dp, PurgeRed), RoundedCornerShape(4.dp))
            .clickable { showDialog = true }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Purge Warning",
                tint = PurgeRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text.lowercase(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PurgeRedText
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = MatrixDarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = PurgeRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dialogTitle,
                        fontFamily = FontFamily.Monospace,
                        color = PurgeRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = dialogMessage,
                    fontFamily = FontFamily.Monospace,
                    color = MatrixTextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onPurgeConfirmed()
                    },
                    modifier = Modifier
                        .background(PurgeRedContainer)
                        .border(BorderStroke(1.dp, PurgeRed), RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = "[CONFIRM PURGE]",
                        fontFamily = FontFamily.Monospace,
                        color = PurgeRedText,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        text = "[CANCEL]",
                        fontFamily = FontFamily.Monospace,
                        color = MatrixGreenPrimary
                    )
                }
            }
        )
    }
}

@Composable
fun TerminalLogConsole(
    logs: List<String>,
    modifier: Modifier = Modifier,
    maxHeight: Int = 160
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF020703))
            .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "root@ccc-terminal:~$",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MatrixGreenDim
            )
            Text(
                text = "STATUS: ${if (logs.isEmpty()) "IDLE" else "ACTIVE"}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MatrixGreenPrimary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(maxHeight.dp)
        ) {
            if (logs.isEmpty()) {
                item {
                    Text(
                        text = "[SYS] Ready for input stream. Awaiting logs or screenshot payload...",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MatrixTextMuted
                    )
                }
            } else {
                items(logs) { log ->
                    Text(
                        text = log,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (log.contains("ERROR", true) || log.contains("FAIL", true)) {
                            PurgeRed
                        } else if (log.contains("SUCCESS", true) || log.contains("EXTRACT", true)) {
                            MatrixGreenPrimary
                        } else {
                            MatrixTextSecondary
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HackerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "search_field"
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MatrixTextMuted
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MatrixGreenPrimary,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MatrixGreenDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        singleLine = true,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MatrixGreenPrimary
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MatrixDarkSurface,
            unfocusedContainerColor = MatrixDarkSurface,
            focusedBorderColor = MatrixGreenPrimary,
            unfocusedBorderColor = MatrixBorder,
            cursorColor = MatrixGreenPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

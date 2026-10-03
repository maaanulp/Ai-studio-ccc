package com.example.ui.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.MatrixBorder
import com.example.ui.theme.MatrixBorderBright
import com.example.ui.theme.MatrixDarkBackground
import com.example.ui.theme.MatrixDarkSurface
import com.example.ui.theme.MatrixDarkSurfaceVariant
import com.example.ui.theme.MatrixGreenGlow
import com.example.ui.theme.MatrixGreenPrimary
import com.example.ui.theme.MatrixTextMuted
import com.example.ui.theme.MatrixTextPrimary
import com.example.ui.viewmodel.IntelViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuditLogModal(
    viewModel: IntelViewModel,
    onDismiss: () -> Unit
) {
    val auditLogs by viewModel.auditLogs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(auditLogs, searchQuery) {
        if (searchQuery.isBlank()) auditLogs
        else {
            val q = searchQuery.trim().lowercase()
            auditLogs.filter { log ->
                log.operatorHandle.lowercase().contains(q) ||
                log.action.lowercase().contains(q) ||
                log.details.lowercase().contains(q) ||
                log.targetIp.lowercase().contains(q)
            }
        }
    }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatrixDarkSurface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(6.dp))
            .testTag("audit_log_modal"),
        title = {
            Column {
                Text(
                    text = "🔒 AUDITORÍA DE ACCIONES DE OPERADORES [ADMIN]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberAmber
                )
                Text(
                    text = "Registro histórico de actividad // Acceso restringido a Administrador",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MatrixTextMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Buscar por usuario, acción o IP...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MatrixTextMuted
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MatrixTextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberAmber,
                        unfocusedBorderColor = MatrixBorder,
                        focusedContainerColor = MatrixDarkBackground,
                        unfocusedContainerColor = MatrixDarkBackground
                    )
                )

                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MatrixDarkBackground)
                            .border(BorderStroke(1.dp, MatrixBorder), RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO HAY REGISTROS DE AUDITORÍA DISPONIBLES",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MatrixTextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredLogs) { log ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MatrixDarkSurfaceVariant)
                                    .border(BorderStroke(0.5.dp, MatrixBorder), RoundedCornerShape(4.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "[${log.action}]",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MatrixGreenPrimary
                                    )
                                    Text(
                                        text = dateFormat.format(Date(log.timestamp)),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = MatrixTextMuted
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Operador: ${log.operatorHandle} ${if (log.targetIp.isNotBlank()) "|| IP: ${log.targetIp}" else ""}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MatrixGreenGlow
                                )
                                if (log.details.isNotBlank()) {
                                    Text(
                                        text = log.details,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = MatrixTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HackerButton(
                    text = "LIMPIAR REGISTROS",
                    onClick = {
                        viewModel.purgeAuditLogs()
                    }
                )
                HackerButton(
                    text = "CERRAR",
                    onClick = onDismiss
                )
            }
        }
    )
}

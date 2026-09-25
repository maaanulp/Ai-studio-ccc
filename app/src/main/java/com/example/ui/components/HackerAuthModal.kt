package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.theme.MatrixGreenPrimary
import com.example.ui.theme.MatrixTextMuted
import com.example.ui.viewmodel.IntelViewModel

@Composable
fun HackerAuthModal(
    viewModel: IntelViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentProfile by viewModel.currentProfile.collectAsState()
    var operativeInput by remember { mutableStateOf(currentProfile) }
    var passcode by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatrixDarkSurface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(6.dp))
            .testTag("hacker_auth_modal"),
        title = {
            Column {
                Text(
                    text = "> OPERATIVE IDENTITY & PROFILE SETUP",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenPrimary
                )
                Text(
                    text = "Configure custom operative handle for telemetry, OCR & crew attribution",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MatrixTextMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section 1: Operative Handle input
                Text(
                    text = "ENTER OPERATIVE HANDLE:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenPrimary
                )

                OutlinedTextField(
                    value = operativeInput,
                    onValueChange = {
                        operativeInput = it
                        if (it.isNotBlank()) inputError = null
                    },
                    placeholder = {
                        Text(
                            "e.g. Viper_Null, Shadow_01, CyberGhost_88",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MatrixTextMuted
                        )
                    },
                    singleLine = true,
                    isError = inputError != null,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MatrixGreenPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MatrixDarkBackground,
                        unfocusedContainerColor = MatrixDarkBackground,
                        focusedBorderColor = MatrixGreenPrimary,
                        unfocusedBorderColor = MatrixBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("operative_handle_input")
                )

                inputError?.let { err ->
                    Text(
                        text = "[!] $err",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CyberAmber
                    )
                }

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Viper_Null", "Shadow_01", "Ghost_Sec").forEach { preset ->
                        HackerButton(
                            text = preset,
                            onClick = {
                                operativeInput = preset
                                inputError = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Section 2: Quick OAuth Sign-In
                Text(
                    text = "[QUICK OAUTH ACCESS]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberAmber
                )

                HackerButton(
                    text = "[G] SIGN IN WITH GOOGLE OAUTH",
                    onClick = {
                        val handle = operativeInput.trim()
                        if (handle.isBlank()) {
                            inputError = "Please enter an Operative Handle first."
                            return@HackerButton
                        }
                        viewModel.authenticateOAuth("Google", handle) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "oauth_google_btn"
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "───────── LOCAL IDENTITY & PASSCODE ─────────",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = MatrixTextMuted
                )

                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it },
                    label = { Text("PASSCODE (OPTIONAL)", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                    placeholder = { Text("••••••••", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MatrixTextMuted) },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MatrixGreenPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MatrixDarkBackground,
                        unfocusedContainerColor = MatrixDarkBackground,
                        focusedBorderColor = MatrixGreenPrimary,
                        unfocusedBorderColor = MatrixBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                HackerButton(
                    text = "[ESTABLISH OPERATIVE IDENTITY]",
                    onClick = {
                        val handle = operativeInput.trim()
                        if (handle.isBlank()) {
                            inputError = "Operative Handle cannot be empty."
                            return@HackerButton
                        }
                        viewModel.loginOperativeLocal(handle, passcode) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "login_operative_local_btn"
                )
            }
        },
        confirmButton = {
            HackerButton(
                text = "DISMISS",
                onClick = onDismiss
            )
        }
    )
}

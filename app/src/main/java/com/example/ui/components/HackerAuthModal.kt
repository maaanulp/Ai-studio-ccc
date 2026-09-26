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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.MatrixBorder
import com.example.ui.theme.MatrixBorderBright
import com.example.ui.theme.MatrixDarkBackground
import com.example.ui.theme.MatrixDarkSurface
import com.example.ui.theme.MatrixGreenGlow
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
    val linkedGoogleHandle = viewModel.getLinkedGoogleHandle()

    var operativeInput by remember { mutableStateOf(currentProfile.ifBlank { linkedGoogleHandle }) }
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
                    text = "Unified Authentication & Google OAuth Account Linking",
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
                if (linkedGoogleHandle.isNotBlank()) {
                    Text(
                        text = "• LINKED GOOGLE OAUTH HANDLE: [$linkedGoogleHandle]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixGreenGlow
                    )
                }

                // Section 1: Google OAuth
                Text(
                    text = "[GOOGLE OAUTH ACCESS]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberAmber
                )

                HackerButton(
                    text = "[G] CONTINUE WITH GOOGLE OAUTH",
                    onClick = {
                        val handle = operativeInput.trim()
                        viewModel.authenticateOAuth("Google", handle) { success, msg ->
                            if (!success && msg == "PROMPT_HANDLE_REQUIRED") {
                                inputError = "First-time Google OAuth access: Enter your Operative Handle below to link your account permanently."
                            } else {
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "oauth_google_btn"
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "──────── LOCAL IDENTITY (HANDLE & PASSCODE) ────────",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = MatrixTextMuted
                )

                // Mandatory Operative Handle Field
                OutlinedTextField(
                    value = operativeInput,
                    onValueChange = {
                        operativeInput = it
                        if (it.isNotBlank()) inputError = null
                    },
                    label = { Text("OPERATIVE HANDLE (ALIAS) *", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                    placeholder = {
                        Text(
                            "Enter unique handle (e.g. CyberGhost_88)",
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

                // Mandatory Passcode Field for Local Authentication
                OutlinedTextField(
                    value = passcode,
                    onValueChange = {
                        passcode = it
                        if (it.isNotBlank()) inputError = null
                    },
                    label = { Text("PASSCODE / PASSWORD *", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                    placeholder = { Text("••••••••", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MatrixTextMuted) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MatrixGreenPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MatrixDarkBackground,
                        unfocusedContainerColor = MatrixDarkBackground,
                        focusedBorderColor = MatrixGreenPrimary,
                        unfocusedBorderColor = MatrixBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("operative_passcode_input")
                )

                inputError?.let { err ->
                    Text(
                        text = "[!] $err",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CyberAmber
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HackerButton(
                        text = "[ESTABLISH LOCAL IDENTITY]",
                        onClick = {
                            val handle = operativeInput.trim()
                            val pw = passcode.trim()
                            if (handle.isBlank()) {
                                inputError = "OPERATIVE HANDLE is required."
                                return@HackerButton
                            }
                            if (pw.isBlank()) {
                                inputError = "PASSCODE / PASSWORD is required for local identity."
                                return@HackerButton
                            }
                            viewModel.loginOperativeLocal(handle, pw) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) onDismiss()
                                else inputError = msg
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "login_operative_local_btn"
                    )

                    if (operativeInput.isNotBlank()) {
                        HackerButton(
                            text = "[LINK GOOGLE OAUTH]",
                            onClick = {
                                val handle = operativeInput.trim()
                                viewModel.linkGoogleOAuthToHandle(handle) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (success) onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "link_google_oauth_btn"
                        )
                    }
                }
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

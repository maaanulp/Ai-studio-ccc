package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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

    var operativeInput by remember { mutableStateOf(currentProfile) }
    var passcode by remember { mutableStateOf("") }
    var rememberSession by remember { mutableStateOf(true) }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MatrixDarkSurface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .border(BorderStroke(1.dp, MatrixBorderBright), RoundedCornerShape(6.dp))
            .testTag("hacker_auth_modal"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_ccc_launcher_icon),
                    contentDescription = "Crypt0 Cr3w Central Emblem",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .border(BorderStroke(1.dp, MatrixGreenPrimary), androidx.compose.foundation.shape.CircleShape)
                )
                Column {
                    Text(
                        text = "> OPERATIVE ACCESS & REGISTRATION",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixGreenPrimary
                    )
                    Text(
                        text = "Crypt0 Cr3w Central [CCC] // Supabase Sync",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = MatrixTextMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentProfile.isNotBlank() && !currentProfile.equals("Unassigned", ignoreCase = true)) {
                    Text(
                        text = "• ACTIVE OPERATIVE: [$currentProfile]",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MatrixGreenGlow
                    )
                }

                // Username / Operative Handle Input
                OutlinedTextField(
                    value = operativeInput,
                    onValueChange = {
                        operativeInput = it
                        if (it.isNotBlank()) inputError = null
                    },
                    label = { Text("USERNAME / OPERATIVE HANDLE *", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                    placeholder = {
                        Text(
                            "Operative username (e.g. Operative_01)",
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

                // Password Input
                OutlinedTextField(
                    value = passcode,
                    onValueChange = {
                        passcode = it
                        if (it.isNotBlank()) inputError = null
                    },
                    label = { Text("PASSWORD *", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
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

                // "Remember session" Checkbox Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberSession = !rememberSession }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberSession,
                        onCheckedChange = { rememberSession = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MatrixGreenPrimary,
                            uncheckedColor = MatrixBorder,
                            checkmarkColor = MatrixDarkBackground
                        )
                    )
                    Text(
                        text = "Remember session (Keep access active)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MatrixGreenPrimary
                    )
                }

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
                        text = "[LOGIN]",
                        onClick = {
                            val handle = operativeInput.trim()
                            val pw = passcode.trim()
                            if (handle.isBlank()) {
                                inputError = "Username is required."
                                return@HackerButton
                            }
                            if (pw.isBlank()) {
                                inputError = "Password is required."
                                return@HackerButton
                            }
                            viewModel.loginOperativeLocal(handle, pw, rememberSession = rememberSession) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) onDismiss()
                                else inputError = msg
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "login_operative_local_btn"
                    )

                    HackerButton(
                        text = "[REGISTER]",
                        onClick = {
                            val handle = operativeInput.trim()
                            val pw = passcode.trim()
                            if (handle.isBlank() || pw.isBlank()) {
                                inputError = "Username and password are required for registration."
                                return@HackerButton
                            }
                            viewModel.loginOperativeLocal(handle, pw, rememberSession = rememberSession) { success, msg ->
                                Toast.makeText(context, "Operative $handle registered and synced with Supabase.", Toast.LENGTH_SHORT).show()
                                if (success) onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "register_operative_local_btn"
                    )
                }
            }
        },
        confirmButton = {
            HackerButton(
                text = "[CLOSE]",
                onClick = onDismiss
            )
        }
    )
}

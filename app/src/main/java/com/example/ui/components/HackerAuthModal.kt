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
                    text = "> OPERATOR AUTHENTICATOR",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MatrixGreenPrimary
                )
                Text(
                    text = "Authenticate personal operative identity",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MatrixTextMuted
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section 1: Quick OAuth Sign-In
                Text(
                    text = "[QUICK OAUTH ACCESS]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberAmber
                )
                HackerButton(
                    text = "[G] GOOGLE OAUTH",
                    onClick = {
                        val operativeAlias = operativeInput.trim().ifBlank { "CyberGhost_88" }
                        viewModel.authenticateOAuth("Google", operativeAlias) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "oauth_google_btn"
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "───────── LOCAL OPERATIVE IDENTITY ─────────",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MatrixTextMuted
                )

                OutlinedTextField(
                    value = operativeInput,
                    onValueChange = { operativeInput = it },
                    label = { Text("OPERATIVE USERNAME / ALIAS", fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                    placeholder = { Text("Shadow_Operative", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = MatrixTextMuted) },
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
                    text = "[LOG IN OPERATIVE SESSION]",
                    onClick = {
                        viewModel.loginOperativeLocal(operativeInput, passcode) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (success) onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "login_operative_btn"
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            HackerButton(
                text = "[CLOSE]",
                onClick = onDismiss,
                testTag = "modal_close_btn"
            )
        }
    )
}

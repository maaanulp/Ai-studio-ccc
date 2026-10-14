package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppDatabase
import com.example.data.repository.IntelRepository
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.IntelViewModel

import com.example.util.configureAppCheck

enum class NavigationScreen(val title: String, val icon: ImageVector) {
    DATABASE("INTEL DB", Icons.Default.Storage),
    PARSER("LOGS", Icons.Default.Terminal),
    OCR("VISION OCR", Icons.Default.DocumentScanner),
    METRICS("TELEMETRY", Icons.Default.BarChart),
    FEED("FEED", Icons.Default.Forum)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Step 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = IntelRepository(
            targetDao = db.targetDao(),
            reportDao = db.intelligenceReportDao(),
            logEntryDao = db.logEntryDao(),
            ocrResultDao = db.ocrTextResultDao(),
            feedDao = db.feedDao(),
            auditLogDao = db.auditLogDao()
        )
        val viewModel = IntelViewModel(application, repository)

        setContent {
            Crypt0Cr3wTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

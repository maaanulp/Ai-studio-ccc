package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.IntelRepository
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.IntelViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: IntelViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = AppDatabase.getInstance(applicationContext)
                val repository = IntelRepository(
                    targetDao = database.targetDao(),
                    reportDao = database.intelligenceReportDao(),
                    logEntryDao = database.logEntryDao(),
                    ocrResultDao = database.ocrTextResultDao()
                )
                return IntelViewModel(repository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.material3.Text(
        text = "Crypt0 Cr3w Central: Welcome $name",
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        color = com.example.ui.theme.MatrixGreenPrimary,
        modifier = modifier
    )
}

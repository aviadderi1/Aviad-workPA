package com.aviad.workpa

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviad.workpa.data.Job
import com.aviad.workpa.ui.Amber
import com.aviad.workpa.ui.ExportDialog
import com.aviad.workpa.ui.JobEditor
import com.aviad.workpa.ui.MainScreen
import com.aviad.workpa.ui.Muted
import com.aviad.workpa.ui.SettingsScreen
import com.aviad.workpa.ui.WorkPATheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = AppState(applicationContext)
        setContent {
            WorkPATheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(state)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // pick up changes made in the calendar app while we were away
        if (::state.isInitialized) state.resumeTick++
    }
}

private sealed interface Editing {
    data object New : Editing
    data class Existing(val job: Job) : Editing
}

@Composable
private fun App(state: AppState) {
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { state.reload() }
    }

    LaunchedEffect(state.month, state.resumeTick) { state.reload() }
    LaunchedEffect(state.resumeTick) { if (state.updateProgress == null) state.checkUpdate() }

    if (!state.hasPermission) {
        PermissionScreen {
            permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
        return
    }

    if (settings) {
        BackHandler { settings = false; scope.launch { state.reload() } }
        SettingsScreen(state, onBack = { settings = false; scope.launch { state.reload() } })
        return
    }

    MainScreen(
        state = state,
        onPrevMonth = { state.month = state.month.minusMonths(1) },
        onNextMonth = { state.month = state.month.plusMonths(1) },
        onSync = { scope.launch { state.reload() } },
        onAdd = { editing = Editing.New },
        onOpenJob = { editing = Editing.Existing(it) },
        onExport = { exporting = true },
        onSettings = { settings = true },
    )

    editing?.let { e ->
        JobEditor(
            state = state,
            job = (e as? Editing.Existing)?.job,
            onDismiss = { editing = null },
            onSaved = { editing = null; scope.launch { state.reload() } },
        )
    }
    if (exporting) ExportDialog(state, onDismiss = { exporting = false })
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aviad workPA", color = Amber, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "כדי לסנכרן את העבודות עם Google Calendar, האפליקציה צריכה גישה ליומן בטלפון.",
            textAlign = TextAlign.Center, color = Muted,
        )
        Button(
            onClick = onRequest,
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1A1A)),
        ) { Text("אפשר גישה ליומן", fontWeight = FontWeight.Bold) }
    }
}

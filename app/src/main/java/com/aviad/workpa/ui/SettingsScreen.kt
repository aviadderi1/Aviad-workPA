package com.aviad.workpa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviad.workpa.AppState
import com.aviad.workpa.data.Employer
import com.aviad.workpa.update.Updater
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit) {
    val s = state.settings
    var reportName by remember { mutableStateOf(s.reportName) }
    var keywords by remember { mutableStateOf(s.keywords.joinToString(", ")) }
    var types by remember { mutableStateOf(s.jobTypes.joinToString(", ")) }
    var calendarId by remember { mutableStateOf(state.repo.defaultCalendarId(s.calendarId)) }
    var addEmployer by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Employer?>(null) }
    var deleting by remember { mutableStateOf<Employer?>(null) }

    fun split(t: String) = t.split(",", "،").map { it.trim() }.filter { it.isNotEmpty() }
    fun persist() {
        state.saveSettings(
            s.copy(
                reportName = reportName.trim(),
                keywords = split(keywords).ifEmpty { listOf("הגברה", "סאונד") },
                jobTypes = split(types).ifEmpty { listOf("הגברה") },
                calendarId = calendarId,
            )
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { persist(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
            Text("הגדרות", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("מעסיקים")
            state.employers.forEach { e ->
                Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(10.dp)).padding(start = 12.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(employerColor(e.color), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(e.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { renaming = e }) { Icon(Icons.Filled.Edit, "שנה שם") }
                        IconButton(onClick = { deleting = e }) { Icon(Icons.Filled.Delete, "מחק") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EmployerPalette.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(c)
                                    .border(if (i == e.color) 3.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { state.setEmployerColor(e.id, i) }
                            )
                        }
                    }
                }
            }
            OutlinedButton(onClick = { addEmployer = true }, modifier = Modifier.fillMaxWidth()) {
                Text("+ הוסף מעסיק")
            }

            Section("דוח PDF")
            OutlinedTextField(value = reportName, onValueChange = { reportName = it },
                label = { Text("השם שלך בדוח") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Section("זיהוי עבודות ביומן")
            OutlinedTextField(value = keywords, onValueChange = { keywords = it },
                label = { Text("מילים בכותרת (מופרדות בפסיק)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = types, onValueChange = { types = it },
                label = { Text("סוגי עבודה בהוספה (מופרדים בפסיק)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("עבודה חדשה נרשמת ביומן עם סוג העבודה בכותרת, כדי שתזוהה אוטומטית.", fontSize = 12.sp, color = Muted)

            Section("יומן לרישום עבודות חדשות")
            val cals = state.calendars.filter { it.writable }
            if (cals.isEmpty()) Text("לא נמצאו יומנים", color = Muted)
            cals.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { calendarId = c.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = calendarId == c.id, onClick = { calendarId = c.id },
                        colors = RadioButtonDefaults.colors(selectedColor = Amber))
                    Column {
                        Text(c.name)
                        Text(c.account, fontSize = 12.sp, color = Muted)
                    }
                }
            }
            OutlinedButton(onClick = { persist(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("שמור וחזור") }

            Section("עדכונים")
            val ctx = LocalContext.current
            val scope = rememberCoroutineScope()
            Text("גרסה מותקנת: ${Updater.installedVersion(ctx)}", fontSize = 13.sp, color = Muted)
            if (state.update != null) {
                UpdateBanner(state)
            } else {
                OutlinedButton(
                    onClick = { state.updateMsg = null; scope.launch { state.checkUpdate(manual = true) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("בדוק עדכונים") }
                state.updateMsg?.let { Text(it, fontSize = 12.sp, color = Muted) }
            }

            Text("Aviad workPA · AD SOUND & SERVICE", fontSize = 11.sp, color = Muted,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
    }

    if (addEmployer) {
        NameDialog("מעסיק חדש", "", onDismiss = { addEmployer = false }) { state.addEmployer(it); addEmployer = false }
    }
    renaming?.let { e ->
        NameDialog("שינוי שם", e.name, onDismiss = { renaming = null }) { state.renameEmployer(e.id, it); renaming = null }
    }
    deleting?.let { e ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("למחוק את ${e.name}?") },
            text = { Text("העבודות שלו יישארו, בלי שיוך למעסיק.") },
            confirmButton = {
                TextButton(onClick = { state.deleteEmployer(e.id); deleting = null }) {
                    Text("מחק", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun Section(t: String) = Text(t, color = Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp)

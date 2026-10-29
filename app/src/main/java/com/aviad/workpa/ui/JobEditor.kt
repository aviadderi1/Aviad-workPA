package com.aviad.workpa.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aviad.workpa.AppState
import com.aviad.workpa.data.Job
import com.aviad.workpa.data.JobMeta
import com.aviad.workpa.data.dateText
import com.aviad.workpa.data.localDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Add a new job (creates a calendar event) or edit an existing one.
 * [job] == null → new job.
 */
@Composable
fun JobEditor(state: AppState, job: Job?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val isNew = job == null
    val ev = job?.event
    val recurring = ev?.recurring == true
    val allDay = ev?.allDay == true
    val tf = DateTimeFormatter.ofPattern("HH:mm")

    val types = state.settings.jobTypes.ifEmpty { state.settings.keywords }
    var type by remember { mutableStateOf(types.firstOrNull() ?: "הגברה") }
    var title by remember { mutableStateOf(ev?.title ?: "") }
    var date by remember {
        mutableStateOf(ev?.localDate() ?: state.month.let {
            if (it == java.time.YearMonth.now()) LocalDate.now() else it.atDay(1)
        })
    }
    var start by remember {
        mutableStateOf(ev?.let { Instant.ofEpochMilli(it.begin).atZone(zone).toLocalTime() } ?: LocalTime.of(18, 0))
    }
    var end by remember {
        mutableStateOf(ev?.let { Instant.ofEpochMilli(it.end).atZone(zone).toLocalTime() } ?: LocalTime.of(23, 0))
    }
    var location by remember { mutableStateOf(ev?.location ?: "") }
    var employerId by remember { mutableStateOf(job?.meta?.employerId ?: state.filter?.takeIf { f -> state.employers.any { it.id == f } }) }
    var amount by remember { mutableStateOf(job?.meta?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var paid by remember { mutableStateOf(job?.meta?.paid ?: false) }
    var notes by remember { mutableStateOf(job?.meta?.notes ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newEmployerDialog by remember { mutableStateOf(false) }

    fun save() {
        val amountVal = amount.trim().replace(",", "").takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (amount.isNotBlank() && amountVal == null) { err = "סכום לא תקין"; return }
        val kws = state.settings.keywords
        var finalTitle = title.trim()
        if (kws.none { finalTitle.contains(it, ignoreCase = true) }) {
            finalTitle = if (finalTitle.isEmpty()) type else "$type – $finalTitle"
        }
        val beginMs = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        var endDt = date.atTime(end)
        if (!end.isAfter(start)) endDt = endDt.plusDays(1)       // gig past midnight
        val endMs = endDt.atZone(zone).toInstant().toEpochMilli()

        try {
            if (isNew) {
                val calId = state.repo.defaultCalendarId(state.settings.calendarId)
                if (calId == null) { err = "לא נמצא יומן Google בטלפון לכתיבה"; return }
                val id = state.repo.insertEvent(calId, finalTitle, beginMs, endMs, location.trim(), notes.trim())
                if (id == null) { err = "שמירה ליומן נכשלה"; return }
                state.store.saveMeta(JobMeta("$id", employerId, amountVal, paid, notes.trim()))
            } else {
                val e = ev!!
                val changed = finalTitle != e.title || location.trim() != e.location ||
                    (!allDay && (beginMs != e.begin || endMs != e.end))
                if (changed && !recurring) {
                    if (allDay) state.repo.updateEvent(e.eventId, finalTitle, e.begin, e.end, location.trim())
                    else state.repo.updateEvent(e.eventId, finalTitle, beginMs, endMs, location.trim())
                }
                state.store.saveMeta(JobMeta(e.key, employerId, amountVal, paid, notes.trim()))
            }
            onSaved()
        } catch (ex: Exception) {
            err = "שגיאה: ${ex.message}"
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "סגור") }
                Text(if (isNew) "עבודה חדשה" else "עריכת עבודה", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isNew) {
                    Label("סוג עבודה")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        types.forEach { t -> Chip(t, t == type) { type = t } }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(if (isNew) "שם האירוע (חתונה, הופעה...)" else "כותרת ביומן") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    enabled = !recurring,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerField("תאריך", dateText(date), Modifier.weight(1f), enabled = !recurring && !allDay) {
                        DatePickerDialog(ctx, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                            date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }
                }
                if (!allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerField("התחלה", start.format(tf), Modifier.weight(1f), enabled = !recurring) {
                            TimePickerDialog(ctx, { _, h, mi -> start = LocalTime.of(h, mi) }, start.hour, start.minute, true).show()
                        }
                        PickerField("סיום", end.format(tf), Modifier.weight(1f), enabled = !recurring) {
                            TimePickerDialog(ctx, { _, h, mi -> end = LocalTime.of(h, mi) }, end.hour, end.minute, true).show()
                        }
                    }
                }
                if (recurring) {
                    Text("אירוע חוזר – את התאריך והשעות משנים ביומן עצמו", fontSize = 12.sp, color = Muted)
                }
                OutlinedTextField(
                    value = location, onValueChange = { location = it }, label = { Text("מיקום") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !recurring,
                )

                Label("מעסיק")
                EmployerPicker(state, employerId, onPick = { employerId = it }, onNew = { newEmployerDialog = true })

                OutlinedTextField(
                    value = amount, onValueChange = { amount = it; err = null },
                    label = { Text("סכום (₪)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("שולם", modifier = Modifier.weight(1f))
                    Switch(checked = paid, onCheckedChange = { paid = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Amber, checkedThumbColor = Color(0xFF1A1A1A)))
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it }, label = { Text("הערות") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )
                if (isNew) {
                    Text("העבודה תירשם גם ביומן Google שלך", fontSize = 12.sp, color = Muted)
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!isNew && !recurring) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("מחק את העבודה מהיומן", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("ביטול") }
                Button(
                    onClick = { save() }, modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1A1A)),
                ) { Text("שמור", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (newEmployerDialog) {
        NameDialog("מעסיק חדש", "", onDismiss = { newEmployerDialog = false }) { name ->
            employerId = state.addEmployer(name).id
            newEmployerDialog = false
        }
    }

    if (confirmDelete && ev != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("למחוק את העבודה?") },
            text = { Text("האירוע \"${ev.title}\" יימחק גם מהיומן שלך.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        state.repo.deleteEvent(ev.eventId)
                        state.store.deleteMeta(ev.key)
                        confirmDelete = false
                        onSaved()
                    } catch (ex: Exception) {
                        err = "מחיקה נכשלה: ${ex.message}"; confirmDelete = false
                    }
                }) { Text("מחק", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ביטול") } },
        )
    }
}

@Composable
fun Label(t: String) = Text(t, fontSize = 13.sp, color = Muted)

@Composable
private fun PickerField(label: String, value: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, fontSize = 11.sp, color = Muted)
            Text(value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun EmployerPicker(state: AppState, selected: String?, onPick: (String?) -> Unit, onNew: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(state.employerName(selected), modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("ללא מעסיק") }, onClick = { onPick(null); open = false })
            state.employers.forEach { e ->
                DropdownMenuItem(text = { Text(e.name) }, onClick = { onPick(e.id); open = false })
            }
            if (onNew != null) {
                DropdownMenuItem(text = { Text("+ מעסיק חדש", color = Amber) }, onClick = { open = false; onNew() })
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("שם") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

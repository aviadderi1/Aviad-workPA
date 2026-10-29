package com.aviad.workpa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviad.workpa.AppState
import com.aviad.workpa.NO_EMPLOYER
import com.aviad.workpa.data.Job
import com.aviad.workpa.data.hoursText
import com.aviad.workpa.data.localDate
import com.aviad.workpa.data.money
import com.aviad.workpa.data.monthTitle
import com.aviad.workpa.data.dateText
import com.aviad.workpa.data.dayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(
    state: AppState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSync: () -> Unit,
    onAdd: () -> Unit,
    onOpenJob: (Job) -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit,
    onPayslips: () -> Unit,
) {
    val visible = state.visibleJobs()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = Amber,
                contentColor = Color(0xFF1A1A1A),
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("הוסף עבודה", fontWeight = FontWeight.Bold) },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aviad workPA", color = Amber, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onPayslips) { Text("תלושים") }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "הגדרות") }
                }
            }
            if (state.update != null || state.updateProgress != null) {
                item { UpdateBanner(state) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onPrevMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "חודש קודם")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthTitle(state.month), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        val sync = state.lastSync?.let {
                            "סונכרן ב-" + SimpleDateFormat("HH:mm", Locale.US).format(Date(it))
                        } ?: "לא סונכרן"
                        Text(sync, fontSize = 12.sp, color = Muted)
                    }
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "חודש הבא")
                    }
                }
            }
            item { EmployerChips(state) }
            item { Summary(visible) }
            state.error?.let { err ->
                item { Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSync, modifier = Modifier.weight(1f)) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("סנכרון יומן")
                    }
                    Button(
                        onClick = onExport,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1A1A)),
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("דוח PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (visible.isEmpty() && !state.loading) {
                item {
                    val kws = state.settings.keywords.joinToString(" / ") { "\"$it\"" }
                    Text(
                        "אין עבודות בחודש הזה.\nאירועים ביומן עם $kws בכותרת יופיעו כאן אוטומטית.",
                        color = Muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            }
            items(visible, key = { it.key }) { job ->
                JobRow(job, state.employerName(job.meta.employerId),
                    color = employerColor(state.employerColorIndex(job.meta.employerId)),
                    onClick = { onOpenJob(job) }, onTogglePaid = { state.togglePaid(job) })
            }
        }
    }
}

@Composable
private fun EmployerChips(state: AppState) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Chip("כולם", state.filter == null) { state.filter = null }
        state.employers.forEach { e ->
            Chip(e.name, state.filter == e.id, dot = employerColor(e.color)) { state.filter = e.id }
        }
        if (state.jobs.any { j -> j.meta.employerId == null || state.employers.none { it.id == j.meta.employerId } }) {
            Chip("ללא מעסיק", state.filter == NO_EMPLOYER, dot = NoEmployerColor) { state.filter = NO_EMPLOYER }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val accent = dot ?: Amber
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent, shape)
            .border(1.dp, if (selected) accent else Color(0xFF444444), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(10.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (selected) (if (dot == null) Amber else Color.White) else Color(0xFFDDDDDD), fontSize = 13.sp)
    }
}

@Composable
private fun Summary(jobs: List<Job>) {
    val total = jobs.sumOf { it.meta.amount ?: 0.0 }
    val paid = jobs.filter { it.meta.paid }.sumOf { it.meta.amount ?: 0.0 }
    val missing = jobs.count { it.meta.amount == null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("עבודות החודש", "${jobs.size}", Color.White, Modifier.weight(1f))
            Stat("סה״כ הכנסות", money(total), Color.White, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("שולם", money(paid), Green, Modifier.weight(1f))
            Stat("ממתין לתשלום", money(total - paid), Warn, Modifier.weight(1f))
        }
        if (missing > 0) {
            Text("ל-$missing עבודות עוד לא הוזן סכום", color = Warn, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.background(Card, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(label, fontSize = 12.sp, color = Muted)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun JobRow(job: Job, employer: String, color: Color, onClick: () -> Unit, onTogglePaid: () -> Unit) {
    val d = job.event.localDate()
    Row(
        Modifier.height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f).compositeOver(Card)).clickable(onClick = onClick)
    ) {
    Box(Modifier.width(6.dp).fillMaxHeight().background(color))
    Column(Modifier.weight(1f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${dayName(d)}, ${dateText(d)} · ${job.event.hoursText()}",
                    fontSize = 12.sp, color = Amber, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(job.event.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row {
                    Text(employer, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (job.event.location.isNotBlank()) {
                        Text(" · ${job.event.location}", fontSize = 12.sp, color = Muted, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val amount = job.meta.amount
                if (amount == null) {
                    Text("הוסף סכום", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(money(amount), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    val (bg, fg, t) = if (job.meta.paid) Triple(GreenBg, Green, "שולם") else Triple(WarnBg, Warn, "ממתין")
                    Box(
                        Modifier.background(bg, RoundedCornerShape(6.dp)).clickable(onClick = onTogglePaid)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text(t, color = fg, fontSize = 11.sp) }
                }
            }
        }
        if (job.meta.notes.isNotBlank()) {
            HorizontalDivider(color = Color(0xFF2E2E2E))
            Text(job.meta.notes, fontSize = 12.sp, color = Muted, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    }
}

package com.aviad.workpa.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.aviad.workpa.AppState
import com.aviad.workpa.NO_EMPLOYER
import com.aviad.workpa.data.Job
import com.aviad.workpa.data.money
import com.aviad.workpa.data.monthTitle
import com.aviad.workpa.pdf.ReportPdf
import java.io.File

private const val ALL = "__all__"

@Composable
fun ExportDialog(state: AppState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    // default: the employer currently filtered, else the first employer that has jobs
    var target by remember {
        mutableStateOf(
            state.filter
                ?: state.employers.firstOrNull { e -> state.jobs.any { it.meta.employerId == e.id } }?.id
                ?: ALL
        )
    }
    var showAmounts by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    fun jobsFor(t: String): List<Job> = when (t) {
        ALL -> state.jobs
        NO_EMPLOYER -> state.jobs.filter { j -> j.meta.employerId == null || state.employers.none { it.id == j.meta.employerId } }
        else -> state.jobs.filter { it.meta.employerId == t }
    }
    fun targetName(t: String) = when (t) {
        ALL -> "כל המעסיקים"
        NO_EMPLOYER -> "ללא מעסיק"
        else -> state.employerName(t)
    }

    val selected = jobsFor(target)
    val fileName = "דוח_${targetName(target).replace(" ", "_")}_${state.month}.pdf"

    fun writeTo(out: java.io.OutputStream) = ReportPdf.write(
        out, state.month, targetName(target), state.settings.reportName, selected, showAmounts,
    )

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { writeTo(it) }
                msg = "הדוח נשמר"
            } catch (e: Exception) { msg = "שמירה נכשלה: ${e.message}" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("דוח PDF – ${monthTitle(state.month)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("מעסיק")
                Box {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(targetName(target), modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        (state.employers.map { it.id } + NO_EMPLOYER + ALL).forEach { id ->
                            val n = jobsFor(id).size
                            if (n > 0 || id == ALL || state.employers.any { it.id == id }) {
                                DropdownMenuItem(
                                    text = { Text("${targetName(id)} ($n)") },
                                    onClick = { target = id; menu = false },
                                )
                            }
                        }
                    }
                }
                val total = selected.sumOf { it.meta.amount ?: 0.0 }
                Text("${selected.size} עבודות · ${money(total)}", color = Muted, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showAmounts, onCheckedChange = { showAmounts = it },
                        colors = CheckboxDefaults.colors(checkedColor = Amber, checkmarkColor = Color(0xFF1A1A1A)))
                    Text("הצג סכומים בדוח")
                }
                if (selected.any { it.meta.amount == null } && showAmounts) {
                    Text("שים לב: לחלק מהעבודות לא הוזן סכום", color = Warn, fontSize = 12.sp)
                }
                msg?.let { Text(it, color = Amber) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (selected.isEmpty()) msg = "אין עבודות לדוח" else saveLauncher.launch(fileName) },
                        modifier = Modifier.weight(1f),
                    ) { Text("שמור") }
                    Button(
                        onClick = {
                            if (selected.isEmpty()) { msg = "אין עבודות לדוח"; return@Button }
                            try { share(ctx, fileName) { writeTo(it) } } catch (e: Exception) { msg = "שגיאה: ${e.message}" }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1A1A)),
                    ) { Text("שלח", fontWeight = FontWeight.Bold) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = MaterialTheme.colorScheme.surface,
    )
}

private fun share(ctx: Context, fileName: String, write: (java.io.OutputStream) -> Unit) {
    val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
    val file = File(dir, fileName)
    file.outputStream().use(write)
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, "שליחת הדוח"))
}

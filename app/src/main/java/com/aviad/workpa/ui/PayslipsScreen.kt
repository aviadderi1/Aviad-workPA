package com.aviad.workpa.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.aviad.workpa.AppState
import com.aviad.workpa.data.dateText
import com.aviad.workpa.data.monthTitle
import com.aviad.workpa.drive.DriveClient
import com.aviad.workpa.drive.GoogleAuth
import com.aviad.workpa.drive.ImageToPdf
import com.aviad.workpa.drive.Payslip
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

@Composable
fun PayslipsScreen(state: AppState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("workpa", Context.MODE_PRIVATE) }

    var connected by remember { mutableStateOf(prefs.getBoolean("driveConnected", false)) }
    var slips by remember { mutableStateOf<List<Payslip>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }      // text shown while working
    var msg by remember { mutableStateOf<String?>(null) }
    var year by remember { mutableStateOf(YearMonth.now().year) }
    var uploadFor by remember { mutableStateOf<YearMonth?>(null) }
    var deleting by remember { mutableStateOf<Payslip?>(null) }
    var pending by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }
    var saveSlip by remember { mutableStateOf<Payslip?>(null) }

    fun authError(e: Exception) {
        busy = null
        val code = (e as? ApiException)?.statusCode
        msg = when (code) {
            10 -> "החיבור ל-Google עוד לא הוגדר (קוד 10). צריך להשלים את ההגדרה ב-Google Cloud."
            12501, 16 -> "החיבור בוטל"
            else -> "החיבור ל-Google נכשל: ${e.message}"
        }
    }

    suspend fun runAction(token: String, action: suspend (String) -> Unit) {
        try {
            action(token)
            if (!connected) { connected = true; prefs.edit().putBoolean("driveConnected", true).apply() }
        } catch (e: Exception) {
            msg = e.message ?: "שגיאה"
        } finally {
            busy = null
        }
    }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val action = pending
        pending = null
        try {
            val token = GoogleAuth.fromIntent(ctx, res.data).accessToken
            if (token == null || action == null) { busy = null; msg = "החיבור בוטל"; return@rememberLauncherForActivityResult }
            scope.launch { runAction(token, action) }
        } catch (e: Exception) { authError(e) }
    }

    /** Get a Drive token (asking the user the first time), then run [action]. */
    fun withDrive(working: String, action: suspend (String) -> Unit) {
        busy = working
        msg = null
        scope.launch {
            try {
                val r = GoogleAuth.authorize(ctx)
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    pending = action
                    authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    runAction(r.accessToken ?: error("אין הרשאה"), action)
                }
            } catch (e: Exception) { authError(e) }
        }
    }

    fun refresh() = withDrive("טוען תלושים…") { t -> slips = DriveClient(t).list() }

    fun openSlip(slip: Payslip) = withDrive("פותח…") { t ->
        val f = DriveClient(t).download(slip, File(ctx.cacheDir, "payslips/${slip.id}.pdf"))
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, slip.mimeType.ifBlank { "application/pdf" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val slip = saveSlip
        saveSlip = null
        if (uri != null && slip != null) {
            withDrive("מוריד…") { t ->
                val f = DriveClient(t).download(slip, File(ctx.cacheDir, "payslips/${slip.id}.pdf"))
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
                }
                msg = "התלוש נשמר בטלפון"
            }
        }
    }

    LaunchedEffect(Unit) { if (connected) refresh() }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה") }
            Text("תלושי משכורת", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (connected) IconButton(onClick = { refresh() }) { Icon(Icons.Filled.Refresh, "רענן") }
        }

        if (!connected) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("התלושים נשמרים ב-Google Drive שלך, בתיקייה \"${DriveClient.FOLDER}\".",
                    textAlign = TextAlign.Center)
                Text("האפליקציה רואה רק את הקבצים שהיא עצמה העלתה.", color = Muted, fontSize = 13.sp,
                    textAlign = TextAlign.Center)
                Button(
                    onClick = { refresh() },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1A1A)),
                ) { Text("חבר את Google Drive", fontWeight = FontWeight.Bold) }
                busy?.let { Status(it) }
                msg?.let { Text(it, color = Warn, textAlign = TextAlign.Center) }
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "שנה קודמת") }
            Text("$year", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { if (year < YearMonth.now().year) year++ }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "שנה הבאה")
            }
        }
        busy?.let { Box(Modifier.padding(horizontal = 16.dp)) { Status(it) } }
        msg?.let { Text(it, color = Warn, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }

        val now = YearMonth.now()
        val lastMonth = if (year == now.year) now.monthValue else 12
        val months = (lastMonth downTo 1).map { YearMonth.of(year, it) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(months, key = { it.toString() }) { m ->
                val list = slips?.filter { it.month == m.toString() }.orEmpty()
                MonthCard(
                    month = m, slips = list, loaded = slips != null, state = state,
                    onUpload = { uploadFor = m },
                    onOpen = { openSlip(it) },
                    onSave = { saveSlip = it; saveLauncher.launch(it.name) },
                    onDelete = { deleting = it },
                )
            }
        }
        Button(
            onClick = { uploadFor = YearMonth.now() },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1A1A)),
        ) { Text("העלאת תלוש", fontWeight = FontWeight.Bold) }
    }

    uploadFor?.let { m ->
        UploadDialog(
            state = state, initialMonth = m,
            onDismiss = { uploadFor = null },
            onUpload = { month, employerId, source, isImage ->
                uploadFor = null
                val emp = state.employers.first { it.id == employerId }
                val fileName = "תלוש_${emp.name.replace(" ", "_")}_$month.pdf"
                withDrive("מעלה תלוש…") { t ->
                    val client = DriveClient(t)
                    val existing = slips ?: client.list()
                    val stream = withContext(Dispatchers.IO) {
                        if (isImage) ImageToPdf.convert(ctx, source, File(ctx.cacheDir, "payslips/upload.pdf")).inputStream()
                        else ctx.contentResolver.openInputStream(source) ?: error("לא ניתן לקרוא את הקובץ")
                    }
                    client.upload(month.toString(), emp.id, emp.name, fileName, "application/pdf", stream, existing)
                    slips = client.list()
                    msg = "התלוש של ${emp.name} ל${monthTitle(month)} הועלה"
                }
            },
        )
    }

    deleting?.let { slip ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("למחוק את התלוש?") },
            text = { Text("${slip.employerName} · ${monthTitle(YearMonth.parse(slip.month))}\nהתלוש יימחק מ-Google Drive.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    withDrive("מוחק…") { t ->
                        val c = DriveClient(t)
                        c.delete(slip.id)
                        slips = c.list()
                    }
                }) { Text("מחק", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun Status(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Amber)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun MonthCard(
    month: YearMonth,
    slips: List<Payslip>,
    loaded: Boolean,
    state: AppState,
    onUpload: () -> Unit,
    onOpen: (Payslip) -> Unit,
    onSave: (Payslip) -> Unit,
    onDelete: (Payslip) -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(monthTitle(month), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (loaded && slips.isEmpty()) Text("חסר תלוש", color = Warn, fontSize = 12.sp)
            TextButton(onClick = onUpload) { Text("+ העלה", color = Amber) }
        }
        slips.sortedBy { it.employerName }.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(employerColor(state.employerColorIndex(s.employerId)), CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.employers.firstOrNull { it.id == s.employerId }?.name ?: s.employerName, fontSize = 14.sp)
                    Text("הועלה ${dateText(s.uploaded)}", fontSize = 11.sp, color = Muted)
                }
                TextButton(onClick = { onOpen(s) }) { Text("פתח") }
                TextButton(onClick = { onSave(s) }) { Text("הורד") }
                IconButton(onClick = { onDelete(s) }) { Icon(Icons.Filled.Delete, "מחק", tint = Muted) }
            }
        }
    }
}

@Composable
private fun UploadDialog(
    state: AppState,
    initialMonth: YearMonth,
    onDismiss: () -> Unit,
    onUpload: (YearMonth, String, Uri, Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    var month by remember { mutableStateOf(initialMonth) }
    var employerId by remember {
        mutableStateOf(state.filter?.takeIf { f -> state.employers.any { it.id == f } } ?: state.employers.firstOrNull()?.id)
    }
    var source by remember { mutableStateOf<Uri?>(null) }
    var isImage by remember { mutableStateOf(false) }
    var sourceLabel by remember { mutableStateOf<String?>(null) }
    var monthMenu by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val cameraUri = remember {
        val f = File(ctx.cacheDir, "camera/payslip.jpg").apply { parentFile?.mkdirs() }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val mime = ctx.contentResolver.getType(uri) ?: ""
            source = uri; isImage = mime.startsWith("image/")
            sourceLabel = if (isImage) "נבחרה תמונה" else "נבחר קובץ PDF"
            err = null
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) { source = cameraUri; isImage = true; sourceLabel = "צולם תלוש"; err = null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("העלאת תלוש") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("חודש")
                Box {
                    OutlinedButton(onClick = { monthMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(monthTitle(month), modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                        (0 until 24).map { YearMonth.now().minusMonths(it.toLong()) }.forEach { m ->
                            DropdownMenuItem(text = { Text(monthTitle(m)) }, onClick = { month = m; monthMenu = false })
                        }
                    }
                }
                Label("מעסיק")
                if (state.employers.isEmpty()) {
                    Text("קודם צריך להוסיף מעסיק בהגדרות", color = Warn, fontSize = 13.sp)
                } else {
                    EmployerPicker(state, employerId, onPick = { employerId = it }, onNew = null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFile.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.weight(1f)) {
                        Text("בחר קובץ")
                    }
                    OutlinedButton(onClick = { takePhoto.launch(cameraUri) }, modifier = Modifier.weight(1f)) {
                        Text("צלם תלוש")
                    }
                }
                sourceLabel?.let { Text("✓ $it", color = Green, fontSize = 13.sp) }
                if (isImage) Text("התמונה תישמר כקובץ PDF", color = Muted, fontSize = 12.sp)
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = employerId
                val s = source
                when {
                    e == null -> err = "בחר מעסיק"
                    s == null -> err = "בחר קובץ או צלם את התלוש"
                    else -> onUpload(month, e, s, isImage)
                }
            }) { Text("העלה לענן", color = Amber, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

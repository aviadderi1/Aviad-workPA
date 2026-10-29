package com.aviad.workpa

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.aviad.workpa.data.AppSettings
import com.aviad.workpa.data.CalendarInfo
import com.aviad.workpa.data.CalendarRepo
import com.aviad.workpa.data.Employer
import com.aviad.workpa.data.Job
import com.aviad.workpa.data.JobMeta
import com.aviad.workpa.data.Store
import com.aviad.workpa.update.UpdateInfo
import com.aviad.workpa.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** Filter value for jobs that have no employer yet. */
const val NO_EMPLOYER = "__none__"

class AppState(private val context: Context) {
    val store = Store(context)
    val repo = CalendarRepo(context)

    var month by mutableStateOf(YearMonth.now())
    var jobs by mutableStateOf<List<Job>>(emptyList())
    var employers by mutableStateOf(store.employers())
    var settings by mutableStateOf(store.settings())
    var calendars by mutableStateOf<List<CalendarInfo>>(emptyList())
    var hasPermission by mutableStateOf(checkPermission())
    var filter by mutableStateOf<String?>(null)          // null = all employers
    var lastSync by mutableStateOf<Long?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var resumeTick by mutableIntStateOf(0)

    // ---------- in-app update ----------
    var update by mutableStateOf<UpdateInfo?>(null)
    var updateProgress by mutableStateOf<Float?>(null)   // non-null while downloading
    var updateMsg by mutableStateOf<String?>(null)

    suspend fun checkUpdate(manual: Boolean = false) {
        try {
            update = Updater.check(context)
            if (manual && update == null) updateMsg = "יש לך את הגרסה האחרונה"
        } catch (e: Exception) {
            if (manual) updateMsg = "אין חיבור לבדיקת עדכונים"
        }
    }

    suspend fun installUpdate() {
        val info = update ?: return
        if (Updater.needsInstallPermission(context)) {
            updateMsg = "אשר \"התקנת אפליקציות\" ל-Aviad workPA, חזור ולחץ שוב על עדכן"
            Updater.openInstallPermissionSettings(context)
            return
        }
        try {
            updateMsg = null
            updateProgress = 0f
            val apk = Updater.download(context, info) { updateProgress = it }
            updateProgress = null
            Updater.install(context, apk)
        } catch (e: Exception) {
            updateProgress = null
            updateMsg = "העדכון נכשל: ${e.message}"
        }
    }

    fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun reload() {
        hasPermission = checkPermission()
        employers = store.employers()
        settings = store.settings()
        if (!hasPermission) { jobs = emptyList(); return }
        loading = true
        try {
            val m = month
            val kw = settings.keywords
            val (loaded, cals) = withContext(Dispatchers.IO) {
                val events = repo.loadMonth(m, kw)
                val metas = store.metaFor(events.map { it.key })
                events.map { Job(it, metas.getValue(it.key)) } to repo.calendars()
            }
            if (m == month) {
                jobs = loaded
                calendars = cals
                lastSync = System.currentTimeMillis()
                error = null
            }
        } catch (e: Exception) {
            error = "שגיאה בקריאת היומן: ${e.message}"
        } finally {
            loading = false
        }
    }

    fun employerName(id: String?): String =
        employers.firstOrNull { it.id == id }?.name ?: "ללא מעסיק"

    fun visibleJobs(): List<Job> = when (val f = filter) {
        null -> jobs
        NO_EMPLOYER -> jobs.filter { j -> j.meta.employerId == null || employers.none { it.id == j.meta.employerId } }
        else -> jobs.filter { it.meta.employerId == f }
    }

    fun saveMeta(meta: JobMeta) {
        store.saveMeta(meta)
        jobs = jobs.map { if (it.key == meta.key) it.copy(meta = meta) else it }
    }

    fun togglePaid(job: Job) = saveMeta(job.meta.copy(paid = !job.meta.paid))

    fun addEmployer(name: String): Employer {
        val e = store.addEmployer(name)
        employers = store.employers()
        return e
    }

    fun renameEmployer(id: String, name: String) {
        store.renameEmployer(id, name); employers = store.employers()
    }

    fun deleteEmployer(id: String) {
        store.deleteEmployer(id); employers = store.employers()
        if (filter == id) filter = null
        jobs = jobs.map { if (it.meta.employerId == id) it.copy(meta = it.meta.copy(employerId = null)) else it }
    }

    fun saveSettings(s: AppSettings) {
        store.saveSettings(s); settings = s
    }
}

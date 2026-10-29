package com.aviad.workpa.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local storage for everything that is not in the calendar:
 * employers, per-job meta (employer / amount / paid / notes) and settings.
 * Plain JSON in SharedPreferences – no extra libraries.
 */
const val PALETTE_SIZE = 8

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("workpa", Context.MODE_PRIVATE)

    // ---------- employers ----------
    fun employers(): List<Employer> {
        val arr = JSONArray(prefs.getString("employers", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Employer(o.getString("id"), o.getString("name"), o.optInt("color", it))
        }
    }

    private fun saveEmployers(list: List<Employer>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        prefs.edit().putString("employers", arr.toString()).apply()
    }

    fun addEmployer(name: String): Employer {
        val current = employers()
        val used = current.map { it.color }.toSet()
        val color = (0 until PALETTE_SIZE).firstOrNull { it !in used } ?: (current.size % PALETTE_SIZE)
        val e = Employer(UUID.randomUUID().toString(), name.trim(), color)
        saveEmployers(current + e)
        return e
    }

    fun renameEmployer(id: String, name: String) =
        saveEmployers(employers().map { if (it.id == id) it.copy(name = name.trim()) else it })

    fun setEmployerColor(id: String, color: Int) =
        saveEmployers(employers().map { if (it.id == id) it.copy(color = color) else it })

    fun deleteEmployer(id: String) {
        saveEmployers(employers().filterNot { it.id == id })
        // detach jobs from the deleted employer
        val all = allMeta().mapValues { (_, m) -> if (m.employerId == id) m.copy(employerId = null) else m }
        saveAllMeta(all)
    }

    // ---------- job meta ----------
    private fun allMeta(): Map<String, JobMeta> {
        val o = JSONObject(prefs.getString("meta", "{}"))
        val out = HashMap<String, JobMeta>()
        o.keys().forEach { k ->
            val m = o.getJSONObject(k)
            out[k] = JobMeta(
                key = k,
                employerId = m.optString("employerId").ifEmpty { null },
                amount = if (m.has("amount") && !m.isNull("amount")) m.getDouble("amount") else null,
                paid = m.optBoolean("paid", false),
                notes = m.optString("notes", ""),
            )
        }
        return out
    }

    private fun saveAllMeta(map: Map<String, JobMeta>) {
        val o = JSONObject()
        map.forEach { (k, m) ->
            val j = JSONObject()
            m.employerId?.let { j.put("employerId", it) }
            m.amount?.let { j.put("amount", it) }
            j.put("paid", m.paid)
            j.put("notes", m.notes)
            o.put(k, j)
        }
        prefs.edit().putString("meta", o.toString()).apply()
    }

    fun meta(key: String): JobMeta = allMeta()[key] ?: JobMeta(key)

    fun metaFor(keys: Collection<String>): Map<String, JobMeta> {
        val all = allMeta()
        return keys.associateWith { all[it] ?: JobMeta(it) }
    }

    fun saveMeta(meta: JobMeta) = saveAllMeta(allMeta() + (meta.key to meta))

    fun deleteMeta(key: String) = saveAllMeta(allMeta() - key)

    // ---------- settings ----------
    fun settings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            keywords = list("keywords") ?: d.keywords,
            calendarId = prefs.getLong("calendarId", -1L).takeIf { it >= 0 },
            reportName = prefs.getString("reportName", d.reportName) ?: d.reportName,
            jobTypes = list("jobTypes") ?: d.jobTypes,
        )
    }

    fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putString("keywords", JSONArray(s.keywords).toString())
            .putString("jobTypes", JSONArray(s.jobTypes).toString())
            .putLong("calendarId", s.calendarId ?: -1L)
            .putString("reportName", s.reportName)
            .apply()
    }

    private fun list(name: String): List<String>? {
        val raw = prefs.getString(name, null) ?: return null
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
    }
}

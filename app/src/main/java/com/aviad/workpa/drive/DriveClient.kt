package com.aviad.workpa.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

/** A payslip stored in Google Drive. */
data class Payslip(
    val id: String,
    val name: String,
    val month: String,          // "2026-10"
    val employerId: String,
    val employerName: String,
    val mimeType: String,
    val uploaded: LocalDate,
)

/**
 * Minimal Google Drive v3 REST client (scope drive.file: the app only sees files it created).
 * All payslips live in one folder: "Aviad workPA – תלושים".
 */
class DriveClient(private val token: String) {
    private val api = "https://www.googleapis.com/drive/v3"
    private val upload = "https://www.googleapis.com/upload/drive/v3"

    class AuthException : Exception("החיבור ל-Google Drive פג. נסה שוב.")

    private fun open(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 60_000
        }

    private fun HttpURLConnection.json(): JSONObject {
        val code = responseCode
        if (code == 401) throw AuthException()
        if (code !in 200..299) {
            val err = errorStream?.bufferedReader()?.readText() ?: ""
            throw IllegalStateException("Drive שגיאה $code ${err.take(200)}")
        }
        val body = inputStream.bufferedReader().readText()
        disconnect()
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun folderId(): String = withContext(Dispatchers.IO) {
        val query = "name='$FOLDER' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val found = open("$api/files?q=${q(query)}&fields=files(id)&spaces=drive").json().getJSONArray("files")
        if (found.length() > 0) return@withContext found.getJSONObject(0).getString("id")
        val c = open("$api/files?fields=id", "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        c.outputStream.use {
            it.write(JSONObject().put("name", FOLDER).put("mimeType", "application/vnd.google-apps.folder")
                .toString().toByteArray())
        }
        c.json().getString("id")
    }

    suspend fun list(): List<Payslip> = withContext(Dispatchers.IO) {
        val folder = folderId()
        val query = "'$folder' in parents and trashed=false"
        val fields = "files(id,name,mimeType,createdTime,appProperties)"
        val files = open("$api/files?q=${q(query)}&fields=${q(fields)}&pageSize=1000").json().getJSONArray("files")
        (0 until files.length()).mapNotNull { i ->
            val f = files.getJSONObject(i)
            val p = f.optJSONObject("appProperties") ?: return@mapNotNull null
            Payslip(
                id = f.getString("id"),
                name = f.getString("name"),
                month = p.optString("month"),
                employerId = p.optString("employerId"),
                employerName = p.optString("employerName"),
                mimeType = f.optString("mimeType"),
                uploaded = runCatching {
                    Instant.parse(f.getString("createdTime")).atZone(ZoneId.systemDefault()).toLocalDate()
                }.getOrDefault(LocalDate.now()),
            )
        }
    }

    /** Uploads a payslip; an older one for the same month + employer is replaced. */
    suspend fun upload(
        month: String, employerId: String, employerName: String,
        fileName: String, mimeType: String, data: InputStream,
        existing: List<Payslip>,
    ): Unit = withContext(Dispatchers.IO) {
        val folder = folderId()
        val boundary = "workpa" + System.currentTimeMillis()
        val meta = JSONObject()
            .put("name", fileName)
            .put("parents", JSONArray().put(folder))
            .put("appProperties", JSONObject()
                .put("month", month).put("employerId", employerId).put("employerName", employerName))
        val c = open("$upload/files?uploadType=multipart&fields=id", "POST").apply {
            doOutput = true
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }
        c.outputStream.use { out ->
            out.write(("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" +
                meta.toString() + "\r\n--$boundary\r\nContent-Type: $mimeType\r\n\r\n").toByteArray())
            data.use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        c.json()
        existing.filter { it.month == month && it.employerId == employerId }.forEach { delete(it.id) }
    }

    suspend fun download(slip: Payslip, dest: File): File = withContext(Dispatchers.IO) {
        val c = open("$api/files/${slip.id}?alt=media")
        if (c.responseCode == 401) throw AuthException()
        if (c.responseCode != 200) throw IllegalStateException("הורדה נכשלה (${c.responseCode})")
        dest.parentFile?.mkdirs()
        c.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        c.disconnect()
        dest
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        val c = open("$api/files/$id", "DELETE")
        val code = c.responseCode
        c.disconnect()
        if (code == 401) throw AuthException()
        if (code !in 200..299 && code != 404) throw IllegalStateException("מחיקה נכשלה ($code)")
    }

    companion object {
        const val FOLDER = "Aviad workPA – תלושים"
        const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}

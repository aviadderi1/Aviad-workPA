package com.aviad.workpa.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val build: Long, val apkUrl: String)

/**
 * Checks the GitHub "latest" release and installs a newer APK over the current one.
 * The APK is always signed with the same key, so the update keeps all data.
 */
object Updater {
    private const val API = "https://api.github.com/repos/aviadderi1/Aviad-workPA/releases/tags/latest"

    fun installedBuild(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }

    fun installedVersion(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""

    /** Returns info about a newer build, or null if this one is the latest. */
    suspend fun check(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val build = Regex("build (\\d+)").find(json.optString("name"))?.groupValues?.get(1)?.toLongOrNull()
                ?: return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
            }
            if (url == null || build <= installedBuild(ctx)) null else UpdateInfo(build, url)
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the APK, reporting progress 0..1. */
    suspend fun download(ctx: Context, info: UpdateInfo, progress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "Aviad-workPA-${info.build}.apk")
            var url = URL(info.apkUrl)
            var conn: HttpURLConnection
            var hops = 0
            while (true) {   // GitHub redirects to another host; follow manually
                conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                val code = conn.responseCode
                if (code in 300..399 && hops++ < 5) {
                    url = URL(url, conn.getHeaderField("Location"))
                    conn.disconnect()
                    continue
                }
                if (code != 200) throw IllegalStateException("הורדה נכשלה ($code)")
                break
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) withContext(Dispatchers.Main) { progress(done.toFloat() / total) }
                    }
                }
            }
            conn.disconnect()
            file
        }

    /** True when Android still needs the user to allow installs from this app. */
    fun needsInstallPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

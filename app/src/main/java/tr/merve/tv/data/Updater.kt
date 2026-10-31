package tr.merve.tv.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import tr.merve.tv.BuildConfig
import java.io.File

/**
 * Updates Merve from GitHub Releases, so a new version never needs the TV's
 * wireless debugging (Android switches that off on its own). Releases carry an
 * asset named `merve-<versionCode>.apk`; Android only accepts it if it is signed
 * with the same key as the installed app.
 */
class Updater(private val context: Context, private val http: OkHttpClient, private val json: Json) {
    @Serializable
    private data class Release(@SerialName("tag_name") val tag: String, val assets: List<Asset> = emptyList())

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val url: String)

    data class Update(val versionCode: Int, val versionName: String, val url: String)

    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(LATEST)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Merve/${BuildConfig.VERSION_NAME}")
                .build()
            val body = http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return@runCatching null
            val release = json.decodeFromString<Release>(body)
            release.assets.firstNotNullOfOrNull { a ->
                APK.matchEntire(a.name)?.groupValues?.get(1)?.toIntOrNull()?.let { code ->
                    Update(code, release.tag.removePrefix("v"), a.url)
                }
            }?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        }.getOrNull()
    }

    /** Downloads and hands the APK to Android's installer, which asks the viewer to confirm. */
    suspend fun install(update: Update, onProgress: (Float) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.cacheDir, "merve-update.apk")
            http.newCall(Request.Builder().url(update.url).header("User-Agent", "Merve/${BuildConfig.VERSION_NAME}").build())
                .execute().use { r ->
                    val body = r.body
                    check(r.isSuccessful && body != null) { "İndirilemedi (HTTP ${r.code})" }
                    val total = body.contentLength().takeIf { it > 0 } ?: -1L
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) onProgress(done.toFloat() / total)
                            }
                        }
                    }
                }

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("merve.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val intent = Intent(context, InstallResultReceiver::class.java)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        }
    }

    companion object {
        private const val LATEST = "https://api.github.com/repos/Qaptan/merve-tv/releases/latest"
        private val APK = Regex("""merve-(\d+)\.apk""")
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999) != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        // Android wants the viewer to confirm (always the first time): show its dialog.
        val confirm = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
        } ?: return
        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

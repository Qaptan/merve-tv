package tr.merve.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks streams from the TV itself. The nightly list is tested from GitHub's
 * servers abroad, so geo-restricted channels can look dead there while working
 * here (and the other way round); the TV's own result wins.
 */
class StreamChecker(context: Context, http: OkHttpClient, private val json: Json) {
    private val file = File(context.filesDir, "alive.json")
    private val quick = http.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    fun load(): Map<String, Boolean> =
        runCatching { json.decodeFromString<Map<String, Boolean>>(file.readText()) }.getOrDefault(emptyMap())

    fun isStale(maxAgeMs: Long = 3 * 60 * 60 * 1000L): Boolean =
        !file.exists() || System.currentTimeMillis() - file.lastModified() > maxAgeMs

    suspend fun checkAll(channels: List<Channel>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val gate = Semaphore(12)
        val result = coroutineScope {
            channels.map { ch ->
                async { gate.withPermit { ch.id to ch.streams.any { works(it) } } }
            }.awaitAll().toMap()
        }
        runCatching { file.writeText(json.encodeToString(result)) }
        result
    }

    /** Master playlist, then its first variant: enough to tell a live stream from a dead one. */
    private fun works(s: Stream): Boolean = runCatching {
        val master = fetch(s.url, s) ?: return false
        if (!master.contains("#EXTM3U")) return false
        if (!master.contains("#EXT-X-STREAM-INF")) return true
        val variant = master.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return false
        val url = s.url.toHttpUrlOrNull()?.resolve(variant)?.toString() ?: return false
        fetch(url, s)?.contains("#EXT") == true
    }.getOrDefault(false)

    private fun fetch(url: String, s: Stream): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", s.userAgent ?: "Mozilla/5.0 (Linux; Android 14) Merve/1.0")
            .apply { s.referrer?.let { header("Referer", it) } }
            .build()
        return quick.newCall(req).execute().use { r -> if (r.isSuccessful) r.peekBody(256 * 1024).string() else null }
    }
}

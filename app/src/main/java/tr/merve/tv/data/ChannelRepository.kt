package tr.merve.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * The channel list lives in three places, newest first: the nightly file on
 * GitHub, the last copy saved on the TV, and the copy bundled in the APK.
 * The app never ends up empty, even offline on first launch.
 */
class ChannelRepository(
    private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
    private val remoteUrl: String,
) {
    private val cacheFile = File(context.filesDir, "tr.json")

    val hasRemote: Boolean get() = remoteUrl.isNotBlank()

    suspend fun loadLocal(): ChannelList = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<ChannelList>(cacheFile.readText()) }.getOrNull()
            ?: context.assets.open("tr.json").bufferedReader().use { json.decodeFromString(it.readText()) }
    }

    fun isStale(maxAgeMs: Long = 6 * 60 * 60 * 1000L): Boolean =
        !cacheFile.exists() || System.currentTimeMillis() - cacheFile.lastModified() > maxAgeMs

    suspend fun refresh(): Result<ChannelList> = withContext(Dispatchers.IO) {
        if (!hasRemote) return@withContext Result.failure(IOException("Güncelleme adresi ayarlanmadı"))
        runCatching {
            val body = http.newCall(Request.Builder().url(remoteUrl).build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Sunucu yanıtı: ${res.code}")
                res.body?.string() ?: throw IOException("Boş yanıt")
            }
            val list = json.decodeFromString<ChannelList>(body)
            if (list.channels.isEmpty()) throw IOException("Liste boş geldi")
            // Write to a temp file first so a crash mid-write never leaves a broken cache.
            val tmp = File(cacheFile.parentFile, "tr.json.tmp")
            tmp.writeText(body)
            tmp.renameTo(cacheFile)
            list
        }
    }
}

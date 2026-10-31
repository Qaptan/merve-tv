package tr.merve.tv.data

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The player downloads HLS segments one after another. Over the long Turkey -> Bali
 * route a single connection is slow, but several in parallel are 2-3x faster, so this
 * interceptor reads each media playlist and, whenever the player asks for a segment,
 * starts downloading the next few at once. When the player gets to them they are
 * already here (or on the way).
 */
class SegmentPrefetcher(
    private val net: OkHttpClient,
    private val depth: Int = 3,
    private val maxEntries: Int = 6,
) : Interceptor {
    private class Fetched(val type: MediaType?, val bytes: ByteArray)

    private val lock = Any()
    private val pending = LinkedHashMap<String, CompletableFuture<Fetched?>>()
    private val playlists = ConcurrentHashMap<String, List<String>>()
    private val segmentOwner = ConcurrentHashMap<String, String>()
    private val pool: ExecutorService = Executors.newFixedThreadPool(depth) { r ->
        Thread(r, "merve-prefetch").apply { isDaemon = true }
    }
    @Volatile private var generation = 0

    /** Call when switching to another stream so nothing from the old one is kept. */
    fun reset() {
        generation++
        synchronized(lock) {
            pending.values.forEach { it.cancel(true) }
            pending.clear()
        }
        playlists.clear()
        segmentOwner.clear()
    }

    fun close() {
        reset()
        pool.shutdownNow()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (req.method != "GET" || req.header("Range") != null) return chain.proceed(req)
        val url = req.url.toString()

        val ready = synchronized(lock) { pending.remove(url) }
        segmentOwner[url]?.let { prefetchAfter(it, url, req) }
        if (ready != null) {
            val got = runCatching { ready.get(60, TimeUnit.SECONDS) }.getOrNull()
            if (got != null) {
                return Response.Builder()
                    .request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(got.bytes.toResponseBody(got.type))
                    .build()
            }
        }

        val res = chain.proceed(req)
        if (res.isSuccessful && runCatching { res.peekBody(7).string() == "#EXTM3U" }.getOrDefault(false)) {
            remember(url, req, res.peekBody(4L shl 20).string())
        }
        return res
    }

    private fun remember(url: String, req: Request, text: String) {
        // Byte-range playlists share one file between segments; leave those to the player.
        if (!text.contains("#EXTINF") || text.contains("#EXT-X-BYTERANGE")) return
        val segments = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { req.url.resolve(it)?.toString() }
            .toList()
        playlists[url] = segments
        if (segmentOwner.size > 4000) segmentOwner.clear()
        segments.forEach { segmentOwner[it] = url }
    }

    private fun prefetchAfter(playlistUrl: String, url: String, req: Request) {
        val segments = playlists[playlistUrl] ?: return
        val i = segments.indexOf(url)
        if (i < 0) return
        val gen = generation
        synchronized(lock) {
            for (next in segments.drop(i + 1).take(depth)) {
                if (pending.containsKey(next)) continue
                pending[next] = CompletableFuture.supplyAsync({ if (gen == generation) fetch(next, req) else null }, pool)
            }
            while (pending.size > maxEntries) {
                val oldest = pending.keys.first()
                pending.remove(oldest)?.cancel(true)
            }
        }
    }

    private fun fetch(url: String, original: Request): Fetched? = runCatching {
        net.newCall(original.newBuilder().url(url).build()).execute().use { r ->
            val body = r.body
            if (!r.isSuccessful || body == null) null else Fetched(body.contentType(), body.bytes())
        }
    }.getOrNull()
}

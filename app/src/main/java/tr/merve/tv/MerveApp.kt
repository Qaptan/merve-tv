package tr.merve.tv

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import tr.merve.tv.data.ChannelRepository
import tr.merve.tv.data.Prefs
import java.util.concurrent.TimeUnit

class MerveApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.imageHttp })) }
            .crossfade(true)
            .build()
}

class AppContainer(context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followSslRedirects(true)
        .build()
    // Wikimedia (source of many logos) refuses requests without a descriptive User-Agent.
    val imageHttp: OkHttpClient = http.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Merve/${BuildConfig.VERSION_NAME} (Android TV; tr.merve.tv)").build())
        }
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    val channels = ChannelRepository(context, http, json, BuildConfig.CHANNELS_URL)
    val prefs = Prefs(context)
}

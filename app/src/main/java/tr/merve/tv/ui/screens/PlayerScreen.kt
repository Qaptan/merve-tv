@file:OptIn(UnstableApi::class)

package tr.merve.tv.ui.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import tr.merve.tv.data.Channel
import tr.merve.tv.data.Motion
import tr.merve.tv.data.Stream
import tr.merve.tv.ui.components.ChannelLogo
import tr.merve.tv.ui.components.LiveBadge
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.LocalMotion
import tr.merve.tv.ui.theme.T

private const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14) Merve/1.0"

private fun mediaSourceFor(s: Stream, http: OkHttpClient): MediaSource {
    val data = OkHttpDataSource.Factory(http).setUserAgent(s.userAgent ?: DEFAULT_UA)
    s.referrer?.let { data.setDefaultRequestProperties(mapOf("Referer" to it)) }
    val item = MediaItem.Builder().setUri(s.url).apply {
        // Many playlist URLs have no .m3u8 suffix; hinting HLS avoids a failed sniff.
        if (s.url.contains(".m3u8", ignoreCase = true) || !s.url.contains(".mp4", ignoreCase = true)) {
            setMimeType(MimeTypes.APPLICATION_M3U8)
        }
    }.build()
    return DefaultMediaSourceFactory(data).createMediaSource(item)
}

@Composable
fun PlayerScreen(
    channels: List<Channel>,
    startId: String,
    http: OkHttpClient,
    onWatched: (String) -> Unit,
    onExit: () -> Unit,
) {
    if (channels.isEmpty()) {
        LaunchedEffect(Unit) { onExit() }
        return
    }
    val context = LocalContext.current
    val motion = LocalMotion.current
    // Track the channel by id, not position: the list changes under us when the
    // background stream check or a refresh finishes, and a stored position would
    // then point at a different channel.
    var currentId by remember { mutableStateOf(channels.firstOrNull { it.id == startId }?.id ?: channels.first().id) }
    var lastSeen by remember { mutableStateOf(channels.firstOrNull { it.id == currentId } ?: channels.first()) }
    var lastIndex by remember { mutableIntStateOf(0) }
    var streamIdx by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(true) }
    var listOpen by remember { mutableStateOf(false) }
    val found = channels.indexOfFirst { it.id == currentId }
    if (found >= 0) {
        lastSeen = channels[found]
        lastIndex = found
    }
    // If the channel dropped out of the list, keep playing it rather than jumping.
    val channel = if (found >= 0) channels[found] else lastSeen
    val index = if (found >= 0) found else lastIndex.coerceIn(0, channels.lastIndex)
    val currentChannel by rememberUpdatedState(channel)

    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Try the channel's next stream before giving up on it.
                if (streamIdx + 1 < currentChannel.streams.size) streamIdx++ else failed = true
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(channel.id, streamIdx) {
        val s = channel.streams.getOrNull(streamIdx)
        if (s == null) {
            failed = true
            return@LaunchedEffect
        }
        failed = false
        player.setMediaSource(mediaSourceFor(s, http))
        player.prepare()
        player.play()
    }
    LaunchedEffect(channel.id) {
        delay(3000)
        if (!failed) onWatched(channel.id)
    }
    LaunchedEffect(channel.id, showInfo) {
        if (showInfo && !listOpen) {
            delay(4500)
            showInfo = false
        }
    }

    fun select(id: String) {
        currentId = id
        streamIdx = 0
        failed = false
        showInfo = true
    }
    fun zap(step: Int) {
        val base = if (found >= 0) index else if (step > 0) index - 1 else index
        select(channels[((base + step) % channels.size + channels.size) % channels.size].id)
    }

    val root = remember { FocusRequester() }
    LaunchedEffect(listOpen) { if (!listOpen) runCatching { root.requestFocus() } }
    BackHandler(enabled = listOpen) { listOpen = false }
    BackHandler(enabled = !listOpen) { onExit() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(root)
            .onKeyEvent { e ->
                if (listOpen || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionUp, Key.ChannelUp -> { zap(-1); true }
                    Key.DirectionDown, Key.ChannelDown -> { zap(1); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionLeft -> { listOpen = true; showInfo = true; true }
                    Key.DirectionRight, Key.Info -> { showInfo = !showInfo; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && !failed) {
            Text("Yükleniyor…", style = T.Body, modifier = Modifier.align(Alignment.Center))
        }
        if (failed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bu kanal şu an açılmıyor", style = T.H1)
                Text("▲ ▼ ile başka bir kanala geçebilirsiniz", style = T.Body)
            }
        }

        val enter = if (motion == Motion.Off) fadeIn() else fadeIn() + slideInVertically { it / 3 }
        val exit = if (motion == Motion.Off) fadeOut() else fadeOut() + slideOutVertically { it / 3 }
        AnimatedVisibility(showInfo || listOpen, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xF205060A)),
                ),
            )
        }
        AnimatedVisibility(showInfo, Modifier.align(Alignment.BottomStart), enter = enter, exit = exit) {
            InfoBar(channel, number = index + 1)
        }
        AnimatedVisibility(
            listOpen,
            Modifier.align(Alignment.CenterStart),
            enter = if (motion == Motion.Off) fadeIn() else fadeIn() + slideInHorizontally { -it / 2 },
            exit = if (motion == Motion.Off) fadeOut() else fadeOut() + slideOutHorizontally { -it / 2 },
        ) {
            ChannelList(channels, if (found >= 0) index else -1) { i ->
                if (channels[i].id != currentId) select(channels[i].id)
                listOpen = false
                showInfo = true
            }
        }
    }
}

@Composable
private fun InfoBar(channel: Channel, number: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 40.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            ChannelLogo(
                channel,
                Modifier.size(104.dp).background(C.Surface, RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, C.Line), RoundedCornerShape(24.dp)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(number.toString().padStart(3, '0'), style = T.Small.copy(fontSize = 15.sp))
                    LiveBadge()
                    channel.streams.firstOrNull()?.quality?.let { q ->
                        Text(
                            q, style = T.Badge.copy(color = C.Soft),
                            modifier = Modifier.border(BorderStroke(1.dp, C.Line), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Text(channel.name, style = T.Hero.copy(fontSize = 46.sp))
            }
        }
        Text("▲ ▼ kanal değiştir · OK kanal listesi", style = T.Small.copy(fontSize = 14.sp))
    }
}

@Composable
private fun ChannelList(channels: List<Channel>, current: Int, onPick: (Int) -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { currentFocus.requestFocus() }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.width(340.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xF005060A), Color(0x9905060A), Color.Transparent))),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(channels, key = { _, c -> c.id }) { i, c ->
            val on = i == current
            Surface(
                onClick = { onPick(i) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .then(if (i == current.coerceAtLeast(0)) Modifier.focusRequester(currentFocus) else Modifier),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (on) Color(0x26FFFFFF) else Color.Transparent,
                    contentColor = if (on) C.Text else C.Soft,
                    focusedContainerColor = C.Accent,
                    focusedContentColor = C.Ink,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text((i + 1).toString().padStart(3, '0'), style = T.Small.copy(color = Color.Unspecified))
                    Text(c.name, style = T.Label.copy(fontSize = 16.sp, color = Color.Unspecified), maxLines = 1)
                }
            }
        }
    }
}

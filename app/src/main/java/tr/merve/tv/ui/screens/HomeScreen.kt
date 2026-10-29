package tr.merve.tv.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import tr.merve.tv.data.Channel
import tr.merve.tv.data.Motion
import tr.merve.tv.ui.ListKey
import tr.merve.tv.ui.MainState
import tr.merve.tv.ui.components.ChannelCard
import tr.merve.tv.ui.components.ChannelLogo
import tr.merve.tv.ui.components.LiveBadge
import tr.merve.tv.ui.components.PillButton
import tr.merve.tv.ui.components.Rail
import tr.merve.tv.ui.components.RailItem
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.LocalMotion
import tr.merve.tv.ui.theme.T

private data class Shelf(val key: String, val title: String, val channels: List<Channel>)

@Composable
fun HomeScreen(
    state: MainState,
    onRail: (RailItem) -> Unit,
    onPlay: (String, String) -> Unit,
    onFavorite: (String) -> Unit,
) {
    val featured = state.settings.lastChannel?.let { state.byId[it] }?.takeIf { it.alive }
        ?: state.inGroup("ulusal").firstOrNull() ?: state.channels.firstOrNull()
    val shelves = remember(state.channels, state.settings) {
        buildList {
            if (state.recent.isNotEmpty()) add(Shelf(ListKey.RECENT, "Son izlenenler", state.recent))
            if (state.favorites.isNotEmpty()) add(Shelf(ListKey.FAVORITES, "Favoriler", state.favorites))
            state.groups.forEach { g ->
                if (g.id == "yerel" && !state.settings.showLocal) return@forEach
                val list = state.inGroup(g.id)
                if (list.isNotEmpty()) add(Shelf(g.id, g.name, list))
            }
        }
    }
    val heroFocus = remember { FocusRequester() }
    LaunchedEffect(featured?.id) { runCatching { heroFocus.requestFocus() } }

    Row(Modifier.fillMaxSize().background(C.Ink)) {
        Rail(RailItem.Home, onRail)
        LazyColumn(
            Modifier.weight(1f).fillMaxSize(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            if (featured != null) {
                item(key = "hero") {
                    Hero(
                        channel = featured,
                        groupName = state.groupName(featured.group),
                        isFavorite = featured.id in state.settings.favorites,
                        onPlay = { onPlay(featured.id, featured.group) },
                        onFavorite = { onFavorite(featured.id) },
                        playFocus = heroFocus,
                    )
                }
            }
            items(shelves, key = { it.key }) { shelf ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(shelf.title, style = T.H2, modifier = Modifier.padding(start = 40.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        items(shelf.channels, key = { it.id }) { ch ->
                            ChannelCard(
                                channel = ch,
                                onClick = { onPlay(ch.id, shelf.key) },
                                onLongClick = { onFavorite(ch.id) },
                                favorite = ch.id in state.settings.favorites,
                                subtitle = state.groupName(ch.group),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(
    channel: Channel,
    groupName: String,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    playFocus: FocusRequester,
) {
    val motion = LocalMotion.current
    val t = rememberInfiniteTransition(label = "waves")
    val shift = t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "shift")

    Box(
        Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(290.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(C.Surface)
            .border(BorderStroke(1.dp, C.Surface2), RoundedCornerShape(28.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val period = 600f
            val dx = if (motion == Motion.Full) -shift.value * period else 0f
            listOf(Triple(0.72f, 0.55f, 2.2f), Triple(0.8f, 0.3f, 1.6f), Triple(0.64f, 0.15f, 1.2f)).forEach { (y, a, w) ->
                val path = Path()
                val base = size.height * y
                var x = -period
                path.moveTo(x, base)
                while (x < size.width + period * 2) {
                    path.relativeCubicTo(period / 4, -40f, period / 4 * 3, 40f, period, 0f)
                    x += period
                }
                translate(left = dx) {
                    drawPath(path, (if (a > 0.2f) C.Accent else C.Text).copy(alpha = a * 0.6f), style = Stroke(width = w * 1.5f))
                }
            }
        }
        ChannelLogo(
            channel,
            Modifier.align(Alignment.CenterEnd).padding(end = 60.dp).size(width = 300.dp, height = 200.dp),
            showInitials = false,
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 44.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LiveBadge()
                Text(groupName, style = T.Small.copy(fontSize = 14.sp))
            }
            Text(channel.name, style = T.Hero)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillButton(
                    "İzle", onPlay, Modifier.focusRequester(playFocus), primary = true,
                    leading = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                )
                PillButton(
                    if (isFavorite) "Favorilerde" else "Favorilere ekle", onFavorite,
                    leading = { Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null) },
                )
            }
        }
    }
}

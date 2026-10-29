package tr.merve.tv.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tr.merve.tv.data.Motion
import tr.merve.tv.ui.ListKey
import tr.merve.tv.ui.MainState
import tr.merve.tv.ui.components.ChannelCard
import tr.merve.tv.ui.components.EmptyNote
import tr.merve.tv.ui.components.Rail
import tr.merve.tv.ui.components.RailItem
import tr.merve.tv.ui.components.focusScale
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.LocalMotion
import tr.merve.tv.ui.theme.T

private const val KEYS = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ0123456789"

/** Channel names in the list are mostly ASCII ("Haberturk"), so compare without Turkish marks. */
internal fun fold(s: String): String = s.uppercase(java.util.Locale.forLanguageTag("tr"))
    .map { when (it) { 'Ç' -> 'C'; 'Ğ' -> 'G'; 'İ' -> 'I'; 'Ö' -> 'O'; 'Ş' -> 'S'; 'Ü' -> 'U'; else -> it } }
    .joinToString("")

@Composable
fun SearchScreen(state: MainState, onRail: (RailItem) -> Unit, onPlay: (String, String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, state.channels) {
        val q = fold(query.trim())
        if (q.isEmpty()) state.channels.take(15) else state.channels.filter { fold(it.name).contains(q) }
    }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Row(Modifier.fillMaxSize().background(C.Ink)) {
        Rail(RailItem.Search, onRail)
        Column(
            Modifier.width(540.dp).fillMaxHeight().padding(start = 36.dp, top = 40.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SearchField(query)
            KEYS.chunked(8).forEachIndexed { row, chars ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chars.forEachIndexed { i, ch ->
                        Key(
                            ch.toString(),
                            Modifier.weight(1f).then(if (row == 0 && i == 0) Modifier.focusRequester(firstKey) else Modifier),
                        ) { if (query.length < 24) query += ch }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Key("Boşluk", Modifier.weight(1f)) { if (query.isNotEmpty() && !query.endsWith(' ')) query += " " }
                Key("Sil", Modifier.weight(1f)) { query = query.dropLast(1) }
                Key("Temizle", Modifier.weight(1f)) { query = "" }
            }
        }
        Column(Modifier.weight(1f).padding(top = 40.dp, end = 40.dp)) {
            Text(
                if (query.isBlank()) "Önerilen kanallar" else "${results.size} kanal bulundu",
                style = T.Small.copy(fontSize = 14.sp),
            )
            if (results.isEmpty()) {
                EmptyNote("Bu adla bir kanal bulunamadı.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp, start = 6.dp, end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(results, key = { it.id }) { ch ->
                        ChannelCard(ch, onClick = { onPlay(ch.id, ListKey.ALL) }, width = null, tileHeight = 92.dp, subtitle = state.groupName(ch.group))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String) {
    val motion = LocalMotion.current
    val t = rememberInfiniteTransition(label = "caret")
    val blink = t.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "blink")
    Row(
        Modifier.fillMaxWidth().height(72.dp)
            .background(C.Surface, RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, C.Line), RoundedCornerShape(20.dp))
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = C.Muted, modifier = Modifier.size(24.dp))
        Text(query.ifEmpty { "Kanal ara" }, style = T.H1.copy(fontSize = 28.sp, color = if (query.isEmpty()) C.Muted else C.Text), maxLines = 1)
        Box(
            Modifier.width(3.dp).height(32.dp)
                .graphicsLayer { alpha = if (motion == Motion.Off) 1f else blink.value }
                .background(C.Accent, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = C.Surface, contentColor = C.Text,
            focusedContainerColor = C.Accent, focusedContentColor = C.Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale(1.1f)),
    ) {
        Text(label, style = T.Label.copy(fontSize = 17.sp, color = Color.Unspecified), modifier = Modifier.align(Alignment.Center))
    }
}

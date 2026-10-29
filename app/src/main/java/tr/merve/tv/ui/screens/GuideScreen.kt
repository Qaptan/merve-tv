package tr.merve.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tr.merve.tv.ui.ListKey
import tr.merve.tv.ui.MainState
import tr.merve.tv.ui.components.ChannelCard
import tr.merve.tv.ui.components.EmptyNote
import tr.merve.tv.ui.components.Rail
import tr.merve.tv.ui.components.RailItem
import tr.merve.tv.ui.components.focusScale
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.T

private data class Cat(val key: String, val name: String, val count: Int)

@Composable
fun GuideScreen(
    state: MainState,
    initialGroup: String?,
    onRail: (RailItem) -> Unit,
    onPlay: (String, String) -> Unit,
    onFavorite: (String) -> Unit,
) {
    val cats = remember(state.channels, state.settings.favorites) {
        buildList {
            add(Cat(ListKey.FAVORITES, "Favoriler", state.favorites.size))
            state.groups.forEach { g ->
                val n = state.inGroup(g.id).size
                if (n > 0) add(Cat(g.id, g.name, n))
            }
            add(Cat(ListKey.ALL, "Tümü", state.channels.size))
        }
    }
    var selected by rememberSaveable { mutableStateOf(initialGroup ?: "ulusal") }
    val shown = when (selected) {
        ListKey.FAVORITES -> state.favorites
        ListKey.ALL -> state.channels
        else -> state.inGroup(selected)
    }
    val firstCat = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstCat.requestFocus() } }

    Row(Modifier.fillMaxSize().background(C.Ink)) {
        Rail(if (initialGroup == ListKey.FAVORITES) RailItem.Favorites else RailItem.Guide, onRail)
        Column(Modifier.width(250.dp).fillMaxHeight().padding(start = 24.dp, top = 36.dp, end = 16.dp)) {
            Text("Kanallar", style = T.H1, modifier = Modifier.padding(start = 18.dp, bottom = 18.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(cats, key = { it.key }) { c ->
                    val on = c.key == selected
                    Surface(
                        onClick = { selected = c.key },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                            .then(if (on) Modifier.focusRequester(firstCat) else Modifier)
                            // Focusing a category is enough to show it: browsing with ▲▼ feels instant.
                            .onFocusChanged { if (it.isFocused) selected = c.key },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (on) C.Surface2 else C.Ink,
                            contentColor = if (on) C.Text else C.Soft,
                            focusedContainerColor = C.Surface2,
                            focusedContentColor = C.Text,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(BorderStroke(2.dp, C.Accent), shape = RoundedCornerShape(14.dp)),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale(1.03f)),
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(c.name, style = T.Label.copy(color = androidx.compose.ui.graphics.Color.Unspecified))
                            Text(c.count.toString(), style = T.Small)
                        }
                    }
                }
            }
        }
        Column(Modifier.weight(1f).padding(top = 40.dp, end = 40.dp, start = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(cats.firstOrNull { it.key == selected }?.name ?: "", style = T.H2.copy(fontSize = 22.sp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(C.Live, CircleShape))
                    Text("OK: izle · Basılı tut: favori", style = T.Small)
                }
            }
            if (shown.isEmpty()) {
                EmptyNote(
                    if (selected == ListKey.FAVORITES) "Henüz favori yok. Bir kanalın üzerinde OK tuşunu basılı tutun."
                    else "Bu kategoride şu an kanal yok.",
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(top = 22.dp, bottom = 40.dp, start = 6.dp, end = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(shown, key = { it.id }) { ch ->
                        ChannelCard(
                            channel = ch,
                            width = null,
                            tileHeight = 92.dp,
                            onClick = { onPlay(ch.id, selected) },
                            onLongClick = { onFavorite(ch.id) },
                            favorite = ch.id in state.settings.favorites,
                        )
                    }
                }
            }
        }
    }
}


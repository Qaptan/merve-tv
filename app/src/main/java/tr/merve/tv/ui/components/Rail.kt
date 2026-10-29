package tr.merve.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.T

enum class RailItem { Home, Guide, Search, Favorites, Settings }

@Composable
fun Rail(selected: RailItem, onSelect: (RailItem) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(96.dp).fillMaxHeight().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("m", style = T.Wordmark.copy(fontSize = 32.sp, color = C.Accent), modifier = Modifier.padding(bottom = 18.dp))
        RailButton(Icons.Filled.Home, "Ana sayfa", selected == RailItem.Home) { onSelect(RailItem.Home) }
        RailButton(Icons.AutoMirrored.Filled.List, "Kanal rehberi", selected == RailItem.Guide) { onSelect(RailItem.Guide) }
        RailButton(Icons.Filled.Search, "Ara", selected == RailItem.Search) { onSelect(RailItem.Search) }
        RailButton(Icons.Filled.Favorite, "Favoriler", selected == RailItem.Favorites) { onSelect(RailItem.Favorites) }
        Spacer(Modifier.weight(1f))
        RailButton(Icons.Filled.Settings, "Ayarlar", selected == RailItem.Settings) { onSelect(RailItem.Settings) }
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) C.Surface2 else C.Ink,
            contentColor = if (selected) C.Text else C.Muted,
            focusedContainerColor = C.Accent,
            focusedContentColor = C.Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale(1.1f)),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.align(Alignment.Center).size(24.dp))
    }
}

package tr.merve.tv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tr.merve.tv.BuildConfig
import tr.merve.tv.data.Motion
import tr.merve.tv.ui.MainState
import tr.merve.tv.ui.components.Rail
import tr.merve.tv.ui.components.RailItem
import tr.merve.tv.ui.components.focusScale
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.LocalMotion
import tr.merve.tv.ui.theme.T

@Composable
fun SettingsScreen(
    state: MainState,
    onRail: (RailItem) -> Unit,
    onHideDead: (Boolean) -> Unit,
    onResumeLast: (Boolean) -> Unit,
    onShowLocal: (Boolean) -> Unit,
    onMotion: () -> Unit,
    onRefresh: () -> Unit,
    onUpdate: () -> Unit,
) {
    val s = state.settings
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Row(Modifier.fillMaxSize().background(C.Ink)) {
        Rail(RailItem.Settings, onRail)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 56.dp, end = 120.dp, top = 40.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Ayarlar", style = T.H1.copy(fontSize = 32.sp), modifier = Modifier.padding(bottom = 14.dp))
            SettingRow("Çalışmayan kanalları gizle", "Açılmayan yayınlar listede görünmez", Modifier.focusRequester(first), { onHideDead(!s.hideDead) }) {
                Switch(s.hideDead)
            }
            SettingRow("Açılışta son kanaldan başla", "Merve açılınca doğrudan son izlediğiniz kanal oynar", onClick = { onResumeLast(!s.resumeLast) }) {
                Switch(s.resumeLast)
            }
            SettingRow("Yerel kanalları ana sayfada göster", "Bölgesel kanallar için ayrı bir satır eklenir", onClick = { onShowLocal(!s.showLocal) }) {
                Switch(s.showLocal)
            }
            SettingRow("Animasyonlar", "Takılma olursa azaltılmış mod daha akıcı çalışır", onClick = onMotion) {
                Pill(s.motion.label)
            }
            val updated = state.list?.generatedAt?.take(16)?.replace('T', ' ')
            SettingRow(
                "Kanal listesini yenile",
                buildString {
                    append(if (state.hasRemote) "Liste her gece otomatik güncellenir" else "Otomatik güncelleme adresi henüz ayarlanmadı")
                    if (updated != null) append(" · Liste tarihi: $updated (UTC)")
                    if (state.checking) append(" · Kanallar bu TV'den test ediliyor")
                    else if (state.localAlive.isNotEmpty()) append(" · Bu TV'de ${state.localAlive.count { it.value }} kanal çalışıyor")
                },
                onClick = onRefresh,
            ) { RefreshPill(state.refreshing || state.checking) }
            state.refreshMessage?.let { Text(it, style = T.Small.copy(color = C.AccentSoft), modifier = Modifier.padding(start = 8.dp)) }
            val up = state.update
            SettingRow(
                "Uygulama güncellemesi",
                buildString {
                    append("Sürüm ${BuildConfig.VERSION_NAME}")
                    up.available?.let { append(" · Yeni sürüm hazır: ${it.versionName}") }
                    up.message?.let { append(" · $it") }
                },
                onClick = onUpdate,
            ) { Pill(if (up.available != null) "Güncelle" else "Denetle") }

            Spacer(Modifier.heightIn(min = 24.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("merve", style = T.Wordmark.copy(fontSize = 20.sp))
                    Text("  ·  Sürüm ${BuildConfig.VERSION_NAME}", style = T.Small)
                }
                Text("Kanal kaynağı: iptv-org (herkese açık yayınlar)", style = T.Small)
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 76.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = C.Surface, contentColor = C.Text,
            focusedContainerColor = C.Surface2, focusedContentColor = C.Text,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, C.Surface2), shape = RoundedCornerShape(18.dp)),
            focusedBorder = Border(BorderStroke(2.dp, C.Accent), shape = RoundedCornerShape(18.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale(1.015f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = T.Label.copy(fontSize = 17.sp))
                Text(desc, style = T.Small)
            }
            trailing()
        }
    }
}

@Composable
private fun Switch(on: Boolean) {
    val bouncy = LocalMotion.current != Motion.Off
    val x by animateDpAsState(if (on) 24.dp else 0.dp, if (bouncy) spring(dampingRatio = 0.55f, stiffness = 500f) else spring(), label = "x")
    val bg by animateColorAsState(if (on) C.Accent else C.Line, label = "bg")
    Box(Modifier.size(width = 56.dp, height = 32.dp).background(bg, RoundedCornerShape(16.dp)).padding(4.dp)) {
        Box(Modifier.offset(x = x).size(24.dp).background(C.Text, CircleShape))
    }
}

@Composable
private fun Pill(text: String, content: @Composable () -> Unit = {}) {
    Row(
        Modifier.background(C.Surface2, RoundedCornerShape(17.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
        Text(text, style = T.Label.copy(fontSize = 14.sp, color = Color.Unspecified))
    }
}

@Composable
private fun RefreshPill(busy: Boolean) {
    val t = rememberInfiniteTransition(label = "spin")
    val r = t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "r")
    Pill(if (busy) "Yenileniyor" else "Şimdi yenile") {
        Icon(
            Icons.Filled.Refresh, contentDescription = null, tint = C.Text,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = if (busy) r.value else 0f },
        )
    }
}

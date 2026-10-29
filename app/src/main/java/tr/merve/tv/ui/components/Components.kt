package tr.merve.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tr.merve.tv.data.Channel
import tr.merve.tv.data.Motion
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.LocalMotion
import tr.merve.tv.ui.theme.T

@Composable
fun focusScale(full: Float): Float = if (LocalMotion.current == Motion.Off) 1f else full

/** Channel logo on a dark tile; falls back to initials while loading or when there is no logo. */
@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier, textSize: Int = 22, showInitials: Boolean = true) {
    var loaded by remember(channel.logo) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded && showInitials) {
            Text(
                channel.initials, style = T.H2.copy(fontSize = textSize.sp), maxLines = 1,
                overflow = TextOverflow.Clip, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        }
    }
}

/** Light sweep that crosses a focused card. Read in the draw phase so it never recomposes. */
fun Modifier.shine(active: Boolean, progress: () -> Float): Modifier =
    if (!active) this else drawWithContent {
        drawContent()
        val x = size.width * (progress() * 2.2f - 0.6f)
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.13f), Color.Transparent),
                start = Offset(x - size.width * 0.25f, 0f),
                end = Offset(x + size.width * 0.25f, size.height),
            ),
        )
    }

@Composable
fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 196.dp,
    tileHeight: Dp = 110.dp,
    subtitle: String? = null,
    favorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    val sweep = rememberInfiniteTransition(label = "shine")
    val p = sweep.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "p")
    Column(
        modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().height(tileHeight).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = C.Surface, focusedContainerColor = C.Surface2,
                contentColor = C.Text, focusedContentColor = C.Text,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale(1.08f)),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, C.Line), shape = RoundedCornerShape(18.dp)),
                focusedBorder = Border(BorderStroke(3.dp, C.Accent), shape = RoundedCornerShape(18.dp)),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(C.Accent.copy(alpha = 0.45f), 14.dp)),
        ) {
            ChannelLogo(
                channel,
                Modifier.fillMaxSize().shine(focused && motion == Motion.Full) { p.value },
            )
            if (favorite) {
                Icon(
                    Icons.Filled.Favorite, contentDescription = "Favori", tint = C.Accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(16.dp),
                )
            }
            if (!channel.alive) {
                Box(Modifier.matchParentSize().background(C.Ink.copy(alpha = 0.55f)))
            }
        }
        Column(Modifier.padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(channel.name, style = T.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle ?: if (channel.alive) "Canlı" else "Şu an yayında değil", style = T.Small, maxLines = 1)
        }
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val t = rememberInfiniteTransition(label = "live")
    val a = t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Row(
        modifier.background(C.Accent.copy(alpha = 0.16f), RoundedCornerShape(15.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(8.dp)
                .graphicsLayer { alpha = if (motion == Motion.Off) 1f else a.value }
                .background(C.Accent, CircleShape),
        )
        Text("CANLI", style = T.Badge, color = C.AccentSoft)
    }
}

/** Pill button with the focus treatment used across the app. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    leading: (@Composable BoxScope.() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(26.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) C.Accent else C.Surface2,
            focusedContainerColor = if (primary) C.Accent else C.Text,
            contentColor = if (primary) C.Ink else C.Text,
            focusedContentColor = C.Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale(1.06f)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, C.Text), inset = 3.dp, shape = RoundedCornerShape(26.dp)),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 26.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (leading != null) Box(Modifier.size(20.dp), contentAlignment = Alignment.Center, content = leading)
            // Unspecified color so the text follows the Surface's focused/unfocused content color.
            Text(text, style = T.Label.copy(fontSize = 17.sp, color = Color.Unspecified))
        }
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.Body, modifier = Modifier.alpha(0.9f))
    }
}

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))

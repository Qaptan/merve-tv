package tr.merve.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tr.merve.tv.data.Motion
import tr.merve.tv.ui.MainState
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.LocalMotion
import tr.merve.tv.ui.theme.T

@Composable
fun SplashScreen(state: MainState, onDone: () -> Unit) {
    val motion = LocalMotion.current
    val letters = "merve"
    val reveal = remember { letters.map { Animatable(0f) } }
    val sweep = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }
    val loaded = rememberUpdatedState(state.loaded)
    val done = rememberUpdatedState(onDone)

    LaunchedEffect(Unit) {
        if (motion == Motion.Off) {
            reveal.forEach { it.snapTo(1f) }; sweep.snapTo(1f); tag.snapTo(1f)
        } else {
            coroutineScope {
                reveal.mapIndexed { i, a ->
                    async { delay(120L * i); a.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
                }.awaitAll()
                launch { sweep.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
                tag.animateTo(1f, tween(500))
            }
            delay(700)
        }
        while (!loaded.value) delay(50)
        done.value()
    }

    val t = rememberInfiniteTransition(label = "splash")
    val drift = t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000), RepeatMode.Reverse), label = "drift")
    val ring = t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "ring")
    val bar = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)), label = "bar")
    val animate = motion != Motion.Off

    Box(Modifier.fillMaxSize().background(C.Ink), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = if (animate) drift.value else 0.5f
            drawCircle(
                Brush.radialGradient(listOf(C.Accent.copy(alpha = 0.32f), C.Accent.copy(alpha = 0f)),
                    center = Offset(size.width * (0.3f + 0.05f * d), size.height * (0.45f - 0.06f * d)), radius = size.width * 0.32f),
                radius = size.width * 0.32f,
                center = Offset(size.width * (0.3f + 0.05f * d), size.height * (0.45f - 0.06f * d)),
            )
            drawCircle(
                Brush.radialGradient(listOf(C.Violet.copy(alpha = 0.2f), C.Violet.copy(alpha = 0f)),
                    center = Offset(size.width * (0.75f - 0.05f * d), size.height * (0.7f + 0.04f * d)), radius = size.width * 0.26f),
                radius = size.width * 0.26f,
                center = Offset(size.width * (0.75f - 0.05f * d), size.height * (0.7f + 0.04f * d)),
            )
            if (animate) {
                for (k in 0..1) {
                    val p = (ring.value + k * 0.5f) % 1f
                    drawCircle(
                        C.Text.copy(alpha = 0.08f * (1f - p)),
                        radius = size.height * (0.35f + 0.4f * p),
                        style = Stroke(width = 1.5f),
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row {
                letters.forEachIndexed { i, ch ->
                    Text(
                        ch.toString(),
                        style = T.Wordmark.copy(fontSize = 150.sp, letterSpacing = (-3).sp, color = if (i == letters.lastIndex) C.Accent else C.Text),
                        modifier = Modifier.graphicsLayer {
                            val v = reveal[i].value
                            alpha = v
                            translationY = (1f - v) * 60f
                            rotationZ = (1f - v) * 6f
                        },
                    )
                }
            }
            Box(
                Modifier.width(340.dp).height(3.dp)
                    .graphicsLayer { scaleX = sweep.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                    .background(C.Accent, RoundedCornerShape(2.dp)),
            )
            Text(
                "TÜRK KANALLARI, TEK EKRANDA",
                style = T.Body.copy(fontSize = 19.sp, letterSpacing = 6.sp),
                modifier = Modifier.graphicsLayer { alpha = tag.value; translationY = (1f - tag.value) * 16f },
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.width(220.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Surface2)) {
                Box(
                    Modifier.fillMaxWidth(0.4f).height(4.dp)
                        .graphicsLayer { translationX = (bar.value * 3.6f - 1.1f) * size.width }
                        .background(C.Accent, RoundedCornerShape(2.dp)),
                )
            }
            val n = state.list?.channels?.size
            Text(if (n != null) "$n kanal hazırlanıyor" else "Kanallar hazırlanıyor", style = T.Small)
        }
    }
}

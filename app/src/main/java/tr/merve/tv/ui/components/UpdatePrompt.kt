package tr.merve.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tr.merve.tv.BuildConfig
import tr.merve.tv.ui.UpdateState
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.T

@Composable
fun UpdatePrompt(update: UpdateState, onInstall: () -> Unit, onLater: () -> Unit) {
    val u = update.available ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onLater)

    Box(Modifier.fillMaxSize().background(Color(0xCC05060A)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(560.dp)
                .background(C.Surface, RoundedCornerShape(28.dp))
                .border(BorderStroke(1.dp, C.Line), RoundedCornerShape(28.dp))
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Yeni sürüm hazır", style = T.H1.copy(fontSize = 30.sp))
            Text("Merve ${BuildConfig.VERSION_NAME} → ${u.versionName}", style = T.Body)
            val p = update.progress
            if (p != null) {
                Box(Modifier.fillMaxWidth().height(8.dp).background(C.Line, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).height(8.dp).background(C.Accent, RoundedCornerShape(4.dp)))
                }
                Text("İndiriliyor %${(p * 100).toInt()}", style = T.Small)
            } else {
                update.message?.let { Text(it, style = T.Small.copy(color = C.AccentSoft)) }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PillButton("Şimdi güncelle", onInstall, Modifier.focusRequester(focus), primary = true)
                    PillButton("Sonra", onLater)
                }
            }
        }
    }
}

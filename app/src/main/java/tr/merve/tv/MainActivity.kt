package tr.merve.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tr.merve.tv.data.Motion
import tr.merve.tv.ui.Dest
import tr.merve.tv.ui.ListKey
import tr.merve.tv.ui.MainViewModel
import tr.merve.tv.ui.components.RailItem
import tr.merve.tv.ui.components.UpdatePrompt
import tr.merve.tv.ui.screens.GuideScreen
import tr.merve.tv.ui.screens.HomeScreen
import tr.merve.tv.ui.screens.PlayerScreen
import tr.merve.tv.ui.screens.SearchScreen
import tr.merve.tv.ui.screens.SettingsScreen
import tr.merve.tv.ui.screens.SplashScreen
import tr.merve.tv.ui.theme.C
import tr.merve.tv.ui.theme.MerveTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val motion = state.settings.motion
            // Lay out on a fixed 1280-wide canvas whatever the TV resolution is.
            val base = LocalDensity.current
            val widthPx = LocalContext.current.resources.displayMetrics.widthPixels
            CompositionLocalProvider(LocalDensity provides Density(widthPx / 1280f, base.fontScale)) {
            MerveTheme(motion) {
                BackHandler { if (!vm.back()) finish() }

                val onRail: (RailItem) -> Unit = { item ->
                    vm.navigate(
                        when (item) {
                            RailItem.Home -> Dest.Home
                            RailItem.Guide -> Dest.Guide()
                            RailItem.Search -> Dest.Search
                            RailItem.Favorites -> Dest.Guide(ListKey.FAVORITES)
                            RailItem.Settings -> Dest.Settings
                        },
                    )
                }
                val play: (String, String) -> Unit = { id, key -> vm.navigate(Dest.Player(id, key)) }

                Box(Modifier.fillMaxSize().background(C.Ink)) {
                    AnimatedContent(
                        targetState = vm.stack.last(),
                        transitionSpec = {
                            val d = if (motion == Motion.Off) 0 else 380
                            (fadeIn(tween(d)) + scaleIn(tween(d), initialScale = if (motion == Motion.Full) 0.97f else 1f))
                                .togetherWith(fadeOut(tween(d / 2)))
                        },
                        label = "screens",
                    ) { dest ->
                        when (dest) {
                            Dest.Splash -> SplashScreen(state) {
                                val last = state.settings.lastChannel
                                if (state.settings.resumeLast && last != null && state.byId[last] != null) {
                                    vm.replace(Dest.Home)
                                    vm.navigate(Dest.Player(last, ListKey.ALL))
                                } else {
                                    vm.replace(Dest.Home)
                                }
                            }
                            Dest.Home -> HomeScreen(state, onRail, play, vm::toggleFavorite)
                            is Dest.Guide -> GuideScreen(state, dest.group, onRail, play, vm::toggleFavorite)
                            is Dest.Player -> PlayerScreen(
                                channels = state.listFor(dest.listKey),
                                startId = dest.channelId,
                                http = vm.http,
                                onWatched = vm::markWatched,
                                onExit = { vm.back() },
                            )
                            Dest.Search -> SearchScreen(state, onRail, play)
                            Dest.Settings -> SettingsScreen(
                                state, onRail,
                                onHideDead = vm::setHideDead,
                                onResumeLast = vm::setResumeLast,
                                onShowLocal = vm::setShowLocal,
                                onMotion = vm::cycleMotion,
                                onRefresh = { vm.refresh() },
                                onUpdate = { if (state.update.available != null) vm.installUpdate() else vm.checkForUpdate(manual = true) },
                            )
                        }
                    }
                    val dest = vm.stack.last()
                    if (state.update.showPrompt && dest != Dest.Splash && dest !is Dest.Player) {
                        UpdatePrompt(state.update, onInstall = vm::installUpdate, onLater = vm::dismissUpdate)
                    }
                }
            }
            }
        }
    }
}

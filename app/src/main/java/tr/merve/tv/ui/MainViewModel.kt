package tr.merve.tv.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tr.merve.tv.MerveApp
import tr.merve.tv.data.Channel
import tr.merve.tv.data.ChannelList
import tr.merve.tv.data.Group
import tr.merve.tv.data.Motion
import tr.merve.tv.data.Settings

sealed interface Dest {
    data object Splash : Dest
    data object Home : Dest
    data class Guide(val group: String? = null) : Dest
    data class Player(val channelId: String, val listKey: String) : Dest
    data object Search : Dest
    data object Settings : Dest
}

object ListKey {
    const val ALL = "all"
    const val FAVORITES = "fav"
    const val RECENT = "recent"
}

data class MainState(
    val list: ChannelList? = null,
    val settings: Settings = Settings(),
    val refreshing: Boolean = false,
    val refreshMessage: String? = null,
    val hasRemote: Boolean = false,
) {
    val loaded: Boolean get() = list != null

    val channels: List<Channel> by lazy {
        list?.channels.orEmpty().filter { !settings.hideDead || it.alive }
    }
    val byId: Map<String, Channel> by lazy { list?.channels.orEmpty().associateBy { it.id } }
    val groups: List<Group> get() = list?.groups.orEmpty()

    fun inGroup(id: String): List<Channel> = channels.filter { it.group == id }
    val favorites: List<Channel> get() = channels.filter { it.id in settings.favorites }
    val recent: List<Channel> get() = settings.recent.mapNotNull { byId[it] }

    fun listFor(key: String): List<Channel> = when (key) {
        ListKey.ALL -> channels
        ListKey.FAVORITES -> favorites
        ListKey.RECENT -> recent
        else -> inGroup(key)
    }.ifEmpty { channels }

    fun groupName(id: String): String = groups.firstOrNull { it.id == id }?.name ?: ""
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as MerveApp).container
    private val repo = container.channels
    private val prefs = container.prefs

    private val list = MutableStateFlow<ChannelList?>(null)
    private val refreshing = MutableStateFlow(false)
    private val refreshMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<MainState> =
        combine(list, prefs.settings, refreshing, refreshMessage) { l, s, r, m ->
            MainState(l, s, r, m, repo.hasRemote)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainState())

    val http get() = container.http

    val stack = mutableStateListOf<Dest>(Dest.Splash)

    init {
        viewModelScope.launch {
            list.value = repo.loadLocal()
            if (repo.isStale()) refresh(silent = true)
        }
    }

    fun refresh(silent: Boolean = false) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            repo.refresh()
                .onSuccess {
                    list.value = it
                    refreshMessage.value = if (silent) null else "Liste güncellendi · ${it.channels.size} kanal"
                }
                .onFailure { if (!silent) refreshMessage.value = it.message ?: "Güncellenemedi" }
            refreshing.value = false
        }
    }

    fun navigate(dest: Dest) {
        if (stack.lastOrNull() != dest) stack.add(dest)
    }

    fun replace(dest: Dest) {
        stack.clear()
        stack.add(dest)
    }

    /** @return false when there is nothing left to go back to. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun toggleFavorite(id: String) = viewModelScope.launch { prefs.toggleFavorite(id) }
    fun markWatched(id: String) = viewModelScope.launch { prefs.markWatched(id) }
    fun setHideDead(v: Boolean) = viewModelScope.launch { prefs.setHideDead(v) }
    fun setResumeLast(v: Boolean) = viewModelScope.launch { prefs.setResumeLast(v) }
    fun setShowLocal(v: Boolean) = viewModelScope.launch { prefs.setShowLocal(v) }
    fun cycleMotion() = viewModelScope.launch {
        val m = state.value.settings.motion
        prefs.setMotion(Motion.entries[(m.ordinal + 1) % Motion.entries.size])
    }
}

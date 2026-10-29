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
    /** Result of the TV's own stream check; overrides the list's verdict when present. */
    val localAlive: Map<String, Boolean> = emptyMap(),
    val checking: Boolean = false,
) {
    val loaded: Boolean get() = list != null

    private val all: List<Channel> by lazy {
        list?.channels.orEmpty().map { c -> localAlive[c.id]?.let { c.copy(alive = it) } ?: c }
    }
    val channels: List<Channel> by lazy { all.filter { !settings.hideDead || it.alive } }
    val byId: Map<String, Channel> by lazy { all.associateBy { it.id } }
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
    private val checker = container.checker
    private val localAlive = MutableStateFlow(emptyMap<String, Boolean>())
    private val checking = MutableStateFlow(false)
    private val status = combine(refreshing, refreshMessage, checking) { r, m, c -> Triple(r, m, c) }

    val state: StateFlow<MainState> =
        combine(list, prefs.settings, status, localAlive) { l, s, st, a ->
            MainState(l, s, st.first, st.second, repo.hasRemote, a, st.third)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainState())

    val http get() = container.http

    val stack = mutableStateListOf<Dest>(Dest.Splash)

    init {
        viewModelScope.launch {
            localAlive.value = checker.load()
            list.value = repo.loadLocal()
            if (repo.isStale()) refresh(silent = true)
            else if (checker.isStale()) checkStreams()
        }
    }

    fun checkStreams() {
        val channels = list.value?.channels ?: return
        if (checking.value) return
        viewModelScope.launch {
            checking.value = true
            localAlive.value = checker.checkAll(channels)
            checking.value = false
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
            checkStreams()
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

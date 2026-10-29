package tr.merve.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class Motion(val label: String) { Full("Tam"), Reduced("Azaltılmış"), Off("Kapalı") }

data class Settings(
    val favorites: Set<String> = emptySet(),
    val recent: List<String> = emptyList(),
    val lastChannel: String? = null,
    val hideDead: Boolean = true,
    val resumeLast: Boolean = false,
    val showLocal: Boolean = false,
    val motion: Motion = Motion.Full,
)

private val Context.store: DataStore<Preferences> by preferencesDataStore("merve")

class Prefs(context: Context) {
    private val store = context.store

    private object K {
        val favorites = stringSetPreferencesKey("favorites")
        val recent = stringPreferencesKey("recent")
        val last = stringPreferencesKey("last")
        val hideDead = booleanPreferencesKey("hide_dead")
        val resumeLast = booleanPreferencesKey("resume_last")
        val showLocal = booleanPreferencesKey("show_local")
        val motion = intPreferencesKey("motion")
    }

    val settings: Flow<Settings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            Settings(
                favorites = p[K.favorites].orEmpty(),
                recent = p[K.recent]?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
                lastChannel = p[K.last],
                hideDead = p[K.hideDead] ?: true,
                resumeLast = p[K.resumeLast] ?: false,
                showLocal = p[K.showLocal] ?: false,
                motion = Motion.entries.getOrElse(p[K.motion] ?: 0) { Motion.Full },
            )
        }

    suspend fun toggleFavorite(id: String) = store.edit { p ->
        val cur = p[K.favorites].orEmpty()
        p[K.favorites] = if (id in cur) cur - id else cur + id
    }

    suspend fun markWatched(id: String) = store.edit { p ->
        val recent = p[K.recent]?.split('|')?.filter { it.isNotBlank() }.orEmpty()
        p[K.recent] = (listOf(id) + (recent - id)).take(12).joinToString("|")
        p[K.last] = id
    }

    suspend fun setHideDead(v: Boolean) = store.edit { it[K.hideDead] = v }
    suspend fun setResumeLast(v: Boolean) = store.edit { it[K.resumeLast] = v }
    suspend fun setShowLocal(v: Boolean) = store.edit { it[K.showLocal] = v }
    suspend fun setMotion(m: Motion) = store.edit { it[K.motion] = m.ordinal }
}

package com.minipicasa

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nuvora_prefs")

internal object StartupState {
    @Volatile var preferencesLoaded: Boolean = false
}

private val KEY_APPEARANCE = intPreferencesKey("appearance")     // 0 = Graphite, 1 = Adaptive
private val KEY_FOLDER_VIEW = intPreferencesKey("folder_view")   // 0 = cuadrícula, 1 = lista

data class AppPrefs(val appearance: Int = 0, val folderView: Int = 0)

@Composable
fun rememberAppPrefs(): State<AppPrefs> {
    val appContext = LocalContext.current.applicationContext

    // Graphite/cuadrícula son los valores iniciales mientras DataStore carga.
    // La vista de carpetas anterior se conserva como respaldo si aún no existe
    // una preferencia equivalente en DataStore.
    return produceState(initialValue = AppPrefs(), appContext) {
        val legacyView = appContext
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getInt("folder_view", 0)

        appContext.dataStore.data
            .map { preferences ->
                AppPrefs(
                    appearance = preferences[KEY_APPEARANCE] ?: 0,
                    folderView = preferences[KEY_FOLDER_VIEW] ?: legacyView
                )
            }
            .collect { loadedPrefs ->
                value = loadedPrefs
                StartupState.preferencesLoaded = true
            }
    }
}

suspend fun setAppearance(context: Context, value: Int) {
    context.applicationContext.dataStore.edit { it[KEY_APPEARANCE] = value }
}

suspend fun setFolderView(context: Context, value: Int) {
    context.applicationContext.dataStore.edit { it[KEY_FOLDER_VIEW] = value }
}

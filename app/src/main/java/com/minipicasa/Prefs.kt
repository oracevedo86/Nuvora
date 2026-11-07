package com.minipicasa

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nuvora_prefs")

private val KEY_APPEARANCE = intPreferencesKey("appearance")     // 0 = Graphite, 1 = Adaptive
private val KEY_FOLDER_VIEW = intPreferencesKey("folder_view")   // 0 = cuadrícula, 1 = lista

data class AppPrefs(val appearance: Int = 0, val folderView: Int = 0)

@Composable
fun rememberAppPrefs(): State<AppPrefs> {
    val appContext = LocalContext.current.applicationContext
    // Si venías de la versión anterior, conserva tu vista de carpetas.
    val legacyView = remember {
        appContext.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("folder_view", 0)
    }
    val flow = remember {
        appContext.dataStore.data.map {
            AppPrefs(it[KEY_APPEARANCE] ?: 0, it[KEY_FOLDER_VIEW] ?: legacyView)
        }
    }
    // Lectura inicial rápida para que no parpadee el tema equivocado al abrir.
    val initial = remember { runBlocking { flow.first() } }
    return flow.collectAsState(initial)
}

suspend fun setAppearance(context: Context, value: Int) {
    context.applicationContext.dataStore.edit { it[KEY_APPEARANCE] = value }
}

suspend fun setFolderView(context: Context, value: Int) {
    context.applicationContext.dataStore.edit { it[KEY_FOLDER_VIEW] = value }
}

package com.amir.filamenttracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.amir.filamenttracker.domain.model.InventoryView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** A corrupted settings file is replaced by empty settings: the start screen must never crash on it. */
internal fun createSettingsStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
    produceFile = produceFile,
)

class InventoryViewPreference @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val view: Flow<InventoryView> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { prefs ->
        val stored = prefs[KEY]
        InventoryView.entries.firstOrNull { it.name == stored } ?: InventoryView.BY_MATERIAL
    }

    suspend fun set(view: InventoryView) {
        store.edit { it[KEY] = view.name }
    }

    private companion object {
        val KEY = stringPreferencesKey("inventory_view")
    }
}

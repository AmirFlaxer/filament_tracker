package com.amir.filamenttracker.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.amir.filamenttracker.domain.model.InventoryView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class InventoryViewPreference @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val view: Flow<InventoryView> = store.data.map { prefs ->
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

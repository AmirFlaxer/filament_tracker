package com.amir.filamenttracker.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.amir.filamenttracker.domain.model.InventoryView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InventoryViewPreferenceTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var pref: InventoryViewPreference

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("x.preferences_pb").also { it.delete() } }
        pref = InventoryViewPreference(store)
    }

    @After fun tearDown() { scope.cancel() }

    @Test
    fun `defaults to by material`() = runTest {
        assertEquals(InventoryView.BY_MATERIAL, pref.view.first())
    }

    @Test
    fun `remembers the chosen view`() = runTest {
        pref.set(InventoryView.TABLE_MATERIAL)
        assertEquals(InventoryView.TABLE_MATERIAL, pref.view.first())
    }

    @Test
    fun `unknown stored value falls back to by material`() = runTest {
        store.edit { it[stringPreferencesKey("inventory_view")] = "NO_SUCH_VIEW" }
        assertEquals(InventoryView.BY_MATERIAL, pref.view.first())
    }
}

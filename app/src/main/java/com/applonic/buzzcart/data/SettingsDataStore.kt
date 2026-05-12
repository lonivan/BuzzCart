package com.applonic.buzzcart.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

// DataStore wrapper for saving simple app settings like selected store and radius
class SettingsDataStore(
    private val context: Context
) {
    companion object {
        val STORE_NAME_KEY = stringPreferencesKey("store_name")
        val RADIUS_KEY = floatPreferencesKey("radius")
    }

    suspend fun saveSettings(storeName: String, radius: Float) {
        context.dataStore.edit { preferences ->
            // Save selected store name
            preferences[STORE_NAME_KEY] = storeName

            // Save selected radius
            preferences[RADIUS_KEY] = radius
        }
    }

    fun getSettingsFlow() = context.dataStore.data.map { preferences ->
        // Read saved store name (default = "REWE" if not saved yet)
        val storeName = preferences[STORE_NAME_KEY] ?: "REWE"

        // Read saved radius (default = 200f if not saved yet)
        val radius = preferences[RADIUS_KEY] ?: 200f

        storeName to radius
    }
}
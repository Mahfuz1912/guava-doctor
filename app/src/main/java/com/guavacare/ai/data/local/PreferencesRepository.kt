package com.guavacare.ai.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.guavacare.ai.core.constants.UpdateConstants
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.ThemeMode
import com.guavacare.ai.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.guavaCareDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "guavacare_preferences"
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STRICT_QUALITY_GATE = booleanPreferencesKey("strict_quality_gate")
        val SHOW_LEAF_GUIDE = booleanPreferencesKey("show_leaf_guide")
        val AUTO_SAVE_SCANS = booleanPreferencesKey("auto_save_scans")
        val DEV_MOCK_MODE = booleanPreferencesKey("dev_mock_mode")
        val UPDATE_FEED_URL = stringPreferencesKey("update_feed_url")
        val GUEST_SCAN_COUNT = intPreferencesKey("guest_scan_count")
    }

    val preferencesFlow: Flow<UserPreferences> = context.guavaCareDataStore.data.map { prefs ->
        val rawUrl = prefs[Keys.UPDATE_FEED_URL]?.trim().orEmpty()
        UserPreferences(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
            strictQualityGate = prefs[Keys.STRICT_QUALITY_GATE] ?: true,
            showLeafGuide = prefs[Keys.SHOW_LEAF_GUIDE] ?: true,
            autoSaveScans = prefs[Keys.AUTO_SAVE_SCANS] ?: true,
            devMockModeEnabled = false,
            updateFeedUrl = rawUrl.ifBlank { UpdateConstants.DEFAULT_UPDATE_FEED_URL },
            guestScanCount = (prefs[Keys.GUEST_SCAN_COUNT] ?: 0).coerceAtLeast(0)
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.guavaCareDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.guavaCareDataStore.edit { it[Keys.THEME_MODE] = mode.key }
    }

    suspend fun setStrictQualityGate(enabled: Boolean) {
        context.guavaCareDataStore.edit { it[Keys.STRICT_QUALITY_GATE] = enabled }
    }

    suspend fun setShowLeafGuide(enabled: Boolean) {
        context.guavaCareDataStore.edit { it[Keys.SHOW_LEAF_GUIDE] = enabled }
    }

    suspend fun setAutoSaveScans(enabled: Boolean) {
        context.guavaCareDataStore.edit { it[Keys.AUTO_SAVE_SCANS] = enabled }
    }

    suspend fun setDevMockMode(enabled: Boolean) {
        context.guavaCareDataStore.edit { it[Keys.DEV_MOCK_MODE] = enabled }
    }

    suspend fun setUpdateFeedUrl(url: String) {
        val clean = url.trim().ifBlank { UpdateConstants.DEFAULT_UPDATE_FEED_URL }
        context.guavaCareDataStore.edit { it[Keys.UPDATE_FEED_URL] = clean }
    }

    suspend fun incrementGuestScanCount(): Int {
        var updated = 0
        context.guavaCareDataStore.edit { prefs ->
            val current = (prefs[Keys.GUEST_SCAN_COUNT] ?: 0).coerceAtLeast(0)
            updated = current + 1
            prefs[Keys.GUEST_SCAN_COUNT] = updated
        }
        return updated
    }

    suspend fun setGuestScanCount(count: Int) {
        context.guavaCareDataStore.edit { it[Keys.GUEST_SCAN_COUNT] = count.coerceAtLeast(0) }
    }
}

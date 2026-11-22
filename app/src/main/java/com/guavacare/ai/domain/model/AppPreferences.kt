package com.guavacare.ai.domain.model

import com.guavacare.ai.core.constants.UpdateConstants

enum class AppLanguage(val code: String, val nativeLabel: String) {
    ENGLISH("en", "English"),
    BANGLA("bn", "বাংলা");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: LIGHT
    }
}

data class UserPreferences(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val strictQualityGate: Boolean = true,
    val showLeafGuide: Boolean = true,
    val autoSaveScans: Boolean = false,
    val devMockModeEnabled: Boolean = false,
    val updateFeedUrl: String = UpdateConstants.DEFAULT_UPDATE_FEED_URL
)

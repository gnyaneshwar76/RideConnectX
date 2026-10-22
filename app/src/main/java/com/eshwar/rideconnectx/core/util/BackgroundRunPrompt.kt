package com.eshwar.rideconnectx.core.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** When to ask for background run on the Navigation page. Pure, so it is unit-tested. */
object BackgroundRunRules {
    /** One-time pop-up: only on the first visit after install, and never if already on. */
    fun showPopup(enabled: Boolean, shownBefore: Boolean, firstOpen: Boolean) =
        !enabled && !shownBefore && firstOpen

    /** Reminder card: exactly while background run is off. */
    fun showReminder(enabled: Boolean) = !enabled
}

private val Context.bgRunPrompt: DataStore<Preferences> by preferencesDataStore(
    name = "bg_run_prompt",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** The "pop-up already shown" flag. Lives in app data, so a fresh install resets it. */
@Singleton
class BackgroundRunPromptStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val shownKey = booleanPreferencesKey("popup_shown")

    val shown: Flow<Boolean> = context.bgRunPrompt.data.map { it[shownKey] ?: false }

    suspend fun markShown() { context.bgRunPrompt.edit { it[shownKey] = true } }
}

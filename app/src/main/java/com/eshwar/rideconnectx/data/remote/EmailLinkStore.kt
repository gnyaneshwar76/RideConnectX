package com.eshwar.rideconnectx.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eshwar.rideconnectx.domain.model.EmailLinkFlow
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.emailLinkPrefs by preferencesDataStore("email_link")

/** The address a sign-in link was sent to, so opening it on this phone needs no typing. */
@Singleton
class EmailLinkStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("pending_email")

    private val sentKey = longPreferencesKey("pending_sent_at")
    private val requestsKey = stringPreferencesKey("request_times")

    suspend fun sentAt(): Long? = context.emailLinkPrefs.data.first()[sentKey]
    suspend fun requests(): List<Long> =
        context.emailLinkPrefs.data.first()[requestsKey]?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    /** Saves the address and send time, and adds the time to the request list (older than the window are dropped). */
    suspend fun markSent(email: String, now: Long) {
        val kept = requests().filter { now - it < EmailLinkFlow.REQUEST_WINDOW_MS } + now
        context.emailLinkPrefs.edit {
            it[key] = email
            it[sentKey] = now
            it[requestsKey] = kept.joinToString(",")
        }
    }

    suspend fun get(): String? = context.emailLinkPrefs.data.first()[key]
    suspend fun clear() { context.emailLinkPrefs.edit { it.remove(key); it.remove(sentKey) } }
}

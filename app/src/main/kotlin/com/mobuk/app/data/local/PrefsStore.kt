package com.mobuk.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobuk.app.domain.model.UserPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mob_prefs")

/** Stores the whole [UserPrefs] object as one JSON string; simple, atomic and easy to extend. */
class PrefsStore(private val context: Context) {

    private val key = stringPreferencesKey("user_prefs_json")

    val prefs: Flow<UserPrefs> = context.prefsDataStore.data.map { data ->
        data[key]?.let { runCatching { AppJson.decodeFromString(UserPrefs.serializer(), it) }.getOrNull() } ?: UserPrefs()
    }

    suspend fun current(): UserPrefs = prefs.first()

    suspend fun update(transform: (UserPrefs) -> UserPrefs) {
        context.prefsDataStore.edit { data ->
            val current = data[key]?.let { runCatching { AppJson.decodeFromString(UserPrefs.serializer(), it) }.getOrNull() } ?: UserPrefs()
            data[key] = AppJson.encodeToString(UserPrefs.serializer(), transform(current))
        }
    }
}

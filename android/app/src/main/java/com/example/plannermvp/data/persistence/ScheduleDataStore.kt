package com.example.plannermvp.data.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "planner_schedule")

object ScheduleDataStore {

    private val KEY_JSON = stringPreferencesKey("schedule_snapshot")

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun load(context: Context): ScheduleSnapshot? {
        val prefs = context.dataStore.data.first()
        val raw = prefs[KEY_JSON] ?: return null
        return runCatching {
            json.decodeFromString(ScheduleSnapshot.serializer(), raw)
        }.getOrNull()
    }

    suspend fun save(context: Context, snapshot: ScheduleSnapshot) {
        val raw = json.encodeToString(ScheduleSnapshot.serializer(), snapshot)
        context.dataStore.edit { it[KEY_JSON] = raw }
    }
}

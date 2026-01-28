package com.example.plannermvp.data.persistence

import android.content.Context

/**
 * ✅ SharedPreferences 기반 저장소 (의존성 추가 0)
 */
object ScheduleDataStore {

    private const val PREF_NAME = "planner_schedule"
    private const val KEY_SNAPSHOT = "snapshot_v4"

    suspend fun save(context: Context, snapshot: ScheduleSnapshot) {
        val raw = SnapshotCodec.encode(snapshot)
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNAPSHOT, raw)
            .apply()
    }

    suspend fun load(context: Context): ScheduleSnapshot? {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null)
            ?: return null

        return runCatching { SnapshotCodec.decode(raw) }.getOrNull()
    }

    suspend fun clearAll(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

package com.example.plannermvp.data.persistence

import android.content.Context

object ScheduleRepository {

    suspend fun load(context: Context): ScheduleSnapshot? {
        return ScheduleDataStore.load(context)
    }

    suspend fun save(context: Context, snapshot: ScheduleSnapshot) {
        ScheduleDataStore.save(context, snapshot)
    }

    suspend fun clearAll(context: Context) {
        ScheduleDataStore.clearAll(context)
    }
}

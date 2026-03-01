package com.wirewhisper.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist", indices = [Index("value", unique = true)])
data class WatchlistEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,          // "hostname" or "ip"
    val value: String,         // lowercased/normalized
    val label: String? = null, // optional user note
    val createdAt: Long = System.currentTimeMillis(),
)

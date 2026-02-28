package com.wirewhisper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GeoCacheDao {
    @Query("SELECT * FROM geo_cache WHERE ip = :ip AND updatedAt > :minTimestamp LIMIT 1")
    suspend fun get(ip: String, minTimestamp: Long): GeoCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GeoCacheEntity)

    @Query("DELETE FROM geo_cache WHERE updatedAt < :olderThan")
    suspend fun evictOlderThan(olderThan: Long)
}

package com.wirewhisper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WatchlistEntryEntity>>

    @Query("SELECT * FROM watchlist ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<WatchlistEntryEntity>

    @Query("SELECT * FROM watchlist WHERE id = :id")
    suspend fun getById(id: Long): WatchlistEntryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: WatchlistEntryEntity): Long

    @Query("DELETE FROM watchlist WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM watchlist WHERE value = :value")
    suspend fun deleteByValue(value: String)
}

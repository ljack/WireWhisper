package com.wirewhisper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flows: List<FlowEntity>)

    @Query("SELECT * FROM flows ORDER BY lastSeen DESC")
    fun getAllFlows(): Flow<List<FlowEntity>>

    @Query("""
        SELECT * FROM flows
        WHERE (:packageName IS NULL OR packageName = :packageName)
          AND (:country IS NULL OR country = :country)
          AND (:protocol IS NULL OR protocol = :protocol)
          AND lastSeen >= :sinceMs
        ORDER BY lastSeen DESC
    """)
    fun getFlowsFiltered(
        packageName: String?,
        country: String?,
        protocol: Int?,
        sinceMs: Long = 0,
    ): Flow<List<FlowEntity>>

    @Query("SELECT * FROM flows WHERE id = :id")
    suspend fun getFlowById(id: Long): FlowEntity?

    @Query("SELECT DISTINCT packageName FROM flows WHERE packageName IS NOT NULL ORDER BY packageName")
    fun getDistinctApps(): Flow<List<String>>

    @Query("SELECT DISTINCT country FROM flows WHERE country IS NOT NULL ORDER BY country")
    fun getDistinctCountries(): Flow<List<String>>

    @Query("DELETE FROM flows WHERE lastSeen < :beforeMs")
    suspend fun deleteFlowsBefore(beforeMs: Long)

    @Query("DELETE FROM flows")
    suspend fun deleteAll()
}

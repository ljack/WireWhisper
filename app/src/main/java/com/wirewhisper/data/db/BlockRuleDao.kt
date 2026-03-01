package com.wirewhisper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockRuleDao {
    @Query("SELECT * FROM block_rules")
    fun getAllRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM block_rules")
    suspend fun getAllRulesOnce(): List<BlockRuleEntity>

    @Insert
    suspend fun insert(rule: BlockRuleEntity)

    @Query("DELETE FROM block_rules WHERE packageName = :packageName AND hostname IS NULL AND countryCode IS NULL")
    suspend fun deleteAppRule(packageName: String)

    @Query("DELETE FROM block_rules WHERE packageName = :packageName AND hostname = :hostname")
    suspend fun deleteHostnameRule(packageName: String, hostname: String)

    @Query("DELETE FROM block_rules WHERE countryCode = :countryCode AND packageName IS NULL")
    suspend fun deleteCountryRule(countryCode: String)
}

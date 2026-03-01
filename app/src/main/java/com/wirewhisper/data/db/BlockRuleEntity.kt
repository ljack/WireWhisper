package com.wirewhisper.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "block_rules",
    indices = [Index("packageName"), Index("hostname"), Index("countryCode")],
)
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String? = null,  // null for country-level blocks
    val hostname: String? = null,     // null = app-level block
    val countryCode: String? = null,  // non-null for country-level blocks
    val createdAt: Long = System.currentTimeMillis(),
)

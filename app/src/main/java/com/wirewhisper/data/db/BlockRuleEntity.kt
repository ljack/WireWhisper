package com.wirewhisper.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "block_rules",
    indices = [Index("packageName"), Index("hostname")],
)
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val hostname: String? = null,  // null = app-level block
    val createdAt: Long = System.currentTimeMillis(),
)

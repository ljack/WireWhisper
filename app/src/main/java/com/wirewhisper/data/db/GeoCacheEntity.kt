package com.wirewhisper.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geo_cache")
data class GeoCacheEntity(
    @PrimaryKey
    val ip: String,
    val countryCode: String,
    val countryName: String?,
    val city: String?,
    val asn: String?,
    val org: String?,
    val updatedAt: Long,    // epoch millis
)

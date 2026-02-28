package com.wirewhisper.data.repository

import com.wirewhisper.core.model.FlowRecord
import com.wirewhisper.data.db.FlowDao
import com.wirewhisper.data.db.FlowEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Repository interface for persisted flow records.
 * Decouples the flow tracking pipeline from the storage backend.
 */
interface FlowRepository {
    /** Non-blocking batch insert. Implementations should handle threading internally. */
    fun insertBatch(records: List<FlowRecord>)

    fun getAllFlows(): Flow<List<FlowRecord>>

    fun getFlowsFiltered(
        packageName: String? = null,
        country: String? = null,
        protocolNumber: Int? = null,
        sinceMs: Long = 0,
    ): Flow<List<FlowRecord>>

    suspend fun getFlowById(id: Long): FlowRecord?

    fun getDistinctApps(): Flow<List<String>>
    fun getDistinctCountries(): Flow<List<String>>

    suspend fun deleteFlowsBefore(beforeMs: Long)
    suspend fun deleteAll()
}

/**
 * Room-backed repository with background batch writes.
 * Insert operations are fire-and-forget on [Dispatchers.IO] to avoid
 * blocking the packet processing pipeline.
 */
class RoomFlowRepository(
    private val dao: FlowDao,
    private val scope: CoroutineScope,
) : FlowRepository {

    override fun insertBatch(records: List<FlowRecord>) {
        scope.launch(Dispatchers.IO) {
            val entities = records.map { FlowEntity.fromFlowRecord(it) }
            dao.insertAll(entities)
        }
    }

    override fun getAllFlows(): Flow<List<FlowRecord>> =
        dao.getAllFlows().map { list -> list.map { it.toFlowRecord() } }

    override fun getFlowsFiltered(
        packageName: String?,
        country: String?,
        protocolNumber: Int?,
        sinceMs: Long,
    ): Flow<List<FlowRecord>> =
        dao.getFlowsFiltered(packageName, country, protocolNumber, sinceMs)
            .map { list -> list.map { it.toFlowRecord() } }

    override suspend fun getFlowById(id: Long): FlowRecord? =
        dao.getFlowById(id)?.toFlowRecord()

    override fun getDistinctApps(): Flow<List<String>> = dao.getDistinctApps()
    override fun getDistinctCountries(): Flow<List<String>> = dao.getDistinctCountries()

    override suspend fun deleteFlowsBefore(beforeMs: Long) = dao.deleteFlowsBefore(beforeMs)
    override suspend fun deleteAll() = dao.deleteAll()
}

/**
 * In-memory repository for early development / testing.
 * Uses the same interface so it can be swapped in via [com.wirewhisper.WireWhisperApp].
 */
class InMemoryFlowRepository : FlowRepository {

    private val store = mutableListOf<FlowRecord>()
    private val _flows = MutableStateFlow<List<FlowRecord>>(emptyList())

    override fun insertBatch(records: List<FlowRecord>) {
        synchronized(store) {
            store.addAll(records)
            _flows.value = store.toList()
        }
    }

    override fun getAllFlows(): Flow<List<FlowRecord>> = _flows

    override fun getFlowsFiltered(
        packageName: String?,
        country: String?,
        protocolNumber: Int?,
        sinceMs: Long,
    ): Flow<List<FlowRecord>> = _flows.map { list ->
        list.filter { record ->
            (packageName == null || record.packageName == packageName) &&
            (country == null || record.country == country) &&
            (protocolNumber == null || record.protocol.number == protocolNumber) &&
            record.lastSeen >= sinceMs
        }
    }

    override suspend fun getFlowById(id: Long): FlowRecord? = null // No ID concept in memory

    override fun getDistinctApps(): Flow<List<String>> = _flows.map { list ->
        list.mapNotNull { it.packageName }.distinct().sorted()
    }
    override fun getDistinctCountries(): Flow<List<String>> = _flows.map { list ->
        list.mapNotNull { it.country }.distinct().sorted()
    }

    override suspend fun deleteFlowsBefore(beforeMs: Long) {
        synchronized(store) {
            store.removeAll { it.lastSeen < beforeMs }
            _flows.value = store.toList()
        }
    }

    override suspend fun deleteAll() {
        synchronized(store) {
            store.clear()
            _flows.value = emptyList()
        }
    }
}

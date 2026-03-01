package com.wirewhisper.firewall

import com.wirewhisper.data.db.BlockRuleDao
import com.wirewhisper.data.db.BlockRuleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockingEngineTest {

    private class FakeBlockRuleDao : BlockRuleDao {
        val rules = mutableListOf<BlockRuleEntity>()

        override fun getAllRules(): Flow<List<BlockRuleEntity>> = flowOf(rules.toList())

        override suspend fun getAllRulesOnce(): List<BlockRuleEntity> = rules.toList()

        override suspend fun getAppRule(packageName: String): BlockRuleEntity? =
            rules.firstOrNull { it.packageName == packageName && it.hostname == null }

        override suspend fun getHostnameRule(packageName: String, hostname: String): BlockRuleEntity? =
            rules.firstOrNull { it.packageName == packageName && it.hostname == hostname }

        override suspend fun insert(rule: BlockRuleEntity) {
            rules.add(rule)
        }

        override suspend fun deleteAppRule(packageName: String) {
            rules.removeAll { it.packageName == packageName && it.hostname == null }
        }

        override suspend fun deleteHostnameRule(packageName: String, hostname: String) {
            rules.removeAll { it.packageName == packageName && it.hostname == hostname }
        }
    }

    @Test
    fun `isBlocked returns false for null packageName`() {
        val engine = BlockingEngine(FakeBlockRuleDao(), CoroutineScope(Dispatchers.Unconfined))
        assertFalse(engine.isBlocked(null, null))
        assertFalse(engine.isBlocked(null, "example.com"))
    }

    @Test
    fun `isBlocked returns false for unknown package`() {
        val engine = BlockingEngine(FakeBlockRuleDao(), CoroutineScope(Dispatchers.Unconfined))
        assertFalse(engine.isBlocked("com.unknown.app", null))
    }

    @Test
    fun `app block overrides hostname allow`() = runTest {
        val dao = FakeBlockRuleDao()
        val engine = BlockingEngine(dao, this)

        engine.toggleAppBlock("com.example.app")
        advanceUntilIdle()

        assertTrue(engine.isBlocked("com.example.app", null))
        assertTrue(engine.isBlocked("com.example.app", "api.example.com"))
    }

    @Test
    fun `hostname block without app block`() = runTest {
        val dao = FakeBlockRuleDao()
        val engine = BlockingEngine(dao, this)

        engine.toggleHostnameBlock("com.example.app", "ads.example.com")
        advanceUntilIdle()

        assertFalse(engine.isBlocked("com.example.app", null))
        assertFalse(engine.isBlocked("com.example.app", "api.example.com"))
        assertTrue(engine.isBlocked("com.example.app", "ads.example.com"))
    }

    @Test
    fun `toggle app block on then off`() = runTest {
        val dao = FakeBlockRuleDao()
        val engine = BlockingEngine(dao, this)

        engine.toggleAppBlock("com.example.app")
        advanceUntilIdle()
        assertTrue(engine.isBlocked("com.example.app", null))

        engine.toggleAppBlock("com.example.app")
        advanceUntilIdle()
        assertFalse(engine.isBlocked("com.example.app", null))
    }

    @Test
    fun `toggle hostname block on then off`() = runTest {
        val dao = FakeBlockRuleDao()
        val engine = BlockingEngine(dao, this)

        engine.toggleHostnameBlock("com.example.app", "ads.example.com")
        advanceUntilIdle()
        assertTrue(engine.isBlocked("com.example.app", "ads.example.com"))

        engine.toggleHostnameBlock("com.example.app", "ads.example.com")
        advanceUntilIdle()
        assertFalse(engine.isBlocked("com.example.app", "ads.example.com"))
    }

    @Test
    fun `loadRules restores state from DAO`() = runTest {
        val dao = FakeBlockRuleDao()
        dao.rules.add(BlockRuleEntity(packageName = "com.blocked.app"))
        dao.rules.add(BlockRuleEntity(packageName = "com.example.app", hostname = "ads.example.com"))

        val engine = BlockingEngine(dao, this)
        engine.loadRules()

        assertTrue(engine.isBlocked("com.blocked.app", null))
        assertTrue(engine.isBlocked("com.blocked.app", "anything.com"))
        assertFalse(engine.isBlocked("com.example.app", null))
        assertTrue(engine.isBlocked("com.example.app", "ads.example.com"))
    }

    @Test
    fun `blocking one app does not affect another`() = runTest {
        val dao = FakeBlockRuleDao()
        val engine = BlockingEngine(dao, this)

        engine.toggleAppBlock("com.blocked.app")
        advanceUntilIdle()

        assertTrue(engine.isBlocked("com.blocked.app", null))
        assertFalse(engine.isBlocked("com.other.app", null))
    }

    @Test
    fun `blocked apps flow emits updates`() = runTest {
        val dao = FakeBlockRuleDao()
        val engine = BlockingEngine(dao, this)

        assertTrue(engine.blockedAppsFlow.value.isEmpty())

        engine.toggleAppBlock("com.example.app")
        advanceUntilIdle()

        assertTrue("com.example.app" in engine.blockedAppsFlow.value)
    }

    @Test
    fun `notifyBlocked increments app counter`() {
        val engine = BlockingEngine(FakeBlockRuleDao(), CoroutineScope(Dispatchers.Unconfined))
        assertEquals(0L, engine.getAppBlockedCount("com.example.app"))

        engine.notifyBlocked("com.example.app", null)
        assertEquals(1L, engine.getAppBlockedCount("com.example.app"))

        engine.notifyBlocked("com.example.app", null)
        assertEquals(2L, engine.getAppBlockedCount("com.example.app"))
    }

    @Test
    fun `notifyBlocked increments hostname counter`() {
        val engine = BlockingEngine(FakeBlockRuleDao(), CoroutineScope(Dispatchers.Unconfined))
        assertEquals(0L, engine.getHostnameBlockedCount("com.example.app", "ads.example.com"))

        engine.notifyBlocked("com.example.app", "ads.example.com")
        assertEquals(1L, engine.getHostnameBlockedCount("com.example.app", "ads.example.com"))
        assertEquals(1L, engine.getAppBlockedCount("com.example.app"))
    }
}

package com.bizconnect.v2.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bizconnect.v2.data.local.db.BizConnectDatabase
import com.bizconnect.v2.data.model.SpamFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SpamFilterDaoTest {

    private lateinit var database: BizConnectDatabase
    private lateinit var dao: SpamFilterDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BizConnectDatabase::class.java
        ).allowMainThreadQueries().build()

        dao = database.spamFilterDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetBlockedNumber() {
        val spamFilter = SpamFilter(
            id = UUID.randomUUID().toString(),
            blockedNumber = "01012345678",
            filterType = "BLOCK_NUMBER",
            reason = "Spam caller",
            createdAt = System.currentTimeMillis()
        )

        dao.insert(spamFilter)

        val retrieved = dao.getFilter(spamFilter.id)
        assertNotNull(retrieved)
        assertEquals("01012345678", retrieved?.blockedNumber)
    }

    @Test
    fun getBlockedNumbers() {
        dao.insert(
            SpamFilter(
                id = "filter-1",
                blockedNumber = "01011111111",
                filterType = "BLOCK_NUMBER",
                reason = "Spam caller",
                createdAt = System.currentTimeMillis()
            )
        )

        dao.insert(
            SpamFilter(
                id = "filter-2",
                blockedNumber = "01022222222",
                filterType = "BLOCK_NUMBER",
                reason = "Telemarketer",
                createdAt = System.currentTimeMillis()
            )
        )

        val blockedNumbers = dao.getBlockedNumbers()

        assertEquals(2, blockedNumbers.size)
        assertTrue(blockedNumbers.contains("01011111111"))
        assertTrue(blockedNumbers.contains("01022222222"))
    }

    @Test
    fun isNumberBlocked() {
        dao.insert(
            SpamFilter(
                id = "filter-blocked",
                blockedNumber = "01099999999",
                filterType = "BLOCK_NUMBER",
                reason = "Known spam",
                createdAt = System.currentTimeMillis()
            )
        )

        val blocked = dao.isNumberBlocked("01099999999")
        assertTrue(blocked)

        val notBlocked = dao.isNumberBlocked("01012345678")
        assertEquals(false, notBlocked)
    }

    @Test
    fun addSpamKeyword() {
        dao.insert(
            SpamFilter(
                id = "filter-keyword",
                blockedNumber = null,
                filterType = "KEYWORD",
                reason = "Spam keyword",
                keyword = "무료 상품",
                createdAt = System.currentTimeMillis()
            )
        )

        val keywords = dao.getSpamKeywords()

        assertEquals(1, keywords.size)
        assertTrue(keywords.contains("무료 상품"))
    }

    @Test
    fun getSpamKeywords() {
        dao.insert(
            SpamFilter(
                id = "filter-kw1",
                blockedNumber = null,
                filterType = "KEYWORD",
                reason = "Spam keyword",
                keyword = "피싱",
                createdAt = System.currentTimeMillis()
            )
        )

        dao.insert(
            SpamFilter(
                id = "filter-kw2",
                blockedNumber = null,
                filterType = "KEYWORD",
                reason = "Spam keyword",
                keyword = "클릭하세요",
                createdAt = System.currentTimeMillis()
            )
        )

        val keywords = dao.getSpamKeywords()

        assertEquals(2, keywords.size)
        assertTrue(keywords.contains("피싱"))
        assertTrue(keywords.contains("클릭하세요"))
    }

    @Test
    fun deleteFilter() {
        val filter = SpamFilter(
            id = "filter-delete",
            blockedNumber = "01012345678",
            filterType = "BLOCK_NUMBER",
            reason = "Remove this filter",
            createdAt = System.currentTimeMillis()
        )

        dao.insert(filter)
        assertTrue(dao.isNumberBlocked("01012345678"))

        dao.delete("filter-delete")

        assertEquals(false, dao.isNumberBlocked("01012345678"))
    }

    @Test
    fun getAllFilters() {
        dao.insert(
            SpamFilter(
                id = "filter-1",
                blockedNumber = "01011111111",
                filterType = "BLOCK_NUMBER",
                reason = "Spam",
                createdAt = System.currentTimeMillis()
            )
        )

        dao.insert(
            SpamFilter(
                id = "filter-2",
                blockedNumber = null,
                filterType = "KEYWORD",
                keyword = "피싱",
                reason = "Spam keyword",
                createdAt = System.currentTimeMillis()
            )
        )

        val allFilters = dao.getAllFilters()

        assertEquals(2, allFilters.size)
    }

    @Test
    fun getFiltersByType() {
        dao.insert(
            SpamFilter(
                id = "filter-block-1",
                blockedNumber = "01011111111",
                filterType = "BLOCK_NUMBER",
                reason = "Spam",
                createdAt = System.currentTimeMillis()
            )
        )

        dao.insert(
            SpamFilter(
                id = "filter-block-2",
                blockedNumber = "01022222222",
                filterType = "BLOCK_NUMBER",
                reason = "Spam",
                createdAt = System.currentTimeMillis()
            )
        )

        dao.insert(
            SpamFilter(
                id = "filter-keyword",
                blockedNumber = null,
                filterType = "KEYWORD",
                keyword = "피싱",
                reason = "Spam keyword",
                createdAt = System.currentTimeMillis()
            )
        )

        val blockFilters = dao.getFiltersByType("BLOCK_NUMBER")

        assertEquals(2, blockFilters.size)
    }

    @Test
    fun updateFilter() {
        val original = SpamFilter(
            id = "filter-update",
            blockedNumber = "01012345678",
            filterType = "BLOCK_NUMBER",
            reason = "Original reason",
            createdAt = System.currentTimeMillis()
        )

        dao.insert(original)

        val updated = original.copy(reason = "Updated reason")
        dao.update(updated)

        val retrieved = dao.getFilter("filter-update")
        assertEquals("Updated reason", retrieved?.reason)
    }

    @Test
    fun getRecentFilters() {
        val now = System.currentTimeMillis()

        dao.insert(
            SpamFilter(
                id = "filter-old",
                blockedNumber = "01011111111",
                filterType = "BLOCK_NUMBER",
                reason = "Old filter",
                createdAt = now - 7 * 86400000
            )
        )

        dao.insert(
            SpamFilter(
                id = "filter-recent",
                blockedNumber = "01022222222",
                filterType = "BLOCK_NUMBER",
                reason = "Recent filter",
                createdAt = now
            )
        )

        val recentFilters = dao.getRecentFilters(1)

        assertEquals(1, recentFilters.size)
        assertEquals("filter-recent", recentFilters[0].id)
    }
}

interface SpamFilterDao {
    fun insert(filter: SpamFilter)
    fun update(filter: SpamFilter)
    fun getFilter(id: String): SpamFilter?
    fun delete(id: String)
    fun getAllFilters(): List<SpamFilter>
    fun getBlockedNumbers(): List<String>
    fun isNumberBlocked(number: String): Boolean
    fun getSpamKeywords(): List<String>
    fun getFiltersByType(type: String): List<SpamFilter>
    fun getRecentFilters(limit: Int): List<SpamFilter>
}

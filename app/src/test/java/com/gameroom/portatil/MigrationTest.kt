package com.gameroom.portatil

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.gameroom.portatil.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Toda versão nova do banco precisa migrar os dados de quem já usa o app (schemas em app/schemas). */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val db = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsGamesAndEvents() {
        helper.createDatabase(db, 1).use {
            it.execSQL("INSERT INTO games (id, title, platform, catalogGameId, updatedAt) VALUES (1, 'Celeste', 'PC', NULL, 0)")
            it.execSQL("INSERT INTO events (id, uuid, gameId, type, date, createdAt, exportedAt) VALUES (1, 'u-1', 1, 'platinum', 20000, 0, 5)")
        }
        helper.runMigrationsAndValidate(db, 2, true).use {
            it.query("SELECT uuid, type, exportedAt, finishDate, finishUuid, pendingSince FROM events").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("u-1", c.getString(0))
                assertEquals("platinum", c.getString(1))
                assertEquals(5L, c.getLong(2))
                assertTrue(c.isNull(3))
                assertTrue(c.isNull(4))
                assertTrue(c.isNull(5))  // pendentes antigos contam a partir do createdAt
            }
            it.query("SELECT COUNT(*) FROM catalog_meta").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
        }
    }
}

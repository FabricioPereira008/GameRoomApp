package com.gameroom.portatil

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.gameroom.portatil.data.AppDatabase
import com.gameroom.portatil.data.Repository
import com.gameroom.portatil.domain.EventDraft
import com.gameroom.portatil.domain.EventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Repositório sobre um Room em memória: regras de exportação e de identidade dos registros. */
@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val day = LocalDate.of(2026, 10, 20)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = Repository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun events(gameId: Long) = repo.game(gameId).first()!!.events

    @Test
    fun exportMarksOnlyWhatWasDeliveredAndUndoRestores() = runTest {
        val id = repo.addGame("Hollow Knight", "Switch", null)
        repo.saveEvent(id, null, EventDraft(EventType.PLAYING, day.minusDays(10)))
        repo.saveEvent(id, null, EventDraft(EventType.FINISHED, day, playedHours = 38.0))
        assertEquals(2, repo.pendingCount.first())

        val (_, bundle) = repo.exportPackage()
        assertEquals(2, bundle.pkg.events.size)
        repo.markExported(bundle.markIds)
        assertEquals(0, repo.pendingCount.first())
        assertNull(repo.oldestPendingCreatedAt.first())
        assertEquals(0, repo.exportPackage().second.pkg.events.size)  // nada novo
        assertEquals(2, repo.exportPackage(includeExported = true).second.pkg.events.size)

        repo.unmarkExported(bundle.markIds)
        assertEquals(2, repo.pendingCount.first())
    }

    @Test
    fun editingExportedPlatinumRenewsBothIdentities() = runTest {
        val id = repo.addGame("Celeste", "PC", null)
        repo.saveEvent(id, null, EventDraft(EventType.PLATINUM, day, finishDate = day.minusDays(3)))
        val before = events(id).single()
        assertNotNull(before.finishUuid)
        repo.markExported(listOf(before.id))

        repo.saveEvent(id, before.id, EventDraft(EventType.PLATINUM, day, score = 9.0, finishDate = day.minusDays(2)))
        val after = events(id).single()
        assertNull(after.exportedAt)
        assertNotEquals(before.uuid, after.uuid)
        assertNotEquals(before.finishUuid, after.finishUuid)
        assertEquals(day.minusDays(2), after.finishDate)
    }

    @Test
    fun editingPendingPlatinumKeepsIdentitiesAndDropsSameDayFinish() = runTest {
        val id = repo.addGame("Celeste", "PC", null)
        repo.saveEvent(id, null, EventDraft(EventType.PLATINUM, day, finishDate = day.minusDays(3)))
        val before = events(id).single()
        repo.saveEvent(id, before.id, EventDraft(EventType.PLATINUM, day, finishDate = day.minusDays(1)))
        assertEquals(before.finishUuid, events(id).single().finishUuid)
        assertEquals(before.uuid, events(id).single().uuid)

        // zerado no mesmo dia da platina: um único evento basta
        repo.saveEvent(id, before.id, EventDraft(EventType.PLATINUM, day, finishDate = day))
        val same = events(id).single()
        assertNull(same.finishDate)
        assertNull(same.finishUuid)
        assertEquals(1, repo.exportPackage().second.pkg.events.size)
    }

    @Test
    fun lastFinishDateComesFromFinishedAndPlatinumRecords() = runTest {
        val id = repo.addGame("Celeste", "PC", null)
        repo.saveEvent(id, null, EventDraft(EventType.FINISHED, day.minusDays(30)))
        repo.saveEvent(id, null, EventDraft(EventType.PLATINUM, day, finishDate = day.minusDays(5)))
        val game = repo.game(id).first()!!
        assertEquals(day.minusDays(5), game.lastFinishDate())
        val platinum = game.events.first { it.type == EventType.PLATINUM }
        assertEquals(day.minusDays(30), game.lastFinishDate(exceptEventId = platinum.id))
    }

    @Test
    fun correctionOfOldExportedRecordIsPendingSinceTheEdit() = runTest {
        val id = repo.addGame("Hollow Knight", "Switch", null)
        val created = 1_000_000L
        repo.saveEvent(id, null, EventDraft(EventType.FINISHED, day), now = created)
        assertEquals(created, repo.oldestPendingCreatedAt.first())
        repo.markExported(events(id).map { it.id })

        val edited = created + 60L * 24 * 60 * 60 * 1000  // 60 dias depois
        repo.saveEvent(id, events(id).single().id, EventDraft(EventType.FINISHED, day, score = 8.0), now = edited)
        assertEquals(edited, repo.oldestPendingCreatedAt.first())
        assertEquals(created, events(id).single().createdAt)  // o created_at do contrato não muda
    }

    @Test
    fun deletedGameIsRestoredWithSameIdentity() = runTest {
        val id = repo.addGame("Celeste", "PC", 7)
        repo.saveEvent(id, null, EventDraft(EventType.PLATINUM, day, finishDate = day.minusDays(1)))
        repo.saveEvent(id, null, EventDraft(EventType.PLAYING, day.minusDays(9)))
        repo.markExported(listOf(events(id).first { it.type == EventType.PLAYING }.id))
        val before = repo.game(id).first()!!

        val snapshot = repo.deleteGame(id)!!
        assertNull(repo.game(id).first())
        assertEquals(0, repo.pendingCount.first())

        repo.restoreGame(snapshot)
        val after = repo.game(id).first()!!
        assertEquals(before.game, after.game)
        assertEquals(before.events.sortedBy { it.id }, after.events.sortedBy { it.id })
        assertEquals(1, repo.pendingCount.first())
    }

    @Test
    fun catalogImportKeepsSourceDateAndDropsLinksToVanishedGames() = runTest {
        val kept = repo.addGame("Celeste", "PC", 1)
        val gone = repo.addGame("Hades", "PC", 2)
        val result = repo.importCatalog(
            """{"format":"gameroom-catalog","version":1,"exported_at":"2026-10-12T15:30:00Z",
               "games":[{"id":1,"title":"Celeste","platform":"PC","status":"Zerado"}],"platforms":["PC"]}""",
        )
        assertEquals(1, result.games)
        assertEquals(1, result.unlinked)
        assertEquals(1, repo.game(kept).first()!!.game.catalogGameId)
        assertNull(repo.game(gone).first()!!.game.catalogGameId)
        assertEquals("2026-10-12T15:30:00Z", repo.catalogMeta.first()!!.exportedAt)
    }
}

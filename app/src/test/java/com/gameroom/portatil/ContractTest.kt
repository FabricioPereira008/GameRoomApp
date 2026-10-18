package com.gameroom.portatil

import com.gameroom.portatil.contract.CatalogFormatException
import com.gameroom.portatil.contract.MAX_NOTES
import com.gameroom.portatil.contract.MAX_PLATFORM
import com.gameroom.portatil.contract.MAX_TITLE
import com.gameroom.portatil.contract.decodeCatalog
import com.gameroom.portatil.contract.encodeInbox
import com.gameroom.portatil.data.EventEntity
import com.gameroom.portatil.data.GameEntity
import com.gameroom.portatil.data.GameWithEvents
import com.gameroom.portatil.data.buildExport
import com.gameroom.portatil.domain.EventType
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate

/** Mesmos schemas do GameRoom (docs/contracts), copiados para src/test/resources/contracts. */
class ContractTest {
    private val mapper = JsonMapper()
    private val registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)

    private fun resource(path: String): String =
        javaClass.classLoader!!.getResource("contracts/$path")!!.readText()

    private fun errors(schemaFile: String, json: String): Set<String> {
        val schema = registry.getSchema(resource(schemaFile))
        return schema.validate(mapper.readTree(json)).map { it.message }.toSet()
    }

    private val hk = GameEntity(id = 1, title = "Hollow Knight", platform = "Nintendo Switch", catalogGameId = 7)
    private val dc = GameEntity(id = 2, title = "Dead Cells", platform = null)

    private fun sample(): String = encodeInbox(buildExport(listOf(
        GameWithEvents(hk, listOf(
            EventEntity(id = 2, uuid = "u-2", gameId = 1, type = EventType.FINISHED, date = LocalDate.of(2026, 10, 12),
                createdAt = 1_760_000_000_000, playedHours = 38.0, score = 9.5, difficulty = 8.0, notes = "Final verdadeiro"),
            EventEntity(id = 1, uuid = "u-1", gameId = 1, type = EventType.PLAYING, date = LocalDate.of(2026, 10, 5), createdAt = 1_759_000_000_000),
        )),
        GameWithEvents(dc, listOf(
            EventEntity(id = 3, uuid = "u-3", gameId = 2, type = EventType.PLATINUM, date = LocalDate.of(2026, 10, 15)),
        )),
        GameWithEvents(GameEntity(id = 3, title = "Celeste", platform = "PC"), listOf(
            EventEntity(id = 4, uuid = "u-4", gameId = 3, type = EventType.PLATINUM, date = LocalDate.of(2026, 10, 20),
                finishDate = LocalDate.of(2026, 10, 16), finishUuid = "u-4z", playedHours = 20.0, score = 9.0),
        )),
    ), now = 1_760_500_000_000).pkg)

    @Test
    fun exportedPackageMatchesInboxSchema() {
        val json = sample()
        assertEquals(emptySet<String>(), errors("gameroom-inbox.v1.schema.json", json))
        // Amostra para testes manuais de ponta a ponta contra o GameRoom (POST /api/v1/inbox/import)
        java.io.File("build/contract").apply { mkdirs() }.resolve("inbox-sample.json").writeText(json)
    }

    @Test
    fun exportedPackageShape() {
        val root = mapper.readTree(sample())
        assertEquals("gameroom-inbox", root["format"].asString())
        assertEquals(1, root["version"].asInt())
        val events = root["events"]
        assertEquals(listOf("u-1", "u-2", "u-3", "u-4z", "u-4"), events.toList().map { it["id"].asString() })  // cronológico
        assertEquals("finished", events[1]["type"].asString())
        assertEquals("2026-10-12", events[1]["date"].asString())
        assertEquals(38.0, events[1]["played_hours"].asDouble(), 0.0)
        assertEquals(7, events[1]["game_id"].asInt())
        // opcionais ausentes não são enviados como null
        assertFalse(events[2].has("platform"))
        assertFalse(events[2].has("game_id"))
        assertFalse(events[0].has("score"))
    }

    @Test
    fun validatorRejectsWhatGameRoomRejects() {
        // Garante que a validação de fato roda (uma troca de biblioteca não pode deixar tudo "válido")
        val bad = """{"format":"gameroom-inbox","version":1,"events":[{"id":"x","type":"dropped","title":"","score":11}]}"""
        assertTrue(errors("gameroom-inbox.v1.schema.json", bad).size >= 3)
    }

    @Test
    fun gameRoomExamplesAreValid() {
        assertEquals(emptySet<String>(), errors("gameroom-inbox.v1.schema.json", resource("examples/inbox-v1.json")))
        assertEquals(emptySet<String>(), errors("gameroom-catalog.v1.schema.json", resource("examples/catalog-v1.json")))
    }

    @Test
    fun decodesCatalogExampleAndIgnoresUnknownFields() {
        val catalog = decodeCatalog(resource("examples/catalog-v1.json"))
        assertEquals(2, catalog.games.size)
        assertEquals("Celeste", catalog.games[0].title)
        assertNull(catalog.games[1].platform)
        assertTrue("PC" in catalog.platforms)

        val extra = """{"format":"gameroom-catalog","version":1,"games":[{"id":1,"title":"X","status":"Fila","cover":"a.png"}],"platforms":[],"novo":true}"""
        assertEquals("X", decodeCatalog(extra).games.single().title)
    }

    @Test(expected = CatalogFormatException::class)
    fun rejectsInboxFileAsCatalog() {
        decodeCatalog(resource("examples/inbox-v1.json"))
    }

    @Test(expected = CatalogFormatException::class)
    fun rejectsNewerCatalogVersion() {
        decodeCatalog("""{"format":"gameroom-catalog","version":2,"games":[],"platforms":[]}""")
    }

    @Test(expected = CatalogFormatException::class)
    fun rejectsGarbage() {
        decodeCatalog("não é json")
    }

    @Test
    fun platinumWithEarlierFinishGoesAsFinishedThenPlatinum() {
        val events = mapper.readTree(sample())["events"]
        val (finish, platinum) = events[3] to events[4]
        assertEquals("finished", finish["type"].asString())
        assertEquals("2026-10-16", finish["date"].asString())
        assertFalse(finish.has("played_hours"))  // estatísticas só na platina
        assertEquals("platinum", platinum["type"].asString())
        assertEquals("2026-10-20", platinum["date"].asString())
        assertEquals(20.0, platinum["played_hours"].asDouble(), 0.0)
    }

    @Test
    fun fieldsAreClippedToContractLimits() {
        val long = GameEntity(id = 9, title = "T".repeat(MAX_TITLE + 50), platform = "P".repeat(MAX_PLATFORM + 50))
        val pkg = buildExport(listOf(GameWithEvents(long, listOf(
            EventEntity(id = 1, uuid = "x", gameId = 9, type = EventType.FINISHED, date = LocalDate.of(2026, 1, 1),
                notes = "n".repeat(MAX_NOTES + 50)),
        ))), now = 0).pkg
        assertEquals(emptySet<String>(), errors("gameroom-inbox.v1.schema.json", encodeInbox(pkg)))
    }

    private fun mixed() = listOf(
        GameWithEvents(hk, listOf(
            EventEntity(id = 1, uuid = "a", gameId = 1, type = EventType.PLAYING, date = LocalDate.of(2026, 10, 1), exportedAt = 100L),
            EventEntity(id = 2, uuid = "b", gameId = 1, type = EventType.FINISHED, date = LocalDate.of(2026, 10, 12)),
        )),
        GameWithEvents(dc, listOf(
            EventEntity(id = 3, uuid = "c", gameId = 2, type = EventType.PLATINUM, date = LocalDate.of(2026, 10, 15),
                finishDate = LocalDate.of(2026, 10, 14), finishUuid = "cz"),
        )),
    )

    @Test
    fun exportsOnlyPendingByDefault() {
        val bundle = buildExport(mixed(), now = 0)
        assertEquals(listOf(2L, 3L), bundle.markIds)
        assertEquals(listOf("b", "cz", "c"), bundle.pkg.events.map { it.id })
        assertEquals(0, bundle.leftOut)
    }

    @Test
    fun includeExportedResendsEverythingButMarksOnlyPending() {
        val bundle = buildExport(mixed(), now = 0, includeExported = true)
        assertEquals(listOf(2L, 3L), bundle.markIds)
        assertEquals(listOf("a", "b", "cz", "c"), bundle.pkg.events.map { it.id })
    }

    @Test
    fun packageLimitKeepsOldestPendingAndNeverSplitsARecord() {
        // 3 eventos de espaço: "b" (1) cabe; o Platinado com zeramento (2) também; o exportado "a" fica de fora
        val full = buildExport(mixed(), now = 0, includeExported = true, maxEvents = 3)
        assertEquals(listOf("b", "cz", "c"), full.pkg.events.map { it.id })
        assertEquals(0, full.leftOut)
        // 2 eventos: "b" entra, o Platinado (2 eventos) fica para o próximo envio e não é marcado
        val tight = buildExport(mixed(), now = 0, maxEvents = 2)
        assertEquals(listOf("b"), tight.pkg.events.map { it.id })
        assertEquals(listOf(2L), tight.markIds)
        assertEquals(1, tight.leftOut)
    }
}

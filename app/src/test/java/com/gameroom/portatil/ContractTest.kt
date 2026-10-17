package com.gameroom.portatil

import com.fasterxml.jackson.databind.ObjectMapper
import com.gameroom.portatil.contract.CatalogFormatException
import com.gameroom.portatil.contract.decodeCatalog
import com.gameroom.portatil.contract.encodeInbox
import com.gameroom.portatil.data.EventEntity
import com.gameroom.portatil.data.GameEntity
import com.gameroom.portatil.data.GameWithEvents
import com.gameroom.portatil.data.buildInboxPackage
import com.gameroom.portatil.domain.EventType
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Mesmos schemas do GameRoom (docs/contracts), copiados para src/test/resources/contracts. */
class ContractTest {
    private val mapper = ObjectMapper()

    private fun resource(path: String): String =
        javaClass.classLoader!!.getResource("contracts/$path")!!.readText()

    private fun errors(schemaFile: String, json: String): Set<String> {
        val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(resource(schemaFile))
        return schema.validate(mapper.readTree(json)).map { it.message }.toSet()
    }

    private val hk = GameEntity(id = 1, title = "Hollow Knight", platform = "Nintendo Switch", catalogGameId = 7)
    private val dc = GameEntity(id = 2, title = "Dead Cells", platform = null)

    private fun sample(): String = encodeInbox(buildInboxPackage(listOf(
        GameWithEvents(hk, listOf(
            EventEntity(id = 2, uuid = "u-2", gameId = 1, type = EventType.FINISHED, date = LocalDate.of(2026, 10, 12),
                createdAt = 1_760_000_000_000, playedHours = 38.0, score = 9.5, difficulty = 8.0, notes = "Final verdadeiro"),
            EventEntity(id = 1, uuid = "u-1", gameId = 1, type = EventType.PLAYING, date = LocalDate.of(2026, 10, 5), createdAt = 1_759_000_000_000),
        )),
        GameWithEvents(dc, listOf(
            EventEntity(id = 3, uuid = "u-3", gameId = 2, type = EventType.PLATINUM, date = LocalDate.of(2026, 10, 15)),
        )),
    ), now = 1_760_500_000_000))

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
        assertEquals("gameroom-inbox", root["format"].asText())
        assertEquals(1, root["version"].asInt())
        val events = root["events"]
        assertEquals(listOf("u-1", "u-2", "u-3"), events.map { it["id"].asText() })  // cronológico por jogo
        assertEquals("finished", events[1]["type"].asText())
        assertEquals("2026-10-12", events[1]["date"].asText())
        assertEquals(38.0, events[1]["played_hours"].asDouble(), 0.0)
        assertEquals(7, events[1]["game_id"].asInt())
        // opcionais ausentes não são enviados como null
        assertFalse(events[2].has("platform"))
        assertFalse(events[2].has("game_id"))
        assertFalse(events[0].has("score"))
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
}

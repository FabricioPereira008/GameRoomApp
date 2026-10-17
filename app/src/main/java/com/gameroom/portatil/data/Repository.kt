package com.gameroom.portatil.data

import com.gameroom.portatil.contract.CATALOG_VERSION
import com.gameroom.portatil.contract.InboxEventDto
import com.gameroom.portatil.contract.InboxPackageDto
import com.gameroom.portatil.contract.decodeCatalog
import com.gameroom.portatil.contract.encodeInbox
import com.gameroom.portatil.domain.EventDraft
import com.gameroom.portatil.domain.EventIdentity
import com.gameroom.portatil.domain.identityAfterEdit
import com.gameroom.portatil.domain.isoInstant
import java.util.UUID

/** Monta o pacote gameroom-inbox com todos os eventos (o GameRoom descarta os já recebidos). */
fun buildInboxPackage(games: List<GameWithEvents>, now: Long): InboxPackageDto = InboxPackageDto(
    exported_at = isoInstant(now),
    events = games.flatMap { gw ->
        gw.sortedEvents.map { ev ->
            InboxEventDto(
                id = ev.uuid,
                type = ev.type.wire,
                title = gw.game.title,
                platform = gw.game.platform,
                game_id = gw.game.catalogGameId,
                date = ev.date.toString(),
                created_at = isoInstant(ev.createdAt),
                played_hours = ev.playedHours,
                score = ev.score,
                difficulty = ev.difficulty,
                notes = ev.notes,
            )
        }
    },
)

/** Registros que ainda não saíram do app (os que o próximo envio torna "exportados"). */
fun pendingEventIds(games: List<GameWithEvents>): List<Long> =
    games.flatMap { g -> g.events.filter { it.exportedAt == null }.map { it.id } }

class Repository(private val db: AppDatabase) {
    private val games = db.games()
    private val catalog = db.catalog()

    val gamesWithEvents = games.gamesWithEvents()
    val pendingCount = games.pendingCount()
    val localPlatforms = games.localPlatforms()
    val catalogGames = catalog.games()
    val catalogPlatforms = catalog.platforms()

    fun game(id: Long) = games.gameWithEvents(id)

    suspend fun addGame(title: String, platform: String?, catalogId: Int?): Long =
        games.insertGame(GameEntity(title = title.trim(), platform = platform?.trim()?.ifEmpty { null }, catalogGameId = catalogId))

    suspend fun updateGame(id: Long, title: String, platform: String?, catalogId: Int?) {
        val current = games.game(id) ?: return
        games.updateGame(current.copy(title = title.trim(), platform = platform?.trim()?.ifEmpty { null },
            catalogGameId = catalogId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteGame(id: Long) {
        games.game(id)?.let { games.deleteGame(it) }
    }

    suspend fun saveEvent(gameId: Long, eventId: Long?, draft: EventDraft) {
        val d = draft.normalized()
        if (eventId == null) {
            games.insertEvent(EventEntity(uuid = UUID.randomUUID().toString(), gameId = gameId, type = d.type, date = d.date,
                playedHours = d.playedHours, score = d.score, difficulty = d.difficulty, notes = d.notes))
        } else {
            val current = games.event(eventId) ?: return
            val identity = identityAfterEdit(EventIdentity(current.uuid, current.exportedAt))
            games.updateEvent(current.copy(uuid = identity.uuid, exportedAt = identity.exportedAt, type = d.type, date = d.date,
                playedHours = d.playedHours, score = d.score, difficulty = d.difficulty, notes = d.notes))
        }
        games.touch(gameId)
    }

    suspend fun deleteEvent(eventId: Long) {
        games.event(eventId)?.let { games.deleteEvent(it) }
    }

    /**
     * JSON pronto para salvar/compartilhar (sempre com todos os registros) e os ids dos ainda não
     * exportados, a marcar só depois que o arquivo for entregue.
     */
    suspend fun exportPackage(now: Long = System.currentTimeMillis()): Pair<String, List<Long>> {
        val all = games.allGamesWithEvents()
        return encodeInbox(buildInboxPackage(all, now)) to pendingEventIds(all)
    }

    suspend fun markExported(ids: List<Long>, now: Long = System.currentTimeMillis()) {
        if (ids.isNotEmpty()) games.markExported(ids, now)
    }

    /** Desfaz um markExported (ex.: o envio pelo app escolhido no compartilhar não foi concluído). */
    suspend fun unmarkExported(ids: List<Long>) {
        if (ids.isNotEmpty()) games.unmarkExported(ids)
    }

    /** Substitui o catálogo local; devolve quantos jogos vieram. */
    suspend fun importCatalog(text: String): Int {
        val dto = decodeCatalog(text)
        check(dto.version <= CATALOG_VERSION)
        catalog.replace(
            dto.games.map { CatalogGameEntity(it.id, it.title, it.platform, it.status) },
            dto.platforms.distinct().map { CatalogPlatformEntity(it) },
        )
        return dto.games.size
    }
}

package com.gameroom.portatil.data

import androidx.room.withTransaction
import com.gameroom.portatil.contract.CATALOG_VERSION
import com.gameroom.portatil.contract.InboxEventDto
import com.gameroom.portatil.contract.InboxPackageDto
import com.gameroom.portatil.contract.MAX_EVENTS
import com.gameroom.portatil.contract.MAX_NOTES
import com.gameroom.portatil.contract.MAX_PLATFORM
import com.gameroom.portatil.contract.MAX_TITLE
import com.gameroom.portatil.contract.decodeCatalog
import com.gameroom.portatil.contract.encodeInbox
import com.gameroom.portatil.domain.EventDraft
import com.gameroom.portatil.domain.EventIdentity
import com.gameroom.portatil.domain.EventType
import com.gameroom.portatil.domain.identityAfterEdit
import com.gameroom.portatil.domain.isoInstant
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Pacote a entregar e os registros que ele torna "exportados".
 * `leftOut` conta os pendentes que não couberam no limite do pacote e ficam para o próximo envio.
 */
data class ExportBundle(val pkg: InboxPackageDto, val markIds: List<Long>, val leftOut: Int = 0)

/** Resultado da importação do catálogo; `unlinked` são jogos do app cujo jogo sumiu do GameRoom. */
data class CatalogImport(val games: Int, val unlinked: Int)

/** Um registro vira um evento, ou dois no Platinado com zeramento em outro dia (Zerado antes da platina). */
fun toInboxEvents(game: GameEntity, ev: EventEntity): List<InboxEventDto> {
    val title = game.title.take(MAX_TITLE)
    val platform = game.platform?.take(MAX_PLATFORM)
    val createdAt = isoInstant(ev.createdAt)
    val finish = if (ev.type == EventType.PLATINUM && ev.finishDate != null && ev.finishUuid != null) {
        InboxEventDto(
            id = ev.finishUuid, type = EventType.FINISHED.wire, title = title, platform = platform,
            game_id = game.catalogGameId, date = ev.finishDate.toString(), created_at = createdAt,
        )
    } else null
    val main = InboxEventDto(
        id = ev.uuid,
        type = ev.type.wire,
        title = title,
        platform = platform,
        game_id = game.catalogGameId,
        date = ev.date.toString(),
        created_at = createdAt,
        played_hours = ev.playedHours,
        score = ev.score,
        difficulty = ev.difficulty,
        notes = ev.notes?.take(MAX_NOTES),
    )
    return listOfNotNull(finish, main)
}

/**
 * Monta o pacote gameroom-inbox. Por padrão leva só os pendentes; com `includeExported`, também os já
 * enviados (o GameRoom descarta os que já recebeu). O pacote respeita o limite de [MAX_EVENTS] do
 * contrato: os pendentes mais antigos entram primeiro e, sobrando espaço, os exportados mais recentes.
 */
fun buildExport(
    games: List<GameWithEvents>,
    now: Long,
    includeExported: Boolean = false,
    maxEvents: Int = MAX_EVENTS,
): ExportBundle {
    data class Row(val game: GameEntity, val ev: EventEntity, val events: List<InboxEventDto>)
    val rows = games.flatMap { gw -> gw.events.map { Row(gw.game, it, toInboxEvents(gw.game, it)) } }
    val chronological = compareBy<Row>({ it.ev.date }, { it.ev.createdAt })
    val pending = rows.filter { it.ev.exportedAt == null }.sortedWith(chronological)
    val exported = if (includeExported) rows.filter { it.ev.exportedAt != null }.sortedWith(chronological.reversed()) else emptyList()

    var room = maxEvents
    fun fits(r: Row) = (r.events.size <= room).also { if (it) room -= r.events.size }
    val pendingIn = pending.takeWhile(::fits)
    val exportedIn = exported.filter(::fits)

    val chosen = (pendingIn + exportedIn).sortedWith(chronological)
    return ExportBundle(
        pkg = InboxPackageDto(exported_at = isoInstant(now), events = chosen.flatMap { it.events }),
        markIds = pendingIn.map { it.ev.id },
        leftOut = pending.size - pendingIn.size,
    )
}

class Repository(private val db: AppDatabase) {
    private val games = db.games()
    private val catalog = db.catalog()

    val gamesWithEvents = games.gamesWithEvents()
    val pendingCount = games.pendingCount()
    val oldestPendingCreatedAt = games.oldestPendingCreatedAt()
    val localPlatforms = games.localPlatforms()
    val catalogGames = catalog.games()
    val catalogPlatforms = catalog.platforms()
    val catalogMeta = catalog.meta()

    fun game(id: Long) = games.gameWithEvents(id)

    suspend fun addGame(title: String, platform: String?, catalogId: Int?): Long =
        games.insertGame(GameEntity(title = cleanTitle(title), platform = cleanPlatform(platform), catalogGameId = catalogId))

    suspend fun updateGame(id: Long, title: String, platform: String?, catalogId: Int?) {
        val current = games.game(id) ?: return
        games.updateGame(current.copy(title = cleanTitle(title), platform = cleanPlatform(platform),
            catalogGameId = catalogId, updatedAt = System.currentTimeMillis()))
    }

    /** Apaga o jogo e devolve o que foi apagado, para [restoreGame] desfazer. */
    suspend fun deleteGame(id: Long): GameWithEvents? {
        val snapshot = games.gameWithEvents(id).first() ?: return null
        games.deleteGame(snapshot.game)
        return snapshot
    }

    /** Recoloca um jogo apagado com os mesmos ids, UUIDs e estado de exportação. */
    suspend fun restoreGame(snapshot: GameWithEvents) = games.restore(snapshot)

    suspend fun saveEvent(gameId: Long, eventId: Long?, draft: EventDraft, now: Long = System.currentTimeMillis()) {
        val d = draft.normalized()
        val notes = d.notes?.take(MAX_NOTES)
        if (eventId == null) {
            games.insertEvent(EventEntity(uuid = newUuid(), gameId = gameId, type = d.type, date = d.date, createdAt = now,
                playedHours = d.playedHours, score = d.score, difficulty = d.difficulty, notes = notes,
                finishDate = d.finishDate, finishUuid = d.finishDate?.let { newUuid() }, pendingSince = now))
        } else {
            val current = games.event(eventId) ?: return
            val identity = identityAfterEdit(EventIdentity(current.uuid, current.exportedAt))
            val renewed = identity.uuid != current.uuid
            // O zeramento acompanha a identidade do registro: um UUID novo para ele também na correção
            val finishUuid = when {
                d.finishDate == null -> null
                renewed || current.finishUuid == null -> newUuid()
                else -> current.finishUuid
            }
            games.updateEvent(current.copy(uuid = identity.uuid, exportedAt = identity.exportedAt, type = d.type, date = d.date,
                playedHours = d.playedHours, score = d.score, difficulty = d.difficulty, notes = notes,
                finishDate = d.finishDate, finishUuid = finishUuid,
                // Uma correção volta a aguardar exportação a partir de agora, não da criação do registro
                pendingSince = if (renewed) now else current.pendingSince))
        }
        games.touch(gameId)
    }

    suspend fun deleteEvent(eventId: Long) {
        games.event(eventId)?.let { games.deleteEvent(it) }
    }

    /** Pacote pronto para salvar/compartilhar; os registros só devem ser marcados depois da entrega. */
    suspend fun exportPackage(includeExported: Boolean = false, now: Long = System.currentTimeMillis()): Pair<String, ExportBundle> {
        val bundle = buildExport(games.allGamesWithEvents(), now, includeExported)
        return encodeInbox(bundle.pkg) to bundle
    }

    suspend fun markExported(ids: List<Long>, now: Long = System.currentTimeMillis()) {
        if (ids.isNotEmpty()) games.markExported(ids, now)
    }

    /** Desfaz um markExported (ex.: o envio pelo app escolhido no compartilhar não foi concluído). */
    suspend fun unmarkExported(ids: List<Long>) {
        if (ids.isNotEmpty()) games.unmarkExported(ids)
    }

    /** Substitui o catálogo local e desfaz os vínculos com jogos que não estão mais nele. */
    suspend fun importCatalog(text: String, now: Long = System.currentTimeMillis()): CatalogImport {
        val dto = decodeCatalog(text)
        check(dto.version <= CATALOG_VERSION)
        return db.withTransaction {
            catalog.replace(
                dto.games.distinctBy { it.id }.map { CatalogGameEntity(it.id, it.title, it.platform, it.status) },
                dto.platforms.distinct().map { CatalogPlatformEntity(it) },
                CatalogMetaEntity(exportedAt = dto.exported_at, importedAt = now),
            )
            CatalogImport(games = dto.games.size, unlinked = games.clearMissingCatalogLinks())
        }
    }

    private fun newUuid() = UUID.randomUUID().toString()
    private fun cleanTitle(title: String) = title.trim().take(MAX_TITLE)
    private fun cleanPlatform(platform: String?) = platform?.trim()?.take(MAX_PLATFORM)?.ifEmpty { null }
}

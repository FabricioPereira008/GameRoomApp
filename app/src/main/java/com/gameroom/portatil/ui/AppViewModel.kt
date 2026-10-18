package com.gameroom.portatil.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gameroom.portatil.data.AppDatabase
import com.gameroom.portatil.data.GameWithEvents
import com.gameroom.portatil.data.Repository
import com.gameroom.portatil.domain.EventDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Opção do campo de título: um jogo do catálogo do GameRoom ou um já cadastrado no app. */
data class TitleOption(val title: String, val platform: String?, val catalogId: Int?) {
    val label: String get() = if (platform.isNullOrBlank()) title else "$title · $platform"
}

/** Aviso para o snackbar; com `undo`, oferece "Desfazer". */
class UiMessage(val text: String, val undo: (() -> Unit)? = null)

/** Catálogo importado: tamanho e quando o GameRoom o gerou (null se o arquivo não dizia). */
data class CatalogInfo(val games: Int, val exportedAt: LocalDate?)

/** Arquivos de compartilhamento mais antigos que isso são apagados (o app de destino já os leu). */
private const val SHARE_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L

private const val IMPORT_HINT = "Importe no GameRoom em Importar → App do portátil."

fun exportFileName(): String =
    "gameroom-inbox-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.json"

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(AppDatabase.get(app))

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val games: StateFlow<List<GameWithEvents>> = repo.gamesWithEvents.state(emptyList())
    val pendingCount: StateFlow<Int> = repo.pendingCount.state(0)
    val oldestPendingCreatedAt: StateFlow<Long?> = repo.oldestPendingCreatedAt.state(null)

    val titleOptions: StateFlow<List<TitleOption>> = combine(repo.catalogGames, repo.gamesWithEvents) { catalog, local ->
        catalog.map { TitleOption(it.title, it.platform, it.id) } +
            local.map { TitleOption(it.game.title, it.game.platform, it.game.catalogGameId) }
    }.state(emptyList())

    val platformOptions: StateFlow<List<String>> = combine(repo.localPlatforms, repo.catalogPlatforms) { a, b ->
        (a + b).distinct()
    }.state(emptyList())

    val catalog: StateFlow<CatalogInfo> = combine(repo.catalogGames, repo.catalogMeta) { g, meta ->
        CatalogInfo(g.size, meta?.exportedAt?.let { parseExportedAt(it) })
    }.state(CatalogInfo(0, null))

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    /** Menus de compartilhar a abrir; a Activity é quem chama startActivity. */
    private val _shareIntents = Channel<Intent>(Channel.BUFFERED)
    val shareIntents: Flow<Intent> = _shareIntents.receiveAsFlow()

    init {
        // Os registros já foram marcados pelo ShareChosenReceiver; aqui só se avisa e oferece desfazer
        viewModelScope.launch {
            ShareEvents.chosen.collect { (appLabel, ids) ->
                _messages.send(
                    if (ids.isEmpty()) UiMessage("Enviado via $appLabel (nenhum registro novo).")
                    else UiMessage("Enviado via $appLabel. ${ids.size} registro(s) marcados como exportados.") { undoExport(ids) }
                )
            }
        }
    }

    fun game(id: Long) = repo.game(id)

    fun addGame(title: String, platform: String?, catalogId: Int?, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(repo.addGame(title, platform, catalogId))
    }

    fun updateGame(id: Long, title: String, platform: String?, catalogId: Int?) = viewModelScope.launch {
        repo.updateGame(id, title, platform, catalogId)
    }

    fun deleteGame(id: Long) = viewModelScope.launch {
        val snapshot = repo.deleteGame(id) ?: return@launch
        _messages.send(UiMessage("“${snapshot.game.title}” apagado.") { viewModelScope.launch { repo.restoreGame(snapshot) } })
    }

    fun saveEvent(gameId: Long, eventId: Long?, draft: EventDraft) = viewModelScope.launch {
        repo.saveEvent(gameId, eventId, draft)
    }

    fun deleteEvent(eventId: Long) = viewModelScope.launch { repo.deleteEvent(eventId) }

    private fun undoExport(ids: List<Long>) { viewModelScope.launch { repo.unmarkExported(ids) } }

    fun saveExport(uri: Uri, includeExported: Boolean) = viewModelScope.launch {
        val result = runCatching {
            val (json, bundle) = repo.exportPackage(includeExported)
            withContext(Dispatchers.IO) {
                val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: error("não foi possível abrir o arquivo")
                out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            repo.markExported(bundle.markIds)
            bundle
        }
        _messages.send(result.fold(
            { b ->
                val base = if (b.markIds.isNotEmpty()) "Arquivo salvo com ${b.markIds.size} registro(s) novo(s). $IMPORT_HINT"
                else "Arquivo salvo (nenhum registro novo desde a última exportação)."
                UiMessage(base + leftOutNote(b.leftOut))
            },
            { UiMessage("Falha ao exportar: ${it.message}") },
        ))
    }

    fun shareExport(includeExported: Boolean) = viewModelScope.launch {
        val context = getApplication<Application>()
        runCatching {
            val (json, bundle) = repo.exportPackage(includeExported)
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val cutoff = System.currentTimeMillis() - SHARE_CACHE_MAX_AGE_MS
                dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
                dir.resolve(exportFileName()).apply { writeText(json) }
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _shareIntents.send(Intent.createChooser(send, "Enviar para o GameRoom", ShareEvents.chooserCallback(context, bundle.markIds)))
            if (bundle.leftOut > 0) _messages.send(UiMessage(leftOutNote(bundle.leftOut).trim()))
        }.onFailure { _messages.send(UiMessage("Falha ao compartilhar: ${it.message}")) }
    }

    fun importCatalog(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val text = withContext(Dispatchers.IO) {
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: error("não foi possível abrir o arquivo")
                input.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            repo.importCatalog(text)
        }
        _messages.send(UiMessage(result.fold(
            { r ->
                "Catálogo importado: ${r.games} jogos." +
                    if (r.unlinked > 0) " ${r.unlinked} jogo(s) do app perderam o vínculo (não estão mais no GameRoom)." else ""
            },
            { it.message ?: "Falha ao importar o catálogo." },
        )))
    }

    private fun parseExportedAt(iso: String): LocalDate? =
        runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).toLocalDate() }.getOrNull()  // sem fuso

    private fun leftOutNote(leftOut: Int) =
        if (leftOut > 0) " $leftOut registro(s) não couberam no arquivo e vão na próxima exportação." else ""
}

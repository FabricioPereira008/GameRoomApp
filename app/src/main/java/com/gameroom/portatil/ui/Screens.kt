package com.gameroom.portatil.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.gameroom.portatil.data.EventEntity
import com.gameroom.portatil.data.GameWithEvents
import com.gameroom.portatil.domain.EventType
import com.gameroom.portatil.domain.formatNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun statusColor(type: EventType?): Color = when (type) {
    EventType.PLAYING -> Tokens.warning
    EventType.FINISHED -> Tokens.success
    EventType.PLATINUM -> Tokens.accent
    null -> Tokens.textMuted
}

private fun exportFileName(): String =
    "gameroom-inbox-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.json"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpenGame: (Long) -> Unit) {
    val games by vm.games.collectAsState()
    val pending by vm.pendingCount.collectAsState()
    val titleOptions by vm.titleOptions.collectAsState()
    val platformOptions by vm.platformOptions.collectAsState()
    val catalogSize by vm.catalogSize.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var exportChoice by remember { mutableStateOf(false) }
    // Registros enviados ao menu de compartilhar, aguardando a escolha de um app
    var awaitingShare by remember { mutableStateOf<List<Long>?>(null) }

    LaunchedEffect(Unit) {
        ShareEvents.chosen.collect { appLabel ->
            val ids = awaitingShare ?: return@collect
            awaitingShare = null
            vm.repo.markExported(ids)
            val res = snackbar.showSnackbar(
                if (ids.isEmpty()) "Enviado via $appLabel (nenhum registro novo)."
                else "Enviado via $appLabel. ${ids.size} registro(s) marcados como exportados.",
                actionLabel = if (ids.isEmpty()) null else "Desfazer",
                duration = SnackbarDuration.Long,
            )
            if (res == SnackbarResult.ActionPerformed) vm.repo.unmarkExported(ids)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { exportTo(context, vm, uri, snackbar) }
    }
    val catalogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { importCatalog(context, vm, uri, snackbar) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GameRoom Portátil")
                        Text(
                            if (pending > 0) "$pending registro(s) a exportar" else "Tudo exportado",
                            style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted,
                        )
                    }
                },
                actions = {
                    IconButton(enabled = games.isNotEmpty(), onClick = { exportChoice = true }) {
                        BadgedBox(badge = { if (pending > 0) Badge { Text("$pending") } }) {
                            Icon(Icons.Default.Upload, "Exportar para o GameRoom")
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Mais opções") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (catalogSize > 0) "Atualizar catálogo do GameRoom ($catalogSize)…" else "Importar catálogo do GameRoom…") },
                                onClick = { menuOpen = false; catalogLauncher.launch(arrayOf("application/json", "*/*")) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Novo jogo") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (games.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhum jogo ainda.\nToque em “Novo jogo” para registrar o que está jogando no portátil.",
                    color = Tokens.textMuted, style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(games, key = { it.game.id }) { GameCard(it) { onOpenGame(it.game.id) } }
            }
        }
    }

    if (exportChoice) {
        ExportDialog(
            pending = pending,
            onDismiss = { exportChoice = false },
            onSave = { exportChoice = false; saveLauncher.launch(exportFileName()) },
            onShare = {
                exportChoice = false
                scope.launch { awaitingShare = shareExport(context, vm, snackbar) }
            },
        )
    }

    if (creating) {
        GameEditorDialog(null, titleOptions, platformOptions, onDismiss = { creating = false }) { title, platform, catalogId ->
            creating = false
            vm.addGame(title, platform, catalogId, onOpenGame)
        }
    }
}

@Composable
private fun ExportDialog(pending: Int, onDismiss: () -> Unit, onSave: () -> Unit, onShare: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Upload, null) },
        title = { Text("Exportar para o GameRoom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    (if (pending > 0) "$pending registro(s) novo(s) desde a última exportação. " else "Nenhum registro novo. ") +
                        "O arquivo leva todos os registros; o GameRoom ignora os que já recebeu.",
                    style = MaterialTheme.typography.bodyMedium, color = Tokens.textMuted,
                )
                ExportOption(Icons.Default.SaveAlt, "Salvar arquivo",
                    "Grava em uma pasta à sua escolha: Downloads, Syncthing, Drive…", onSave)
                ExportOption(Icons.Default.Share, "Compartilhar",
                    "Envia por um app: Telegram, e-mail, Quick Share… Os registros só contam como exportados se você escolher um app.", onShare)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ExportOption(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Tokens.surface2),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Tokens.accent)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
            }
        }
    }
}

@Composable
private fun GameCard(item: GameWithEvents, onClick: () -> Unit) {
    val latest = item.latest
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Tokens.surface2),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.game.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.game.platform ?: "Sem plataforma", style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(latest?.type?.label ?: "Sem registros", color = statusColor(latest?.type), style = MaterialTheme.typography.labelLarge)
                latest?.let { Text(it.date.format(dateFormat), style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted) }
                if (item.pendingCount > 0) {
                    Text("${item.pendingCount} a exportar", style = MaterialTheme.typography.labelSmall, color = Tokens.warning)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: AppViewModel, gameId: Long, onBack: () -> Unit) {
    val item by vm.game(gameId).collectAsState(initial = null)
    val titleOptions by vm.titleOptions.collectAsState()
    val platformOptions by vm.platformOptions.collectAsState()
    var editingGame by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newEventType by remember { mutableStateOf<EventType?>(null) }
    var editingEvent by remember { mutableStateOf<EventEntity?>(null) }
    val game = item?.game

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(game?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    IconButton(onClick = { editingGame = true }) { Icon(Icons.Default.Edit, "Editar jogo") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Apagar jogo") }
                },
            )
        },
    ) { padding ->
        val events = item?.sortedEvents?.reversed().orEmpty()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(game?.platform ?: "Sem plataforma", color = Tokens.textMuted)
                Spacer(Modifier.padding(4.dp))
                Text("Registrar", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventType.entries.forEach { t ->
                        AssistChip(onClick = { newEventType = t }, label = { Text(t.label, color = statusColor(t)) })
                    }
                }
            }
            if (events.isEmpty()) {
                item { Text("Nenhum registro ainda.", color = Tokens.textMuted) }
            }
            items(events, key = { it.id }) { ev -> EventCard(ev) { editingEvent = ev } }
        }
    }

    if (editingGame && game != null) {
        GameEditorDialog(game, titleOptions, platformOptions, onDismiss = { editingGame = false }) { title, platform, catalogId ->
            editingGame = false
            vm.updateGame(game.id, title, platform, catalogId)
        }
    }
    if (confirmDelete && game != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar “${game.title}”?") },
            text = { Text("Todos os registros deste jogo serão apagados do app. O que já foi exportado continua no GameRoom.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteGame(game.id); onBack() }) { Text("Apagar", color = Tokens.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
    newEventType?.let { type ->
        EventEditorDialog(type, null, onDismiss = { newEventType = null }, onSave = {
            newEventType = null
            vm.saveEvent(gameId, null, it)
        }, onDelete = null)
    }
    editingEvent?.let { ev ->
        EventEditorDialog(ev.type, ev, onDismiss = { editingEvent = null }, onSave = {
            editingEvent = null
            vm.saveEvent(gameId, ev.id, it)
        }, onDelete = { editingEvent = null; vm.deleteEvent(ev.id) })
    }
}

@Composable
private fun EventCard(ev: EventEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Tokens.surface2),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ev.type.label, color = statusColor(ev.type), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(ev.date.format(dateFormat), color = Tokens.textMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(
                    if (ev.exportedAt == null) "A exportar" else "Exportado",
                    color = if (ev.exportedAt == null) Tokens.warning else Tokens.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            val stats = listOfNotNull(
                ev.playedHours?.let { "${formatNumber(it)}h" },
                ev.score?.let { "nota ${formatNumber(it)}" },
                ev.difficulty?.let { "dificuldade ${formatNumber(it)}" },
            )
            if (stats.isNotEmpty()) Text(stats.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            ev.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted) }
        }
    }
}

// --- exportação / catálogo (I/O fora da thread principal) ---

private suspend fun exportTo(context: Context, vm: AppViewModel, uri: Uri, snackbar: SnackbarHostState) {
    val result = runCatching {
        val (json, ids) = vm.repo.exportPackage()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        }
        vm.repo.markExported(ids)
        ids.size
    }
    snackbar.showSnackbar(result.fold(
        { if (it > 0) "Arquivo salvo com $it registro(s) novo(s). Importe em Configurações → App do Portátil."
          else "Arquivo salvo (nenhum registro novo desde a última exportação)." },
        { "Falha ao exportar: ${it.message}" },
    ))
}

/** Abre o menu de compartilhar e devolve os ids a marcar quando um app for escolhido (null em caso de erro). */
private suspend fun shareExport(context: Context, vm: AppViewModel, snackbar: SnackbarHostState): List<Long>? {
    val result = runCatching {
        val (json, ids) = vm.repo.exportPackage()
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "exports").apply { mkdirs() }.resolve(exportFileName()).apply { writeText(json) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Enviar para o GameRoom", ShareEvents.chooserCallback(context)))
        ids
    }
    result.exceptionOrNull()?.let { snackbar.showSnackbar("Falha ao compartilhar: ${it.message}") }
    return result.getOrNull()
}

private suspend fun importCatalog(context: Context, vm: AppViewModel, uri: Uri, snackbar: SnackbarHostState) {
    val result = runCatching {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        vm.repo.importCatalog(text)
    }
    snackbar.showSnackbar(result.fold({ "Catálogo importado: $it jogos." }, { it.message ?: "Falha ao importar o catálogo." }))
}


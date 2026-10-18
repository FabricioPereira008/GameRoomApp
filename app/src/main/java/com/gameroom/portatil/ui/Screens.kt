package com.gameroom.portatil.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gameroom.portatil.data.EventEntity
import com.gameroom.portatil.data.GameWithEvents
import com.gameroom.portatil.domain.EventType
import com.gameroom.portatil.domain.formatNumber
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration

fun statusColor(type: EventType?): Color = when (type) {
    EventType.PLAYING -> Tokens.warning
    EventType.FINISHED -> Tokens.success
    EventType.PLATINUM -> Tokens.accent
    null -> Tokens.textMuted
}

/** A partir de quantos dias o pendente mais antigo vira um alerta na tela inicial. */
private const val STALE_PENDING_DAYS = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpenGame: (Long) -> Unit) {
    val games by vm.games.collectAsStateWithLifecycle()
    val pending by vm.pendingCount.collectAsStateWithLifecycle()
    val titleOptions by vm.titleOptions.collectAsStateWithLifecycle()
    val platformOptions by vm.platformOptions.collectAsStateWithLifecycle()
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val oldestPending by vm.oldestPendingCreatedAt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var exportChoice by remember { mutableStateOf(false) }
    var includeExported by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { vm.shareIntents.collect { context.startActivity(it) } }
        vm.messages.collect { msg ->
            val res = snackbar.showSnackbar(
                msg.text,
                actionLabel = if (msg.undo != null) "Desfazer" else null,
                duration = SnackbarDuration.Long,
            )
            if (res == SnackbarResult.ActionPerformed) msg.undo?.invoke()
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.saveExport(uri, includeExported)
    }
    val catalogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCatalog(uri)
    }
    val pendingDays = oldestPending?.let { Duration.ofMillis(System.currentTimeMillis() - it).toDays() } ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GameRoom Portátil")
                        // Sem backup no PC, desinstalar o app perde os pendentes: avisa quando estão parados há dias
                        val stale = pending > 0 && pendingDays >= STALE_PENDING_DAYS
                        Text(
                            when {
                                pending == 0 -> "Tudo exportado"
                                stale -> "$pending registro(s) a exportar há $pendingDays dias"
                                else -> "$pending registro(s) a exportar"
                            },
                            style = MaterialTheme.typography.bodySmall, color = if (stale) Tokens.warning else Tokens.textMuted,
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
                                text = {
                                    Column {
                                        Text(if (catalog.games > 0) "Atualizar catálogo do GameRoom…" else "Importar catálogo do GameRoom…")
                                        if (catalog.games > 0) {
                                            Text(
                                                "${catalog.games} jogos" + (catalog.exportedAt?.let { " · de ${it.format(dateFormat)}" } ?: ""),
                                                style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted,
                                            )
                                        }
                                    }
                                },
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
            includeExported = includeExported,
            onIncludeExportedChange = { includeExported = it },
            onDismiss = { exportChoice = false },
            onSave = { exportChoice = false; saveLauncher.launch(exportFileName()) },
            onShare = { exportChoice = false; vm.shareExport(includeExported) },
        )
    }

    if (creating) {
        GameEditorDialog(null, titleOptions, platformOptions, hasExportedEvents = false, onDismiss = { creating = false }) { title, platform, catalogId ->
            creating = false
            vm.addGame(title, platform, catalogId, onOpenGame)
        }
    }
}

@Composable
internal fun ExportDialog(
    pending: Int,
    includeExported: Boolean,
    onIncludeExportedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Upload, null) },
        title = { Text("Exportar para o GameRoom") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (pending > 0) "$pending registro(s) novo(s) desde a última exportação."
                    else "Nenhum registro novo desde a última exportação.",
                    style = MaterialTheme.typography.bodyMedium, color = Tokens.textMuted,
                )
                Row(
                    Modifier.fillMaxWidth().clickable { onIncludeExportedChange(!includeExported) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeExported, onCheckedChange = onIncludeExportedChange)
                    Column {
                        Text("Incluir os já exportados", style = MaterialTheme.typography.bodyMedium)
                        Text("Para reenviar um arquivo perdido; o GameRoom ignora o que já recebeu.",
                            style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
                    }
                }
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

/** Resultado da busca do jogo; `item` null = o jogo não existe (apagado, ou id antigo no histórico). */
private class GameLoad(val item: GameWithEvents?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(vm: AppViewModel, gameId: Long, onBack: () -> Unit) {
    val load by remember(gameId) { vm.game(gameId).map(::GameLoad) }.collectAsStateWithLifecycle(initialValue = null)
    val item = load?.item
    val titleOptions by vm.titleOptions.collectAsStateWithLifecycle()
    val platformOptions by vm.platformOptions.collectAsStateWithLifecycle()
    LaunchedEffect(load) { if (load != null && load?.item == null) onBack() }
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        val hasExported = item.events.any { it.exportedAt != null }
        GameEditorDialog(game, titleOptions, platformOptions, hasExported, onDismiss = { editingGame = false }) { title, platform, catalogId ->
            editingGame = false
            vm.updateGame(game.id, title, platform, catalogId)
        }
    }
    if (confirmDelete && game != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar “${game.title}”?") },
            text = {
                val unsent = item.pendingCount
                Text(
                    if (unsent > 0) "$unsent registro(s) deste jogo ainda não foram exportados e serão perdidos. " +
                        "O que já foi exportado continua no GameRoom."
                    else "Todos os registros deste jogo serão apagados do app. O que já foi exportado continua no GameRoom.",
                    color = if (unsent > 0) Tokens.warning else Color.Unspecified,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteGame(game.id); onBack() }) { Text("Apagar", color = Tokens.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
    newEventType?.let { type ->
        EventEditorDialog(type, null, item?.lastFinishDate(), onDismiss = { newEventType = null }, onSave = {
            newEventType = null
            vm.saveEvent(gameId, null, it)
        }, onDelete = null)
    }
    editingEvent?.let { ev ->
        EventEditorDialog(ev.type, ev, item?.lastFinishDate(exceptEventId = ev.id), onDismiss = { editingEvent = null }, onSave = {
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
            ev.finishDate?.let {
                Text("Zerado em ${it.format(dateFormat)}", color = Tokens.textMuted, style = MaterialTheme.typography.bodySmall)
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

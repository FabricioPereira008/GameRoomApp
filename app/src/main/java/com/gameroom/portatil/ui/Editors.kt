package com.gameroom.portatil.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gameroom.portatil.contract.MAX_NOTES
import com.gameroom.portatil.contract.MAX_PLATFORM
import com.gameroom.portatil.contract.MAX_TITLE
import com.gameroom.portatil.data.EventEntity
import com.gameroom.portatil.data.GameEntity
import com.gameroom.portatil.domain.EventDraft
import com.gameroom.portatil.domain.SuggestIndex
import com.gameroom.portatil.domain.EventType
import com.gameroom.portatil.domain.finishDateValid
import com.gameroom.portatil.domain.foldText
import com.gameroom.portatil.domain.formatNumber
import com.gameroom.portatil.domain.parseDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Campo de texto livre com sugestões (catálogo do GameRoom + o que já foi digitado no app). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    maxLength: Int,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val byLabel = remember(options) { options.associateBy(optionLabel) }
    val index = remember(byLabel) { SuggestIndex(byLabel.keys) }
    val shown = remember(value, index) { index.query(value) }
    ExposedDropdownMenuBox(expanded = expanded && shown.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)); expanded = true },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && shown.isNotEmpty(), onDismissRequest = { expanded = false }) {
            shown.forEach { text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { byLabel[text]?.let(onPick); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
fun GameEditorDialog(
    initial: GameEntity?,
    titleOptions: List<TitleOption>,
    platformOptions: List<String>,
    hasExportedEvents: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, platform: String?, catalogId: Int?) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var platform by remember { mutableStateOf(initial?.platform ?: "") }
    var catalogId by remember { mutableStateOf(initial?.catalogGameId) }
    var pickedTitle by remember { mutableStateOf(initial?.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo jogo" else "Editar jogo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SuggestField(
                    value = title,
                    onValueChange = {
                        title = it
                        // Digitou outra coisa: deixa de apontar para o jogo do catálogo
                        if (pickedTitle == null || foldText(it) != foldText(pickedTitle!!)) catalogId = null
                    },
                    label = "Título",
                    options = titleOptions.filter { it.catalogId != null } + titleOptions.filter { it.catalogId == null },
                    optionLabel = { it.label },
                    maxLength = MAX_TITLE,
                    onPick = {
                        title = it.title.take(MAX_TITLE)
                        pickedTitle = it.title
                        catalogId = it.catalogId
                        if (!it.platform.isNullOrBlank()) platform = it.platform.take(MAX_PLATFORM)
                    },
                )
                SuggestField(
                    value = platform,
                    onValueChange = { platform = it },
                    label = "Plataforma",
                    options = platformOptions,
                    optionLabel = { it },
                    maxLength = MAX_PLATFORM,
                    onPick = { platform = it.take(MAX_PLATFORM) },
                )
                if (catalogId != null) {
                    Text("Vinculado ao jogo do GameRoom.", style = MaterialTheme.typography.bodySmall,
                        color = Tokens.success)
                }
                val renamed = initial != null && (title.trim() != initial.title || platform.trim() != (initial.platform ?: ""))
                if (renamed && hasExportedEvents) {
                    Text("Os registros já exportados continuam no GameRoom com o nome e a plataforma anteriores. " +
                        "Só os próximos vão com os novos.", style = MaterialTheme.typography.bodySmall, color = Tokens.warning)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onSave(title, platform, catalogId) }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorDialog(
    initialType: EventType,
    existing: EventEntity?,
    lastFinishDate: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var type by remember { mutableStateOf(existing?.type ?: initialType) }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var hours by remember { mutableStateOf(existing?.playedHours?.let(::formatNumber) ?: "") }
    var score by remember { mutableStateOf(existing?.score?.let(::formatNumber) ?: "") }
    var difficulty by remember { mutableStateOf(existing?.difficulty?.let(::formatNumber) ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    // Zeramento do Platinado; null = mesmo dia da platina (acompanha a data da platina)
    var finishDate by remember { mutableStateOf(existing?.finishDate ?: lastFinishDate?.takeIf { existing == null }) }
    var confirmDelete by remember { mutableStateOf(false) }
    val shownFinish = finishDate ?: date
    val finishOk = type != EventType.PLATINUM || finishDateValid(shownFinish, date)

    val hoursR = parseDecimal(hours)
    val scoreR = parseDecimal(score, 10.0)
    val diffR = parseDecimal(difficulty, 10.0)
    val valid = finishOk && (!type.hasStats || (hoursR.isSuccess && scoreR.isSuccess && diffR.isSuccess))

    // Tela cheia: com duas datas, três números e a observação, um diálogo fica apertado com o teclado aberto
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(if (existing == null) "Registrar" else "Editar registro") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cancelar") } },
                    actions = {
                        TextButton(enabled = valid, onClick = {
                            onSave(EventDraft(type, date, hoursR.getOrNull(), scoreR.getOrNull(), diffR.getOrNull(), notes, finishDate))
                        }) { Text("Salvar") }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
                if (type == EventType.PLATINUM) {
                    DateField("Data do zeramento", shownFinish, { finishDate = it },
                        error = if (finishOk) null else "O zeramento não pode ser depois da platina")
                }
                DateField(when (type) {
                    EventType.PLAYING -> "Data"
                    EventType.FINISHED -> "Data do zeramento"
                    EventType.PLATINUM -> "Data da platina"
                }, date, { date = it })
                if (type.hasStats) {
                    NumberField(hours, { hours = it }, "Horas jogadas (total)", hoursR, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(score, { score = it }, "Nota (0–10)", scoreR, Modifier.weight(1f))
                        NumberField(difficulty, { difficulty = it }, "Dificuldade (0–10)", diffR, Modifier.weight(1f))
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it.take(MAX_NOTES) }, label = { Text("Observação") },
                    supportingText = if (notes.length > MAX_NOTES * 9 / 10) {
                        { Text("${notes.length}/$MAX_NOTES") }
                    } else null,
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                if (existing?.exportedAt != null) {
                    Text("Este registro já foi exportado. Ao salvar, ele vai como correção na próxima exportação.",
                        style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmDelete = true }) { Text("Apagar registro", color = Tokens.danger) }
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar registro?") },
            text = {
                Text(if (existing?.exportedAt != null)
                    "Ele já foi enviado ao GameRoom: apagar aqui não desfaz nada lá."
                else "O registro ainda não foi exportado e será apagado.")
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Apagar", color = Tokens.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, error: String? = null) {
    var picking by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = date.format(dateFormat), onValueChange = {}, readOnly = true, label = { Text(label) },
        trailingIcon = { IconButton(onClick = { picking = true }) { Icon(Icons.Default.CalendarMonth, "Escolher data") } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, parsed: Result<Double?>, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        isError = parsed.isFailure,
        supportingText = parsed.exceptionOrNull()?.let { { Text(it.message ?: "") } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

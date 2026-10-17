package com.gameroom.portatil.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gameroom.portatil.data.EventEntity
import com.gameroom.portatil.data.GameEntity
import com.gameroom.portatil.domain.EventDraft
import com.gameroom.portatil.domain.EventType
import com.gameroom.portatil.domain.foldText
import com.gameroom.portatil.domain.formatNumber
import com.gameroom.portatil.domain.parseDecimal
import com.gameroom.portatil.domain.suggest
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val byLabel = remember(options) { options.associateBy(optionLabel) }
    val shown = remember(value, byLabel) { suggest(value, byLabel.keys) }
    ExposedDropdownMenuBox(expanded = expanded && shown.isNotEmpty(), onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it); expanded = true },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
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
                    onPick = {
                        title = it.title
                        pickedTitle = it.title
                        catalogId = it.catalogId
                        if (!it.platform.isNullOrBlank()) platform = it.platform
                    },
                )
                SuggestField(
                    value = platform,
                    onValueChange = { platform = it },
                    label = "Plataforma",
                    options = platformOptions,
                    optionLabel = { it },
                    onPick = { platform = it },
                )
                if (catalogId != null) {
                    Text("Vinculado ao jogo do GameRoom.", style = MaterialTheme.typography.bodySmall,
                        color = Tokens.success)
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
    var pickingDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val hoursR = parseDecimal(hours)
    val scoreR = parseDecimal(score, 10.0)
    val diffR = parseDecimal(difficulty, 10.0)
    val valid = !type.hasStats || (hoursR.isSuccess && scoreR.isSuccess && diffR.isSuccess)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Registrar" else "Editar registro") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
                OutlinedTextField(
                    value = date.format(dateFormat), onValueChange = {}, readOnly = true, label = { Text("Data") },
                    trailingIcon = { IconButton(onClick = { pickingDate = true }) { Icon(Icons.Default.CalendarMonth, "Escolher data") } },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type.hasStats) {
                    NumberField(hours, { hours = it }, "Horas jogadas (total)", hoursR)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(score, { score = it }, "Nota (0–10)", scoreR, Modifier.weight(1f))
                        NumberField(difficulty, { difficulty = it }, "Dificuldade (0–10)", diffR, Modifier.weight(1f))
                    }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Observação") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                if (existing?.exportedAt != null) {
                    Text("Este registro já foi exportado. Ao salvar, ele vai como correção na próxima exportação.",
                        style = MaterialTheme.typography.bodySmall, color = Tokens.textMuted)
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Apagar registro", color = Tokens.danger) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(EventDraft(type, date, hoursR.getOrNull(), scoreR.getOrNull(), diffR.getOrNull(), notes))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
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

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, parsed: Result<Double?>, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        isError = parsed.isFailure,
        supportingText = parsed.exceptionOrNull()?.let { { Text(it.message ?: "") } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

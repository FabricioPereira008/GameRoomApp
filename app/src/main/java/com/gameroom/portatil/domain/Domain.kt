package com.gameroom.portatil.domain

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Tipos de evento; `wire` é o valor do contrato gameroom-inbox. */
enum class EventType(val wire: String, val label: String, val hasStats: Boolean) {
    PLAYING("playing", "Jogando", false),
    FINISHED("finished", "Zerado", true),
    PLATINUM("platinum", "Platinado", true);

    companion object {
        fun fromWire(value: String): EventType = entries.first { it.wire == value }
    }
}

/** Dados editáveis de um evento (formulário). */
data class EventDraft(
    val type: EventType,
    val date: LocalDate,
    val playedHours: Double? = null,
    val score: Double? = null,
    val difficulty: Double? = null,
    val notes: String? = null,
) {
    /** Jogando não leva estatísticas (elas pertencem ao zeramento/platina). */
    fun normalized(): EventDraft = if (type.hasStats) copy(notes = notes?.trim()?.ifEmpty { null })
    else copy(playedHours = null, score = null, difficulty = null, notes = notes?.trim()?.ifEmpty { null })
}

data class EventIdentity(val uuid: String, val exportedAt: Long?)

/**
 * Um evento já exportado pode já estar no GameRoom, que ignora UUIDs repetidos.
 * Editá-lo gera um UUID novo e o marca como não exportado, para a correção chegar como evento novo.
 */
fun identityAfterEdit(current: EventIdentity, newUuid: () -> String = { UUID.randomUUID().toString() }): EventIdentity =
    if (current.exportedAt != null) EventIdentity(newUuid(), null) else current

fun foldText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

/** Sugestões que contêm o texto digitado (sem acento/caixa), começos primeiro, sem duplicatas. */
fun suggest(query: String, options: Collection<String>, limit: Int = 6): List<String> {
    val q = foldText(query)
    if (q.isEmpty()) return emptyList()
    return options.asSequence()
        .distinctBy { foldText(it) }
        .filter { val f = foldText(it); f.contains(q) && f != q }
        .sortedWith(compareBy({ !foldText(it).startsWith(q) }, { it.length }, { it }))
        .take(limit)
        .toList()
}

/** Aceita "12,5" ou "12.5"; vazio = null; fora da faixa = inválido. */
fun parseDecimal(text: String, max: Double? = null): Result<Double?> {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return Result.success(null)
    val v = t.toDoubleOrNull() ?: return Result.failure(IllegalArgumentException("Número inválido"))
    if (v < 0 || (max != null && v > max)) return Result.failure(IllegalArgumentException("Use de 0 a ${max?.let { formatNumber(it) } ?: "∞"}"))
    return Result.success(v)
}

fun formatNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')

fun isoInstant(millis: Long): String = Instant.ofEpochMilli(millis).toString()

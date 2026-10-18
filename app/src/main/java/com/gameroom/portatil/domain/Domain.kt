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

/**
 * Dados editáveis de um evento (formulário). `date` é o dia do evento (no Platinado, o da platina);
 * `finishDate` só existe no Platinado, quando o zeramento foi em outro dia.
 */
data class EventDraft(
    val type: EventType,
    val date: LocalDate,
    val playedHours: Double? = null,
    val score: Double? = null,
    val difficulty: Double? = null,
    val notes: String? = null,
    val finishDate: LocalDate? = null,
) {
    /** Jogando não leva estatísticas (elas pertencem ao zeramento/platina). */
    fun normalized(): EventDraft {
        val base = copy(
            notes = notes?.trim()?.ifEmpty { null },
            finishDate = finishDate?.takeIf { type == EventType.PLATINUM && it != date },
        )
        return if (type.hasStats) base else base.copy(playedHours = null, score = null, difficulty = null)
    }
}

/** O zeramento não pode ser depois da platina. */
fun finishDateValid(finishDate: LocalDate, platinumDate: LocalDate): Boolean = !finishDate.isAfter(platinumDate)

data class EventIdentity(val uuid: String, val exportedAt: Long?)

/**
 * Um evento já exportado pode já estar no GameRoom, que ignora UUIDs repetidos.
 * Editá-lo gera um UUID novo e o marca como não exportado, para a correção chegar como evento novo.
 */
fun identityAfterEdit(current: EventIdentity, newUuid: () -> String = { UUID.randomUUID().toString() }): EventIdentity =
    if (current.exportedAt != null) EventIdentity(newUuid(), null) else current

fun foldText(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

/**
 * Opções já normalizadas (sem acento/caixa) uma única vez: o catálogo pode ter milhares de jogos e
 * a busca roda a cada tecla.
 */
class SuggestIndex(options: Collection<String>) {
    private val entries: List<Pair<String, String>> = options.asSequence()
        .map { it to foldText(it) }
        .distinctBy { it.second }
        .toList()

    /** Opções que contêm o texto digitado, começos primeiro, sem a que já é igual ao texto. */
    fun query(query: String, limit: Int = 6): List<String> {
        val q = foldText(query)
        if (q.isEmpty()) return emptyList()
        return entries.asSequence()
            .filter { (_, f) -> f.contains(q) && f != q }
            .sortedWith(compareBy({ !it.second.startsWith(q) }, { it.first.length }, { it.first }))
            .take(limit)
            .map { it.first }
            .toList()
    }
}

fun suggest(query: String, options: Collection<String>, limit: Int = 6): List<String> =
    SuggestIndex(options).query(query, limit)

/** Aceita "12,5" ou "12.5"; vazio = null; fora da faixa = inválido. */
fun parseDecimal(text: String, max: Double? = null): Result<Double?> {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return Result.success(null)
    // "NaN" e "Infinity" passam no toDoubleOrNull, mas não cabem no JSON e travariam a exportação
    val v = t.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return Result.failure(IllegalArgumentException("Número inválido"))
    if (v < 0 || (max != null && v > max)) return Result.failure(IllegalArgumentException("Use de 0 a ${max?.let { formatNumber(it) } ?: "∞"}"))
    return Result.success(v)
}

fun formatNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')

fun isoInstant(millis: Long): String = Instant.ofEpochMilli(millis).toString()

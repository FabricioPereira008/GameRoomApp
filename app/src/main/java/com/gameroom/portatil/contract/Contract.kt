package com.gameroom.portatil.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Formatos trocados com o GameRoom. O dono dos schemas é o GameRoom (docs/contracts);
 * uma cópia fica em src/test/resources/contracts e os testes validam a saída contra ela.
 */
const val INBOX_FORMAT = "gameroom-inbox"
const val INBOX_VERSION = 1
const val CATALOG_FORMAT = "gameroom-catalog"
const val CATALOG_VERSION = 1

val contractJson = Json {
    ignoreUnknownKeys = true   // campos novos do GameRoom não quebram o app
    explicitNulls = false      // opcionais ausentes não vão para o arquivo
    encodeDefaults = true
    prettyPrint = true
}

@Serializable
data class InboxPackageDto(
    val format: String = INBOX_FORMAT,
    val version: Int = INBOX_VERSION,
    val exported_at: String,
    val events: List<InboxEventDto>,
)

@Serializable
data class InboxEventDto(
    val id: String,
    val type: String,
    val title: String,
    val platform: String? = null,
    val game_id: Int? = null,
    val date: String,
    val created_at: String,
    val played_hours: Double? = null,
    val score: Double? = null,
    val difficulty: Double? = null,
    val notes: String? = null,
)

@Serializable
data class CatalogDto(
    val format: String,
    val version: Int,
    val exported_at: String? = null,
    val games: List<CatalogGameDto> = emptyList(),
    val platforms: List<String> = emptyList(),
)

@Serializable
data class CatalogGameDto(
    val id: Int,
    val title: String,
    val platform: String? = null,
    val status: String = "",
)

class CatalogFormatException(message: String) : Exception(message)

fun encodeInbox(pkg: InboxPackageDto): String = contractJson.encodeToString(InboxPackageDto.serializer(), pkg)

fun decodeCatalog(text: String): CatalogDto {
    val catalog = try {
        contractJson.decodeFromString(CatalogDto.serializer(), text)
    } catch (e: Exception) {
        throw CatalogFormatException("Arquivo inválido: não é um catálogo do GameRoom.")
    }
    if (catalog.format != CATALOG_FORMAT) {
        throw CatalogFormatException("Arquivo inválido: não é um catálogo do GameRoom.")
    }
    if (catalog.version > CATALOG_VERSION) {
        throw CatalogFormatException("Catálogo da versão ${catalog.version}; atualize o app.")
    }
    return catalog
}

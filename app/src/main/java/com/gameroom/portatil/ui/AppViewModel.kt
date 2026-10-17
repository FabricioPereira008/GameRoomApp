package com.gameroom.portatil.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gameroom.portatil.data.AppDatabase
import com.gameroom.portatil.data.GameWithEvents
import com.gameroom.portatil.data.Repository
import com.gameroom.portatil.domain.EventDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Opção do campo de título: um jogo do catálogo do GameRoom ou um já cadastrado no app. */
data class TitleOption(val title: String, val platform: String?, val catalogId: Int?) {
    val label: String get() = if (platform.isNullOrBlank()) title else "$title · $platform"
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(AppDatabase.get(app))

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val games: StateFlow<List<GameWithEvents>> = repo.gamesWithEvents.state(emptyList())
    val pendingCount: StateFlow<Int> = repo.pendingCount.state(0)

    val titleOptions: StateFlow<List<TitleOption>> = combine(repo.catalogGames, repo.gamesWithEvents) { catalog, local ->
        catalog.map { TitleOption(it.title, it.platform, it.id) } +
            local.map { TitleOption(it.game.title, it.game.platform, it.game.catalogGameId) }
    }.state(emptyList())

    val platformOptions: StateFlow<List<String>> = combine(repo.localPlatforms, repo.catalogPlatforms) { a, b ->
        (a + b).distinct()
    }.state(emptyList())

    val catalogSize: StateFlow<Int> = combine(repo.catalogGames, repo.catalogPlatforms) { g, _ -> g.size }.state(0)

    fun game(id: Long) = repo.game(id)

    fun addGame(title: String, platform: String?, catalogId: Int?, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(repo.addGame(title, platform, catalogId))
    }

    fun updateGame(id: Long, title: String, platform: String?, catalogId: Int?) = viewModelScope.launch {
        repo.updateGame(id, title, platform, catalogId)
    }

    fun deleteGame(id: Long) = viewModelScope.launch { repo.deleteGame(id) }

    fun saveEvent(gameId: Long, eventId: Long?, draft: EventDraft) = viewModelScope.launch {
        repo.saveEvent(gameId, eventId, draft)
    }

    fun deleteEvent(eventId: Long) = viewModelScope.launch { repo.deleteEvent(eventId) }
}

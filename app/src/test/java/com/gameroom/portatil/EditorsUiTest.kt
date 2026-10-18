package com.gameroom.portatil

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.gameroom.portatil.contract.MAX_TITLE
import com.gameroom.portatil.data.EventEntity
import com.gameroom.portatil.data.GameEntity
import com.gameroom.portatil.domain.EventDraft
import com.gameroom.portatil.domain.EventType
import com.gameroom.portatil.ui.EventEditorDialog
import com.gameroom.portatil.ui.ExportDialog
import com.gameroom.portatil.ui.GameEditorDialog
import com.gameroom.portatil.ui.GameRoomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class EditorsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val day = LocalDate.of(2026, 10, 20)

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    @Test
    fun playingHidesStatsAndPlatinumShowsBothDates() {
        compose.setContent { GameRoomTheme { EventEditorDialog(EventType.PLAYING, null, null, {}, {}, null) } }
        compose.onNodeWithText("Horas jogadas (total)").assertDoesNotExist()
        compose.onNodeWithText("Data do zeramento").assertDoesNotExist()

        compose.onNodeWithText("Platinado").performClick()
        compose.onNodeWithText("Data do zeramento").assertIsDisplayed()
        compose.onNodeWithText("Data da platina").assertIsDisplayed()
        compose.onNodeWithText("Horas jogadas (total)").assertIsDisplayed()
    }

    @Test
    fun invalidNumberBlocksSaving() {
        compose.setContent { GameRoomTheme { EventEditorDialog(EventType.FINISHED, null, null, {}, {}, null) } }
        field("Horas jogadas (total)").performTextInput("NaN")
        compose.onNodeWithText("Salvar").assertIsNotEnabled()
        field("Horas jogadas (total)").performTextReplacement("38,5")
        compose.onNodeWithText("Salvar").assertIsEnabled()
    }

    @Test
    fun finishAfterPlatinumBlocksSaving() {
        val existing = EventEntity(id = 1, uuid = "u", gameId = 1, type = EventType.PLATINUM, date = day,
            finishDate = day.plusDays(2), finishUuid = "z")
        compose.setContent { GameRoomTheme { EventEditorDialog(EventType.PLATINUM, existing, null, {}, {}, null) } }
        compose.onNodeWithText("O zeramento não pode ser depois da platina").assertIsDisplayed()
        compose.onNodeWithText("Salvar").assertIsNotEnabled()
    }

    @Test
    fun newPlatinumSuggestsLastFinishAndSavesIt() {
        var saved: EventDraft? = null
        compose.setContent {
            GameRoomTheme { EventEditorDialog(EventType.PLATINUM, null, LocalDate.of(2026, 1, 5), {}, { saved = it }, null) }
        }
        compose.onNodeWithText("05/01/2026").assertIsDisplayed()
        compose.onNodeWithText("Salvar").performClick()
        assertEquals(LocalDate.of(2026, 1, 5), saved!!.finishDate)
    }

    @Test
    fun titleIsCappedAndRenameWarningShows() {
        var savedTitle = ""
        compose.setContent {
            GameRoomTheme {
                GameEditorDialog(GameEntity(id = 1, title = "Celeste", platform = "PC"), emptyList(), emptyList(),
                    hasExportedEvents = true, onDismiss = {}) { title, _, _ -> savedTitle = title }
            }
        }
        compose.onNodeWithText("continuam no GameRoom", substring = true).assertDoesNotExist()
        field("Título").performTextReplacement("X".repeat(MAX_TITLE + 30))
        compose.onNodeWithText("continuam no GameRoom", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Salvar").performClick()
        assertEquals(MAX_TITLE, savedTitle.length)
    }

    @Test
    fun exportDialogTogglesResendingEverything() {
        var include = false
        var shared = false
        compose.setContent {
            GameRoomTheme {
                ExportDialog(pending = 2, includeExported = include, onIncludeExportedChange = { include = it },
                    onDismiss = {}, onSave = {}, onShare = { shared = true })
            }
        }
        compose.onNodeWithText("2 registro(s) novo(s) desde a última exportação.").assertIsDisplayed()
        compose.onNodeWithText("Incluir os já exportados").performClick()
        assertTrue(include)
        compose.onNodeWithText("Compartilhar").performClick()
        assertTrue(shared)
    }
}

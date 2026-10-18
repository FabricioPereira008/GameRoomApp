package com.gameroom.portatil.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import com.gameroom.portatil.data.AppDatabase
import com.gameroom.portatil.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Um app foi escolhido no menu de compartilhar e `ids` já foram marcados como exportados. */
data class ShareChosen(val appLabel: String, val ids: List<Long>)

/**
 * O Android não informa se o arquivo foi de fato enviado, mas avisa quando um app é escolhido no
 * menu de compartilhar. É esse sinal que marca os registros como exportados; cancelar não marca nada.
 * Os ids viajam no próprio PendingIntent, então a marcação não depende da tela (rotação, processo
 * encerrado enquanto o menu estava aberto).
 */
object ShareEvents {
    private const val EXTRA_IDS = "com.gameroom.portatil.EXPORT_IDS"

    private val _chosen = MutableSharedFlow<ShareChosen>(extraBufferCapacity = 1)
    val chosen: SharedFlow<ShareChosen> = _chosen
    internal fun emit(event: ShareChosen) { _chosen.tryEmit(event) }

    fun chooserCallback(context: Context, ids: List<Long>): IntentSender {
        // Mutável: o menu de compartilhar acrescenta EXTRA_CHOSEN_COMPONENT ao intent
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val intent = Intent(context, ShareChosenReceiver::class.java).putExtra(EXTRA_IDS, ids.toLongArray())
        return PendingIntent.getBroadcast(context, 0, intent, flags).intentSender
    }

    internal fun ids(intent: Intent): List<Long> = intent.getLongArrayExtra(EXTRA_IDS)?.toList().orEmpty()
}

class ShareChosenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val component = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
        }
        val label = component?.let {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(it.packageName, 0)).toString()
            }.getOrNull()
        } ?: "o app escolhido"
        val ids = ShareEvents.ids(intent)
        val pending = goAsync()
        scope.launch {
            try {
                Repository(AppDatabase.get(context)).markExported(ids)
                ShareEvents.emit(ShareChosen(label, ids))
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

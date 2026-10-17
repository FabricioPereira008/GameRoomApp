package com.gameroom.portatil.ui

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * O Android não informa se o arquivo foi de fato enviado, mas avisa quando um app é escolhido no
 * menu de compartilhar. É esse sinal que marca os registros como exportados; cancelar não marca nada.
 */
object ShareEvents {
    private val _chosen = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val chosen: SharedFlow<String> = _chosen
    internal fun emit(appLabel: String) { _chosen.tryEmit(appLabel) }

    fun chooserCallback(context: Context): IntentSender {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 0, Intent(context, ShareChosenReceiver::class.java), flags).intentSender
    }
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
        ShareEvents.emit(label)
    }
}

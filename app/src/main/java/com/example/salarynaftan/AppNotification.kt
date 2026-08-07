package com.example.salarynaftan

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Сообщение внутри приложения (красивое in-app уведомление через Snackbar).
 * @param message текст (для ошибок автоматически добавляется отметка ⚠️)
 * @param isError если true — подсветка красным
 */
data class AppToast(
    val message: String,
    val isError: Boolean = false
)

/**
 * Единый центр in-app уведомлений для ВСЕХ вкладок.
 * Вместо разрозненных Toast/AlertDialog используем один фирменный Snackbar,
 * показываемый в корневом Scaffold приложения (поверх любой вкладки).
 */
object AppNotifier {
    private val _toasts = MutableSharedFlow<AppToast>(extraBufferCapacity = 16)
    val toasts = _toasts

    /** Показать обычное уведомление. */
    fun show(message: String) {
        _toasts.tryEmit(AppToast(message, isError = false))
    }

    /** Показать уведомление об ошибке (красная подсветка). */
    fun showError(message: String) {
        _toasts.tryEmit(AppToast(message, isError = true))
    }
}

/**
 * Хост уведомлений: встраивается в корневой Scaffold (snackbarHost)
 * и отображает единые красивые Snackbar с любой вкладки.
 */
@Composable
fun AppNotificationHost(modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        AppNotifier.toasts.collect { toast ->
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(
                message = if (toast.isError) "⚠️  ${toast.message}" else toast.message,
                actionLabel = null,
                duration = SnackbarDuration.Short
            )
        }
    }

    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { data ->
        val isError = data.visuals.message.startsWith("⚠️")
        Snackbar(
            snackbarData = data,
            containerColor = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.inverseSurface
            },
            contentColor = if (isError) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.inverseOnSurface
            }
        )
    }
}

package com.example.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

class SafeTextToolbar(
    private val delegate: TextToolbar
) : TextToolbar {
    override val status: TextToolbarStatus
        get() = delegate.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        runCatching {
            delegate.showMenu(
                rect = rect,
                onCopyRequested = onCopyRequested,
                onPasteRequested = onPasteRequested,
                onCutRequested = onCutRequested,
                onSelectAllRequested = onSelectAllRequested
            )
        }
    }

    override fun hide() {
        runCatching {
            if (delegate.status == TextToolbarStatus.Shown) {
                delegate.hide()
            }
        }
    }
}

fun clearActiveTextInput(
    focusManager: FocusManager,
    textToolbar: TextToolbar,
    keyboardController: SoftwareKeyboardController?
) {
    runCatching {
        if (textToolbar.status == TextToolbarStatus.Shown) {
            textToolbar.hide()
        }
    }
    runCatching { keyboardController?.hide() }
    runCatching { focusManager.clearFocus(force = true) }
}

/**
 * Wraps [AlertDialog] so that the dialog window's own [LocalTextToolbar] is guarded by
 * [SafeTextToolbar] and any active selection/IME inside the dialog is cleared before
 * [onDismissRequest], confirm, or dismiss actions tear down the dialog window.
 */
@Composable
fun SafeAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable (clearDialogInput: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable (clearDialogInput: () -> Unit) -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    val clearActionHolder = remember { mutableStateOf<() -> Unit>({}) }

    AlertDialog(
        onDismissRequest = {
            clearActionHolder.value()
            onDismissRequest()
        },
        modifier = modifier,
        title = title?.let { titleContent ->
            {
                ProvideSafeDialogLocals(onReadyClear = { clearActionHolder.value = it }) {
                    titleContent()
                }
            }
        },
        text = text?.let { textContent ->
            {
                ProvideSafeDialogLocals(onReadyClear = { clearActionHolder.value = it }) {
                    textContent()
                }
            }
        },
        confirmButton = {
            ProvideSafeDialogLocals(onReadyClear = { clearActionHolder.value = it }) {
                confirmButton(clearActionHolder.value)
            }
        },
        dismissButton = dismissButton?.let { dismissContent ->
            {
                ProvideSafeDialogLocals(onReadyClear = { clearActionHolder.value = it }) {
                    dismissContent(clearActionHolder.value)
                }
            }
        }
    )
}

@Composable
private fun ProvideSafeDialogLocals(
    onReadyClear: (() -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    val baseToolbar = LocalTextToolbar.current
    val safeToolbar = remember(baseToolbar) {
        if (baseToolbar is SafeTextToolbar) baseToolbar else SafeTextToolbar(baseToolbar)
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    SideEffect {
        onReadyClear {
            clearActiveTextInput(focusManager, safeToolbar, keyboardController)
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides safeToolbar) {
        content()
    }
}

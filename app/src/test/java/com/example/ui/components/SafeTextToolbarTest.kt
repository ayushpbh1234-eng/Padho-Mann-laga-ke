package com.example.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeTextToolbarTest {

    private class FakeTextToolbar(
        var failOnHide: Boolean = false
    ) : TextToolbar {
        var currentStatus: TextToolbarStatus = TextToolbarStatus.Hidden
        var hideCallCount: Int = 0
        var showCallCount: Int = 0

        override val status: TextToolbarStatus
            get() = currentStatus

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?
        ) {
            showCallCount++
            currentStatus = TextToolbarStatus.Shown
        }

        override fun hide() {
            hideCallCount++
            if (failOnHide) {
                throw IllegalStateException("ActionMode already destroyed")
            }
            currentStatus = TextToolbarStatus.Hidden
        }
    }

    @Test
    fun hide_doesNotDelegateWhenAlreadyHidden() {
        val delegate = FakeTextToolbar()
        val safeToolbar = SafeTextToolbar(delegate)

        safeToolbar.hide()

        assertEquals(0, delegate.hideCallCount)
    }

    @Test
    fun hide_delegatesOnceWhenShownAndSwallowsDelegateFailures() {
        val delegate = FakeTextToolbar(failOnHide = true)
        val safeToolbar = SafeTextToolbar(delegate)

        safeToolbar.showMenu(Rect(0f, 0f, 10f, 10f), null, null, null, null)
        safeToolbar.hide()

        assertEquals(1, delegate.showCallCount)
        assertEquals(1, delegate.hideCallCount)
    }
}

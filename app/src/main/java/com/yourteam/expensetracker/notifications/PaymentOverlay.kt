package com.yourteam.expensetracker.notifications

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.yourteam.expensetracker.ui.theme.AppCard
import com.yourteam.expensetracker.ui.theme.ExpenseTrackerTheme
import com.yourteam.expensetracker.ui.theme.Heading
import com.yourteam.expensetracker.ui.theme.PillShape
import kotlinx.coroutines.delay

/** What the pop-up shows. */
sealed interface OverlayContent {
    val amountText: String
    val payee: String

    /** A bank SMS or GPay chat line already recorded it. Closes by itself. */
    data class Recorded(override val amountText: String, override val payee: String, val source: String) : OverlayContent

    /** Nothing recorded it yet: ask. */
    data class Ask(override val amountText: String, override val payee: String) : OverlayContent
}

/**
 * A pop-up drawn on top of any app by the accessibility service (TYPE_ACCESSIBILITY_OVERLAY),
 * so no extra "display over other apps" permission is needed. One at a time.
 */
class PaymentOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var shownId: Long? = null
    private var shownContent: androidx.compose.runtime.MutableState<OverlayContent>? = null

    /**
     * Slides a bottom sheet up over whatever is on screen.
     * [onLater] runs when the user closes it with ✕ (Ask only). Tapping outside does nothing.
     */
    fun show(id: Long, content: OverlayContent, onYes: () -> Unit, onNo: () -> Unit, onLater: () -> Unit) {
        removeNow()
        val owner = OverlayOwner().also { it.start() }
        val state = mutableStateOf(content)
        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                ExpenseTrackerTheme {
                    OverlaySheet(
                        content = state.value,
                        onYes = onYes,
                        onNo = onNo,
                        onLater = onLater,
                        onGone = ::removeNow, // after the slide-down animation
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Not focusable: the keyboard and Back keep going to the app underneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }

        windowManager.addView(composeView, params)
        view = composeView
        this.owner = owner
        shownId = id
        shownContent = state
    }

    /** The record turned up while we were asking about payment [id]: show "Payment saved" instead. */
    fun showRecordedIfAsking(id: Long, recorded: OverlayContent.Recorded) {
        val state = shownContent ?: return
        if (view == null || shownId != id || state.value !is OverlayContent.Ask) return
        state.value = recorded
    }

    /** Removes the window immediately (no animation). */
    fun removeNow() {
        view?.let { runCatching { windowManager.removeView(it) } }
        owner?.stop()
        view = null
        owner = null
        shownId = null
        shownContent = null
    }
}

private enum class Answer { Yes, No, Later, AutoClose }

@Composable
private fun OverlaySheet(
    content: OverlayContent,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onLater: () -> Unit,
    onGone: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var answer by remember { mutableStateOf<Answer?>(null) }
    val close = { a: Answer -> if (answer == null) { answer = a; visible.targetState = false } }

    // "Saved" closes by itself.
    if (content is OverlayContent.Recorded) {
        LaunchedEffect(content) {
            delay(4_000)
            close(Answer.AutoClose)
        }
    }
    // Once the slide-down has finished, act on the answer and remove the window.
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState && answer != null) {
            when (answer) {
                Answer.Yes -> onYes()
                Answer.No -> onNo()
                Answer.Later -> if (content is OverlayContent.Ask) onLater()
                else -> Unit
            }
            onGone()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Dimmed background. Taps on it are swallowed: only ✕, Yes or No close the sheet.
        AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            )
        }
        AnimatedVisibility(
            visibleState = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(200)),
        ) {
            SheetCard(
                content = content,
                onYes = { close(Answer.Yes) },
                onNo = { close(Answer.No) },
                onClose = { close(if (content is OverlayContent.Ask) Answer.Later else Answer.AutoClose) },
            )
        }
    }
}

@Composable
private fun SheetCard(content: OverlayContent, onYes: () -> Unit, onNo: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null) {} // don't close on sheet taps
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Heading(
                text = if (content is OverlayContent.Recorded) "Payment saved" else "Did this payment go through?",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(top = 12.dp),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(end = 12.dp)) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("₹${content.amountText}", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "to ${content.payee}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            when (content) {
                is OverlayContent.Recorded -> Text(
                    "We found it in your ${content.source}. Nothing to do.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is OverlayContent.Ask -> Row {
                    OutlinedButton(onClick = onNo, shape = PillShape, modifier = Modifier.weight(1f)) {
                        Text("No")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onYes, shape = PillShape, modifier = Modifier.weight(1f)) {
                        Text("Yes, paid")
                    }
                }
            }
        }
    }
}
/** Compose needs a lifecycle and saved-state owner; a service window has neither, so we provide one. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() = registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
}

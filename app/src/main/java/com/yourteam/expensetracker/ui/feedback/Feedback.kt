package com.yourteam.expensetracker.ui.feedback

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yourteam.expensetracker.ui.theme.Accent
import com.yourteam.expensetracker.ui.theme.AppType
import com.yourteam.expensetracker.ui.theme.Negative
import com.yourteam.expensetracker.ui.theme.Positive
import com.yourteam.expensetracker.ui.theme.SurfaceRaised
import com.yourteam.expensetracker.ui.theme.TextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/*
 * FEEDBACK.md: one place to confirm user actions, so every screen behaves the same.
 * Call from screens or ViewModels; [FeedbackHost] in AppRoot shows the top banner and fires the haptic.
 *
 *   Feedback.success("Saved as Groceries", undo = { ... })   // Confirm haptic + top banner with Undo
 *   Feedback.failure("Install Google Pay to scan and pay")   // Reject haptic + top banner
 *   Feedback.tick()                                          // tab / period / chip change
 *   Feedback.toggle(on)                                      // a switch
 *
 * Never call these for background events (a payment captured, a refresh finishing).
 */

enum class Haptic { NONE, CONFIRM, REJECT, TICK, PULL, RELEASE, TOGGLE_ON, TOGGLE_OFF }

data class FeedbackEvent(val message: String?, val haptic: Haptic, val undo: (() -> Unit)? = null)

object Feedback {
    private val _events = MutableSharedFlow<FeedbackEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    internal val events = _events

    /** Confirm haptic, plus a top banner when [message] is given. [undo] adds an Undo button (4 s). */
    fun success(message: String? = null, undo: (() -> Unit)? = null) {
        _events.tryEmit(FeedbackEvent(message, Haptic.CONFIRM, undo))
    }

    /** Reject haptic. Pass [message] only when the screen has no inline error for it. */
    fun failure(message: String? = null) {
        _events.tryEmit(FeedbackEvent(message, Haptic.REJECT))
    }

    /** Selecting a chip, switching a tab or period. No message. */
    fun tick() {
        _events.tryEmit(FeedbackEvent(null, Haptic.TICK))
    }

    /** One light step while pulling to refresh (F12a). Lighter than [tick]. No message. */
    fun pull() {
        _events.tryEmit(FeedbackEvent(null, Haptic.PULL))
    }

    /** Letting go of a pull-to-refresh past the line (F12b). A firmer click than [tick]. No message. */
    fun release() {
        _events.tryEmit(FeedbackEvent(null, Haptic.RELEASE))
    }

    /** A switch turned on or off. No message. */
    fun toggle(on: Boolean) {
        _events.tryEmit(FeedbackEvent(null, if (on) Haptic.TOGGLE_ON else Haptic.TOGGLE_OFF))
    }
}

/** How long a message stays (FEEDBACK.md §2): 4 s with Undo, 3 s without. */
private const val WITH_UNDO_MS = 4_000L
private const val PLAIN_MS = 3_000L

/**
 * The short loading step every action button shows before it applies (FEEDBACK.md §2a).
 * Saves are instant on-device; this pause makes a change read as deliberate instead of a flicker.
 */
const val ACTION_DELAY_MS = 1_200L

/**
 * Shows [Feedback] events as a banner that slides down from the top. Put it once in AppRoot,
 * top-aligned under the status bar. One at a time; a new one replaces the old. Tap to dismiss.
 */
@Composable
fun FeedbackHost(modifier: Modifier = Modifier) {
    val view = LocalView.current
    var shown by remember { mutableStateOf<FeedbackEvent?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var showing: Job? = null
        Feedback.events.collect { event ->
            view.haptic(event.haptic)
            if (event.message == null) return@collect
            showing?.cancel() // replaces the old message
            showing = launch {
                shown = event
                visible = true
                delay(if (event.undo != null) WITH_UNDO_MS else PLAIN_MS)
                visible = false
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { -it } + fadeOut(tween(180)),
    ) {
        val event = shown ?: return@AnimatedVisibility
        val failed = event.haptic == Haptic.REJECT
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceRaised)
                .clickable { visible = false }
                .semantics { liveRegion = LiveRegionMode.Polite } // TalkBack reads it out
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = 44.dp),
        ) {
            Icon(
                if (failed) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null, // the words carry the meaning
                tint = if (failed) Negative else Positive,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(event.message.orEmpty(), style = AppType.body, color = TextPrimary, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
            event.undo?.let { undo ->
                TextButton(onClick = { visible = false; undo() }) {
                    Text("Undo", style = AppType.tab, color = Accent)
                }
            } ?: Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * FEEDBACK.md §3, View fallbacks. performHapticFeedback respects the system
 * "touch feedback" setting, so nothing vibrates when the user has it off.
 */
private fun View.haptic(type: Haptic) {
    val constant = when (type) {
        Haptic.NONE -> return
        Haptic.CONFIRM ->
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.REJECT ->
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
        Haptic.PULL -> when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK // made for rapid steps
            Build.VERSION.SDK_INT >= 27 -> HapticFeedbackConstants.TEXT_HANDLE_MOVE      // very light
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        Haptic.RELEASE -> HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.TOGGLE_ON ->
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
        Haptic.TOGGLE_OFF ->
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(constant)
}

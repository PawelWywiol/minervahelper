package com.pawelwywiol.minervahelper.ui

import android.view.KeyEvent

/**
 * The handheld's built-in controls report as an Xbox pad under Generic.kl, so confirm arrives as
 * BUTTON_A and cancel as BUTTON_B. Android only treats DPAD_CENTER and ENTER as confirm keys, so
 * a focused widget never activates. Rewriting the events before they reach the view tree keeps
 * every screen operable without per-widget key handling.
 */
private val REMAP = mapOf(
    KeyEvent.KEYCODE_BUTTON_A to KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_BUTTON_B to KeyEvent.KEYCODE_BACK,
)

fun KeyEvent.remapGamepad(): KeyEvent {
    val target = REMAP[keyCode] ?: return this
    return KeyEvent(
        downTime, eventTime, action, target, repeatCount, metaState,
        deviceId, scanCode, flags, source,
    )
}

package com.user.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent

class GestureActionDispatcher(private val context: Context) {
    fun dispatchClick() {
        AirTouchAccessibilityService.instance?.performClick()
    }

    fun dispatchBack() {
        AirTouchAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun dispatchHome() {
        AirTouchAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun dispatchRecents() {
         AirTouchAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    fun dispatchScrollDown() {
         AirTouchAccessibilityService.instance?.performScroll(1)
    }

    fun dispatchScrollUp() {
         AirTouchAccessibilityService.instance?.performScroll(-1)
    }

    fun dispatchAction(actionName: String) {
        when (actionName) {
            "HOME" -> dispatchHome()
            "BACK" -> dispatchBack()
            "RECENTS" -> dispatchRecents()
            // Expandable to other configurable actions later
        }
    }
}
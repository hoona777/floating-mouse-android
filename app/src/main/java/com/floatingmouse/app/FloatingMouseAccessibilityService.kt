package com.floatingmouse.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
import android.accessibilityservice.AccessibilityServiceInfo.FLAG_DEFAULT
import android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
import android.accessibilityservice.AccessibilityServiceInfo.TYPES_ALL_MASK
import android.accessibilityservice.GestureDescription
import android.graphics.Path

class FloatingMouseAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = TYPES_ALL_MASK
            feedbackType = FEEDBACK_GENERIC
            flags = FLAG_DEFAULT or FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        }
        serviceInfo = info
    }

    fun injectTouchClick(x: Float, y: Float) {
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

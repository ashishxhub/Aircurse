package com.ashishxhub.aircursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class AirCursorAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        activeService = this
        super.onServiceConnected()
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    private fun execute(command: JSONObject) {
        when (command.optString("action")) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "tap" -> {
                val metrics = resources.displayMetrics
                val x = (command.optDouble("x", 0.5) * metrics.widthPixels).toFloat()
                val y = (command.optDouble("y", 0.5) * metrics.heightPixels).toFloat()
                val path = Path().apply { moveTo(x, y) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 90))
                    .build()
                dispatchGesture(gesture, null, null)
            }
            "swipe" -> {
                val metrics = resources.displayMetrics
                val path = Path().apply {
                    moveTo((command.optDouble("x1", .5) * metrics.widthPixels).toFloat(),
                        (command.optDouble("y1", .7) * metrics.heightPixels).toFloat())
                    lineTo((command.optDouble("x2", .5) * metrics.widthPixels).toFloat(),
                        (command.optDouble("y2", .3) * metrics.heightPixels).toFloat())
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
                    .build()
                dispatchGesture(gesture, null, null)
            }
            "type" -> {
                val root = rootInActiveWindow ?: return
                val node = findFocusedEditable(root) ?: return
                val args = android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        command.optString("text"))
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                root.recycle()
            }
        }
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findFocusedEditable(child)
            if (found != null) return found
        }
        return null
    }

    companion object {
        @Volatile private var activeService: AirCursorAccessibilityService? = null
        fun runRemoteCommand(command: JSONObject): Boolean {
            val service = activeService ?: return false
            service.execute(command)
            return true
        }
    }
}

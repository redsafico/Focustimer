package com.example.focustimer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {

    private var lastRealApp: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return

        val pkg = event.packageName?.toString() ?: return

        // Our own overlay/activity and temporary/system windows must not
        // be interpreted as leaving the controlled app.
        if (!isRealLaunchableApp(pkg)) return

        // Ignore duplicate window events for the same real app.
        if (pkg == lastRealApp) return

        val previous = lastRealApp
        lastRealApp = pkg

        val selected = AppPrefs.getSelectedPackages(this)

        if (pkg in selected) {
            sendToMonitor(FocusMonitorService.ACTION_APP_ENTERED, pkg)
        } else if (previous != null) {
            // A real launchable app replaced the previous real launchable app.
            // This is a genuine app switch, so the active session can end.
            sendToMonitor(FocusMonitorService.ACTION_APP_LEFT, pkg)
        }
    }

    private fun isRealLaunchableApp(pkg: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
            }
            packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun sendToMonitor(action: String, pkg: String) {
        val intent = Intent(this, FocusMonitorService::class.java).apply {
            this.action = action
            putExtra(FocusMonitorService.EXTRA_PACKAGE, pkg)
        }

        try {
            startForegroundService(intent)
        } catch (_: Exception) {
            try {
                startService(intent)
            } catch (_: Exception) {
            }
        }
    }

    override fun onInterrupt() = Unit
}

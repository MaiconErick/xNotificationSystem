package com.maiconerick.xnotificationsystem

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Preferred host: accessibility overlays sit above the status bar, so the island can wrap the
 * front camera exactly like the original notch design. It never reads window content.
 */
class IslandAccessibilityService : AccessibilityService() {

    companion object {
        var instance: IslandAccessibilityService? = null
            private set
    }

    private var host: OverlayHost? = null
    var aod: AodOverlay? = null
        private set

    /** Re-adds the island window so it sits above windows added after it (e.g. the xAOD overlay). */
    fun bringIslandToFront() {
        host?.detach()
        host = OverlayHost(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY).also { it.attach() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // only one overlay at a time — the fallback service steps aside
        if (IslandService.running) IslandService.stop(this)
        IslandRuntime.install(this)
        ExtraDim.restore(this)
        IslandRuntime.lockScreenAction = { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }
        host = OverlayHost(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY).also { it.attach() }
        aod = AodOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        IslandRuntime.lockScreenAction = null
        aod?.hide()
        aod = null
        host?.detach()
        host = null
        IslandRuntime.uninstall()
        super.onDestroy()
    }
}

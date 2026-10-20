package com.maiconerick.xnotificationsystem

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.maiconerick.xnotificationsystem.island.Island
import com.maiconerick.xnotificationsystem.island.IslandSettings
import com.maiconerick.xnotificationsystem.island.LockMode

/**
 * xAOD drawn as a see-through accessibility overlay instead of an activity.
 *
 * Why: an activity over the lock screen "occludes" the keyguard, and the keyguard stops listening
 * for the fingerprint while occluded and interactive. As an overlay the real lock screen stays
 * visible underneath (just hidden by our black pixels), so a finger on the sensor unlocks straight
 * from the dim mascot screen, and a swipe up opens the PIN prompt as usual.
 *
 * Two windows: the visual one is not touchable (every touch falls through to the keyguard); a 1 px
 * sentinel receives ACTION_OUTSIDE for any touch and fades the visual away.
 */
class AodOverlay(private val svc: IslandAccessibilityService) : SensorEventListener {
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var owner: OverlayOwner? = null
    private var visual: FrameLayout? = null
    private var sentinel: View? = null
    private var visualParams: WindowManager.LayoutParams? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensors: SensorManager? = null

    private var alwaysOn = false
    private var covered by mutableStateOf(false)
    private var revealed by mutableStateOf(false)
    private var sessionStart = 0L
    private var sessionLevel = -1
    private var sessionCharging = false

    val showing get() = visual != null

    @Suppress("DEPRECATION")
    fun show(alwaysOn: Boolean) {
        if (showing) hide()
        this.alwaysOn = alwaysOn
        revealed = false
        covered = false
        val o = OverlayOwner().also { it.start() }
        owner = o

        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                (if (alwaysOn) WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON else 0)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            screenBrightness = if (alwaysOn) IslandSettings.aodBacklight else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            title = "xAOD"
        }
        visualParams = lp

        val compose = ComposeView(svc).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LockScreen(
                    alwaysOn = alwaysOn, preview = false, covered = covered,
                    onUnlock = {}, onAwake = {}, revealed = revealed, hintText = "Touch the fingerprint sensor",
                )
            }
        }
        val frame = FrameLayout(svc).apply {
            addView(compose, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewTreeViewModelStoreOwner(o)
        }
        runCatching { wm.addView(frame, lp) }.onFailure { cleanup(); return }
        visual = frame

        val spy = object : View(svc) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) onTouched()
                return false
            }
        }
        val spyLp = WindowManager.LayoutParams(
            1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; title = "xAOD touch" }
        runCatching { wm.addView(spy, spyLp) }
        sentinel = spy

        // the pill (bolt + percentage while charging) has to sit above the black overlay
        svc.bringIslandToFront()
        Island.hideCompletely()

        if (alwaysOn) {
            val pm = svc.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "xnotificationsystem:xaod",
            ).apply { acquire() }
            val charging = IslandRuntime.isCharging(svc)
            // on the charger in "while charging" mode it is a bedside clock: no timeout
            val stay = IslandSettings.lockMode == LockMode.WhileCharging && charging
            if (!stay) handler.postDelayed(::sleepNow, IslandSettings.lockMinutes * 60_000L)
            if (IslandSettings.aodPocket) {
                val sm = svc.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                sensors = sm
                sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            }
            ExtraDim.apply(svc)
            sessionStart = System.currentTimeMillis()
            sessionLevel = IslandRuntime.batteryLevel(svc)
            sessionCharging = charging
        }
    }

    /** Any touch: show the real lock screen (bright) and drop out of the way a few seconds later. */
    private fun onTouched() {
        if (revealed) return
        revealed = true
        visualParams?.let { lp ->
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            runCatching { visual?.let { wm.updateViewLayout(it, lp) } }
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ExtraDim.restore(svc)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ hide() }, 8000)
    }

    override fun onSensorChanged(e: SensorEvent) {
        covered = e.values[0] < e.sensor.maximumRange.coerceAtMost(5f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Always-on timeout / charger removed: really turn the display off (and don't bounce back on). */
    fun sleepNow() {
        IslandRuntime.ignoreNextScreenOff = true
        hide()
        if (IslandRuntime.lockScreenAction?.invoke() != true) IslandRuntime.ignoreNextScreenOff = false
    }

    fun chargerRemoved() {
        if (showing && alwaysOn && !revealed && IslandSettings.lockMode == LockMode.WhileCharging) sleepNow()
    }

    private fun recordUsage() {
        if (sessionStart == 0L) return
        val minutes = (System.currentTimeMillis() - sessionStart) / 60_000f
        val drop = sessionLevel - IslandRuntime.batteryLevel(svc)
        val onBattery = !sessionCharging && !IslandRuntime.isCharging(svc)
        if (onBattery && minutes >= 5f && sessionLevel >= 0 && drop >= 0) {
            IslandSettings.aodMinutes += minutes
            IslandSettings.aodDrop += drop
        }
        sessionStart = 0L
    }

    fun hide() {
        recordUsage()
        cleanup()
        Island.setLockCharge(null)
        if (Island.state.value.kind == com.maiconerick.xnotificationsystem.island.Kind.Hidden) Island.collapse()
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        ExtraDim.restore(svc)
        sensors?.unregisterListener(this)
        sensors = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        visual?.let { runCatching { wm.removeViewImmediate(it) } }
        sentinel?.let { runCatching { wm.removeViewImmediate(it) } }
        visual = null
        sentinel = null
        visualParams = null
        owner?.stop()
        owner = null
    }
}

package com.maiconerick.xnotificationsystem

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.maiconerick.xnotificationsystem.island.Island
import com.maiconerick.xnotificationsystem.island.IslandSettings
import com.maiconerick.xnotificationsystem.island.IslandGeometry
import com.maiconerick.xnotificationsystem.island.IslandHost
import com.maiconerick.xnotificationsystem.island.IslandRoot
import com.maiconerick.xnotificationsystem.island.Kind
import com.maiconerick.xnotificationsystem.island.LocalIslandHost
import com.maiconerick.xnotificationsystem.island.isExpanded
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/** Lifecycle plumbing so a ComposeView can live in a WindowManager overlay owned by a Service. */
internal class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}

/**
 * Owns the overlay window. The window is kept as small as the panel once an animation settles,
 * so only the island itself ever swallows touches (the status bar pull-down keeps working).
 */
class OverlayHost(
    private val ctx: Context,
    windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
) : IslandHost {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val owner = OverlayOwner()
    private val scope = MainScope()
    private var root: FrameLayout? = null
    private var settleJob: Job? = null
    private var lastPanel = IntSize.Zero
    private val density = ctx.resources.displayMetrics.density
    private val shadowPad = (28 * density).roundToInt()
    private val maxHeight = (520 * density).roundToInt()

    private var landscape by mutableStateOf(IslandRuntime.isLandscape(ctx))
    /** True while hidden because of landscape (setting "Hide in landscape"). */
    private var suppressed = false
    private var wantTouch = false
    private val rotation = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = Unit
    }

    private var focusableState by mutableStateOf(false)
    override val focusable: Boolean get() = focusableState

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        maxHeight,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        x = 0
        y = 0
        layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= 30)
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        title = "xNotificationSystem island"
    }

    fun attach() {
        if (root != null) return
        owner.start()
        val geo = computeGeometry()
        val compose = ComposeView(ctx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                CompositionLocalProvider(LocalIslandHost provides this@OverlayHost) {
                    IslandRoot(geo, ::onPanelSize)
                }
            }
        }
        val frame = object : FrameLayout(ctx) {
            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    Island.outsideTap()
                    return false
                }
                return super.dispatchTouchEvent(ev)
            }
        }
        frame.addView(compose, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        frame.setViewTreeViewModelStoreOwner(owner)
        frame.clipChildren = false
        wm.addView(frame, params)
        root = frame

        scope.launch {
            var lastKind: Kind? = null
            Island.state.collect { s ->
                if (s.kind != lastKind) {
                    lastKind = s.kind
                    if (s.kind != Kind.Chat && focusableState) setFocusable(false)
                    if (s.kind.isExpanded) grow()
                    wantTouch = s.kind != Kind.Hidden
                    applyTouchable()
                }
                // grow() enlarges the window, but the panel may end up exactly as big as before (no
                // size callback then), which used to leave a tall invisible window eating touches.
                scheduleSettle(900)
            }
        }

        ctx.registerComponentCallbacks(rotation)
        scope.launch {
            snapshotFlow { IslandSettings.hideInLandscape && landscape }.collect { hide ->
                suppressed = hide
                if (hide && focusableState) setFocusable(false)
                root?.visibility = if (hide) View.GONE else View.VISIBLE
                applyTouchable()
            }
        }
    }

    fun detach() {
        scope.cancel()
        runCatching { ctx.unregisterComponentCallbacks(rotation) }
        root?.let { runCatching { wm.removeViewImmediate(it) } }
        root = null
        owner.stop()
    }

    /** Before an expansion starts the window must already be big enough for the animation. */
    private fun grow() {
        settleJob?.cancel()
        if (params.width != WindowManager.LayoutParams.MATCH_PARENT || params.height != maxHeight) {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = maxHeight
            update()
        }
    }

    /** Called every frame while the panel animates; shrink the window once it stops changing. */
    private fun onPanelSize(size: IntSize) {
        lastPanel = size
        scheduleSettle(420)
    }

    private fun scheduleSettle(afterMs: Long) {
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(afterMs)
            val size = lastPanel
            if (size.height <= 0) return@launch
            val expanded = Island.state.value.kind.isExpanded
            val h = (size.height + shadowPad).coerceAtMost(maxHeight)
            val w = if (expanded) WindowManager.LayoutParams.MATCH_PARENT
            else size.width + (8 * density).roundToInt()
            if (params.height != h || params.width != w) {
                params.height = max(h, 1)
                params.width = w
                update()
            }
        }
    }

    private fun applyTouchable() = setTouchable(wantTouch && !suppressed)

    private fun setTouchable(touchable: Boolean) {
        val f = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (touchable) params.flags and f.inv() else params.flags or f
        if (newFlags != params.flags) {
            params.flags = newFlags
            update()
        }
    }

    override fun setFocusable(focusable: Boolean) {
        focusableState = focusable
        val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.flags = if (focusable) params.flags and f.inv() else params.flags or f
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                if (focusable) WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                else WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        update()
    }

    private fun update() {
        root?.let { runCatching { wm.updateViewLayout(it, params) } }
    }

    /** Pill/toolbar heights from the status bar + camera cutout, so the camera sits inside the bar. */
    private fun computeGeometry(): IslandGeometry {
        var statusPx = 0
        var cutBottom = 0
        var cutWidth = 0
        var holeTop = 0
        if (Build.VERSION.SDK_INT >= 30) {
            val insets = wm.currentWindowMetrics.windowInsets
            statusPx = insets.getInsets(WindowInsets.Type.statusBars()).top
            val screenH = wm.currentWindowMetrics.bounds.height()
            insets.displayCutout?.boundingRects?.filter { it.top < screenH / 4 }?.minByOrNull { it.top }?.let {
                cutBottom = it.bottom
                cutWidth = it.width()
                // Samsung reports a rect from the screen edge down to the hole; assume a round hole at its bottom
                holeTop = if (it.top <= 1) max(0, it.bottom - it.width()) else it.top
            }
        } else {
            val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) statusPx = ctx.resources.getDimensionPixelSize(id)
        }
        val statusDp = statusPx / density
        val cutDp = cutBottom / density
        val toolbar = max(max(statusDp, cutDp + 6f), 36f)
        val pillH = max(max(statusDp, cutDp + 6f), 30f)
        return IslandGeometry(
            toolbarH = toolbar.dp,
            pillW = 150.dp,
            pillH = pillH.dp,
            cameraW = max(cutWidth / density, 24f).dp,
            cutTop = (holeTop / density).dp,
            cutH = ((cutBottom - holeTop).coerceAtLeast(0) / density).dp,
        )
    }
}

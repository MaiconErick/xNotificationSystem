package com.maiconerick.xnotificationsystem

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.maiconerick.xnotificationsystem.island.AodLayout
import com.maiconerick.xnotificationsystem.island.Face
import com.maiconerick.xnotificationsystem.island.Island
import com.maiconerick.xnotificationsystem.island.IslandSettings
import com.maiconerick.xnotificationsystem.island.Kind
import com.maiconerick.xnotificationsystem.island.LockMode
import com.maiconerick.xnotificationsystem.island.Mascot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * xAOD: our own always-on display. A pure-black lock screen shown over the keyguard when the
 * phone locks (or when the charger is plugged in); a tap (or swipe up) hands over to the real
 * unlock (fingerprint / PIN). Samsung's own AOD is meant to be switched off while this is used.
 */
class LockActivity : ComponentActivity(), SensorEventListener {

    companion object {
        private const val EXTRA_ALWAYS_ON = "alwaysOn"
        private const val EXTRA_PREVIEW = "preview"
        private var current: WeakReference<LockActivity>? = null

        fun start(ctx: Context, alwaysOn: Boolean, preview: Boolean = false) {
            ctx.startActivity(
                Intent(ctx, LockActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                    .putExtra(EXTRA_ALWAYS_ON, alwaysOn)
                    .putExtra(EXTRA_PREVIEW, preview)
            )
        }

        fun finishIfShowing() {
            current?.get()?.let { if (!it.isFinishing) it.finish() }
        }

        /** Charger unplugged: in "while charging" mode the display should go dark now. */
        fun chargerRemoved() {
            val a = current?.get() ?: return
            if (a.isFinishing || a.preview || !a.alwaysOn) return
            if (IslandSettings.lockMode == LockMode.WhileCharging) a.sleepNow()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var alwaysOn = false
    private var preview = false

    /** Proximity sensor says something covers the phone (pocket, face down). */
    private var covered by mutableStateOf(false)
    private var sensors: SensorManager? = null
    private var sessionStart = 0L
    private var sessionLevel = -1
    private var sessionCharging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandSettings.init(this)
        current = WeakReference(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        applyMode(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (preview) finish()
            }
        })
        setContent {
            LockScreen(alwaysOn = alwaysOn, preview = preview, covered = covered, onUnlock = ::unlock, onAwake = ::setAwake)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyMode(intent)
    }

    private fun batteryLevel(): Int {
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    private fun applyMode(i: Intent) {
        // close out the previous session's statistics before a new mode starts
        recordUsage()
        alwaysOn = i.getBooleanExtra(EXTRA_ALWAYS_ON, false)
        preview = i.getBooleanExtra(EXTRA_PREVIEW, false)
        setShowWhenLocked(true)
        setTurnScreenOn(alwaysOn)
        handler.removeCallbacksAndMessages(null)
        covered = false
        sensors?.unregisterListener(this)
        if (alwaysOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = IslandSettings.aodBacklight }
            val charging = IslandRuntime.isCharging(this)
            // on the charger in "while charging" mode it is a bedside clock: no timeout
            val stayWhileCharging = IslandSettings.lockMode == LockMode.WhileCharging && charging
            if (!stayWhileCharging && !preview) handler.postDelayed(::sleepNow, IslandSettings.lockMinutes * 60_000L)
            if (IslandSettings.aodPocket) {
                val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                sensors = sm
                sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
                    sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
            }
            sessionStart = System.currentTimeMillis()
            sessionLevel = batteryLevel()
            sessionCharging = charging
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            sessionStart = 0L
        }
    }

    /** Touched: full brightness so the fingerprint sensor and the screen are usable; asleep: dim again. */
    private fun setAwake(awake: Boolean) {
        if (!alwaysOn) return
        window.attributes = window.attributes.apply {
            screenBrightness = if (awake) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else IslandSettings.aodBacklight
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        covered = e.values[0] < (e.sensor.maximumRange.coerceAtMost(5f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Adds this always-on session to the "battery use" statistics (battery-only sessions of 5+ min). */
    private fun recordUsage() {
        if (sessionStart == 0L) return
        val minutes = (System.currentTimeMillis() - sessionStart) / 60_000f
        val drop = sessionLevel - batteryLevel()
        val onBattery = !sessionCharging && !IslandRuntime.isCharging(this)
        if (onBattery && minutes >= 5f && sessionLevel >= 0 && drop >= 0) {
            IslandSettings.aodMinutes += minutes
            IslandSettings.aodDrop += drop
        }
        sessionStart = 0L
    }

    /** Always-on timeout / charger removed: let the display really go dark (and don't bounce back on). */
    private fun sleepNow() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        IslandRuntime.ignoreNextScreenOff = true
        val locked = IslandRuntime.lockScreenAction?.invoke() ?: false
        if (!locked) {
            // without the accessibility host the system timeout turns it off; stay ready for that
            setTurnScreenOn(false)
        }
    }

    private fun unlock() {
        val km = getSystemService(KeyguardManager::class.java)
        if (preview || !km.isKeyguardLocked) {
            finish()
            return
        }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { finish() }
        })
    }

    override fun onStart() {
        super.onStart()
        Island.hideCompletely()
    }

    override fun onStop() {
        super.onStop()
        Island.setLockCharge(null)
        if (Island.state.value.kind == Kind.Hidden) Island.collapse()
    }

    override fun onDestroy() {
        recordUsage()
        sensors?.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
        if (current?.get() === this) current = null
        super.onDestroy()
    }
}

@Composable
internal fun LockScreen(
    alwaysOn: Boolean,
    preview: Boolean,
    covered: Boolean,
    onUnlock: () -> Unit,
    onAwake: (Boolean) -> Unit,
    /** Overlay engine: a touch landed, fade the whole screen away to show the real lock screen. */
    revealed: Boolean = false,
    hintText: String? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var now by remember { mutableStateOf(Date()) }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    var awake by remember { mutableStateOf(false) }
    val jump = remember { Animatable(0f) }
    val driftX = remember { Animatable(0f) }
    val driftY = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (b != null) {
                val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                battery = if (level >= 0) level * 100 / scale else -1
                val st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
            delay(1000L - System.currentTimeMillis() % 1000L + 5)
        }
    }
    // charging: the island pill stays visible over the lock screen with a bolt + percentage
    LaunchedEffect(charging, battery, covered) {
        Island.setLockCharge(if (IslandSettings.aodPill && charging && battery >= 0 && !covered) battery else null)
    }
    DisposableEffect(Unit) { onDispose { Island.setLockCharge(null) } }
    // burn-in protection: everything slowly wanders a little
    LaunchedEffect(Unit) {
        while (true) {
            delay(40_000)
            launch { driftX.animateTo(Random.nextInt(-16, 17).toFloat(), tween(4000, easing = FastOutSlowInEasing)) }
            driftY.animateTo(Random.nextInt(-24, 25).toFloat(), tween(4000, easing = FastOutSlowInEasing))
        }
    }

    /**
     * A tap only wakes the screen (full brightness) so a finger on the sensor unlocks straight away;
     * asking the system for the unlock prompt here would make it wait for a second scan.
     * Swiping up opens the prompt (PIN / pattern); in a preview any touch just closes it.
     */
    fun wake(dismiss: Boolean) {
        if (awake && !dismiss) return
        awake = true
        onAwake(true)
        scope.launch {
            jump.snapTo(0f)
            jump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
            if (dismiss || preview) {
                delay(450)
                onUnlock()
            }
            delay(if (dismiss || preview) 1500 else 5000)
            awake = false
            onAwake(false)
            jump.snapTo(0f)
        }
    }

    val dim = IslandSettings.lockBrightness.coerceIn(0.2f, 1f) * if (alwaysOn) 0.85f else 1f
    val tint = IslandSettings.aodTint.color
    val layout = IslandSettings.aodLayout
    val hh = SimpleDateFormat("HH", Locale.getDefault()).format(now)
    val mm = SimpleDateFormat("mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH).format(now)

    val drift = Modifier.offset { IntOffset((driftX.value * density).roundToInt(), (driftY.value * density).roundToInt()) }
    val inf = rememberInfiniteTransition(label = "aod")
    val hint by inf.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "hint")
    val breath by inf.animateFloat(0.78f, 1f, infiniteRepeatable(tween(4600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val breathing = if (IslandSettings.aodBreathe && alwaysOn && !awake) Modifier.graphicsLayer { alpha = breath } else Modifier

    val shown by androidx.compose.animation.core.animateFloatAsState(if (revealed) 0f else 1f, tween(160), label = "reveal")
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown }
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { wake(false) } }
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(onDragEnd = {
                    if (dragged < -60f) wake(true)
                    dragged = 0f
                }) { _, dy -> dragged += dy }
            },
    ) {
        Column(
            Modifier.fillMaxSize().then(drift).then(breathing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (layout) {
                AodLayout.Classic -> {
                    Spacer(Modifier.weight(0.7f))
                    if (IslandSettings.lockClock) {
                        Text("$hh:$mm", color = tint, fontSize = 88.sp, fontWeight = FontWeight.Thin, letterSpacing = 2.sp)
                    }
                    DateLine(date)
                    BatteryLine(battery, charging)
                    Spacer(Modifier.weight(0.6f))
                    AodMascot(150, 127, awake, jump.value)
                    Spacer(Modifier.weight(1f))
                }
                AodLayout.Stacked -> {
                    Spacer(Modifier.weight(0.5f))
                    if (IslandSettings.lockClock) {
                        Text(hh, color = tint, fontSize = 132.sp, fontWeight = FontWeight.Bold, lineHeight = 116.sp)
                        Text(mm, color = tint.copy(alpha = 0.5f), fontSize = 132.sp, fontWeight = FontWeight.Bold, lineHeight = 116.sp)
                    }
                    DateLine(date)
                    BatteryLine(battery, charging)
                    Spacer(Modifier.weight(0.5f))
                    AodMascot(84, 71, awake, jump.value)
                    Spacer(Modifier.weight(0.6f))
                }
                AodLayout.Minimal -> {
                    Spacer(Modifier.height(110.dp))
                    if (IslandSettings.lockClock) {
                        Text("$hh:$mm", color = tint, fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp)
                    }
                    DateLine(date)
                    Spacer(Modifier.weight(1f))
                    AodMascot(170, 144, awake, jump.value)
                    Spacer(Modifier.weight(0.7f))
                    BatteryLine(battery, charging)
                    Spacer(Modifier.weight(0.3f))
                }
                AodLayout.Mascot -> {
                    Spacer(Modifier.weight(1f))
                    AodMascot(230, 195, awake, jump.value)
                    Spacer(Modifier.height(16.dp))
                    if (IslandSettings.lockClock) {
                        Text("$hh:$mm", color = tint.copy(alpha = 0.8f), fontSize = 30.sp, fontWeight = FontWeight.Light)
                    }
                    DateLine(date)
                    BatteryLine(battery, charging)
                    Spacer(Modifier.weight(1f))
                }
            }
            if (IslandSettings.aodHint) {
                Text(
                    hintText ?: if (awake) "Touch the sensor, or swipe up" else "Tap to wake",
                    color = Color(0xFF8C8C9A).copy(alpha = hint), fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(48.dp))
        }
        // brightness: a black scrim on top (alpha on the content itself would clip the mascot's halo);
        // covered (pocket / face down) = fully black
        val scrim = if (covered) 1f else if (awake) 0f else 1f - dim
        if (scrim > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)))
    }
}

@Composable
private fun DateLine(date: String) {
    if (IslandSettings.lockDate) Text(date, color = Color(0xFF8C8C9A), fontSize = 16.sp)
}

@Composable
private fun BatteryLine(battery: Int, charging: Boolean) {
    if (!IslandSettings.lockBattery || battery < 0) return
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd, null,
            tint = if (charging) Color(0xFF5EE3A8) else Color(0xFF8C8C9A), modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("$battery%", color = Color(0xFF8C8C9A), fontSize = 14.sp)
    }
}

@Composable
private fun AodMascot(w: Int, h: Int, awake: Boolean, jump: Float) {
    Box(Modifier.graphicsLayer { translationY = -jump * 40.dp.toPx() }) {
        if (!awake) Snores(Modifier.align(Alignment.TopStart).offset(x = (-30).dp * (w / 150f), y = (-50).dp * (h / 127f)))
        Mascot(
            Modifier.size(w.dp, h.dp),
            face = if (awake) Face.Happy else Face.Sleep,
            hands = awake,
        )
    }
}

/** Three little "z"s drifting up and fading, staggered. */
@Composable
private fun Snores(modifier: Modifier) {
    val inf = rememberInfiniteTransition(label = "zzz")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "t")
    Box(modifier.size(70.dp, 70.dp)) {
        for (i in 0..2) {
            val p = (t + i / 3f) % 1f
            val a = if (p < 0.2f) p / 0.2f else (1f - p) / 0.8f
            Text(
                "z",
                color = Color(0xFFB9A4FF).copy(alpha = a.coerceIn(0f, 1f) * 0.9f),
                fontSize = (12 + p * 14).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-p * 30).dp, y = (-p * 50).dp),
            )
        }
    }
}

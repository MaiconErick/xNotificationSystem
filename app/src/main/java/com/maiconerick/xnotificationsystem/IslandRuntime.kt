package com.maiconerick.xnotificationsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.launch
import com.maiconerick.xnotificationsystem.island.Category
import com.maiconerick.xnotificationsystem.island.Island
import com.maiconerick.xnotificationsystem.island.IslandSettings
import com.maiconerick.xnotificationsystem.island.LockMode
import com.maiconerick.xnotificationsystem.island.Scenario

/**
 * Glue shared by both overlay hosts (accessibility service = above the status bar, preferred;
 * foreground service = plain app overlay, fallback): sounds, "open app", delayed scenarios and
 * the screen on/off hooks for the unlock greeting and the AMOLED lock screen.
 */
object IslandRuntime {
    private val handler = Handler(Looper.getMainLooper())
    private val hubScope = kotlinx.coroutines.MainScope()
    private var sfx: Sfx? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var assistantReceiver: BroadcastReceiver? = null
    private var assistantWoke = false
    private var appCtx: Context? = null

    /** Set when we turn the screen off ourselves (always-on timeout) so it doesn't relaunch. */
    var ignoreNextScreenOff = false

    /** Turns the screen off; only possible while the accessibility host is connected. */
    var lockScreenAction: (() -> Boolean)? = null

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        appCtx = app
        IslandSettings.init(app)
        sfx?.release()
        val s = Sfx(app)
        sfx = s
        Island.onPing = { kind, snd, hap ->
            if (!(IslandSettings.hideInLandscape && isLandscape(app)))
                s.ping(kind, IslandSettings.soundOn && snd, IslandSettings.hapticsOn && hap)
        }
        Island.onOpenApp = {
            ctx.startActivity(
                Intent(app, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        }
        Island.onReply = { chat, text -> HubClient.reply(chat, text) }
        Island.onMarkRead = { chat -> hubScope.launch { HubClient.markRead(chat) } }
        Island.reset()
        if (!IslandSettings.keepPill) Island.hideCompletely()
        registerScreenEvents(app)
        HubClient.start()
    }

    fun isLandscape(ctx: Context): Boolean =
        ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun uninstall() {
        handler.removeCallbacksAndMessages(null)
        screenReceiver?.let { r -> appCtx?.let { runCatching { it.unregisterReceiver(r) } } }
        screenReceiver = null
        assistantReceiver?.let { r -> appCtx?.let { runCatching { it.unregisterReceiver(r) } } }
        assistantReceiver = null
        HubClient.stop()
        Island.onPing = null
        Island.onOpenApp = null
        Island.onReply = null
        Island.onMarkRead = null
        Island.reset()
        sfx?.release()
        sfx = null
    }

    private fun registerScreenEvents(app: Context) {
        screenReceiver?.let { runCatching { app.unregisterReceiver(it) } }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> onScreenOff(c)
                    Intent.ACTION_USER_PRESENT -> onUnlocked()
                    Intent.ACTION_POWER_CONNECTED -> onPowerConnected(c)
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        LockActivity.chargerRemoved()
                        IslandAccessibilityService.instance?.aod?.chargerRemoved()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        app.registerReceiver(r, filter)
        screenReceiver = r

        // xAssistant (another app) tells us what the voice assistant is doing
        assistantReceiver?.let { runCatching { app.unregisterReceiver(it) } }
        val ar = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onAssistant(c, i)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            app, ar, IntentFilter(ASSISTANT_ACTION), androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        assistantReceiver = ar
    }

    /**
     * Wake word heard with the phone locked and dark: light xAOD so the Umbra card is visible, and
     * put the screen back to sleep a few seconds after the conversation (unless xAOD should stay on).
     */
    private fun onAssistant(c: Context, i: Intent) {
        val phase = i.getStringExtra("phase") ?: return
        val live = phase == "Listening" || phase == "Thinking" || phase == "Speaking"
        val pm = c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val km = c.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (live && !pm.isInteractive && km.isKeyguardLocked && IslandSettings.lockScreen &&
            IslandSettings.cat(Category.Assistant).enabled
        ) {
            showAod(c, alwaysOn = true)
            assistantWoke = true
        }
        Island.onAssistant(phase, i.getStringExtra("user").orEmpty(), i.getStringExtra("reply").orEmpty(),
            i.getStringExtra("error").orEmpty())
        if (!live && assistantWoke) {
            assistantWoke = false
            handler.postDelayed({
                val aod = IslandAccessibilityService.instance?.aod
                if (km.isKeyguardLocked && !aodShouldStayOn(c) && aod?.showing == true) aod.sleepNow()
            }, 5000)
        }
    }

    fun isCharging(c: Context): Boolean {
        val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val st = b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        return st == android.os.BatteryManager.BATTERY_STATUS_CHARGING || st == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    fun batteryLevel(c: Context): Int {
        val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    /**
     * Shows xAOD with the preferred engine: the overlay (real lock screen stays live underneath, so
     * the fingerprint works straight away) or, without the accessibility host, the activity.
     */
    fun showAod(c: Context, alwaysOn: Boolean) {
        val aod = IslandAccessibilityService.instance?.aod
        if (IslandSettings.aodOverlay && aod != null) aod.show(alwaysOn)
        else LockActivity.start(c, alwaysOn = alwaysOn)
    }

    private fun hideAod() {
        IslandAccessibilityService.instance?.aod?.hide()
        LockActivity.finishIfShowing()
    }

    /** xAOD rule: should the lock screen stay lit (dim) right now? */
    fun aodShouldStayOn(c: Context): Boolean {
        if (!IslandSettings.lockScreen) return false
        val modeOn = when (IslandSettings.lockMode) {
            LockMode.OnWake -> false
            LockMode.AlwaysOn -> true
            LockMode.WhileCharging -> isCharging(c)
        }
        return modeOn && IslandSettings.aodInSchedule()
    }

    private fun onPowerConnected(c: Context) {
        if (!IslandSettings.lockScreen || IslandSettings.lockMode != LockMode.WhileCharging) return
        val pm = c.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val km = c.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (pm.isInteractive || !km.isKeyguardLocked) return
        if (aodShouldStayOn(c)) showAod(c, alwaysOn = true)
    }

    private fun onScreenOff(c: Context) {
        handler.removeCallbacksAndMessages(GREET_TOKEN)
        if (ignoreNextScreenOff) {
            ignoreNextScreenOff = false
            return
        }
        if (!IslandSettings.lockScreen) return
        Island.collapse()
        showAod(c, alwaysOn = aodShouldStayOn(c))
    }

    private fun onUnlocked() {
        hideAod()
        if (!IslandSettings.cat(Category.Greeting).enabled) return
        // small pause so the launcher / app is on screen before the mascot pops out
        handler.postDelayed({ Island.greet() }, GREET_TOKEN, 450)
    }

    fun schedule(sc: Scenario, delayMs: Long) {
        handler.removeCallbacksAndMessages(PLAY_TOKEN)
        handler.postDelayed({ Island.play(sc) }, PLAY_TOKEN, delayMs)
    }

    fun greetLater(delayMs: Long) {
        handler.postDelayed({ Island.greet() }, GREET_TOKEN, delayMs)
    }

    /** Picks whichever host is available and plays the scenario after [delayMs]. */
    fun play(ctx: Context, sc: Scenario, delayMs: Long) {
        if (IslandAccessibilityService.instance != null) schedule(sc, delayMs)
        else IslandService.play(ctx, sc, delayMs)
    }

    val isRunning get() = IslandAccessibilityService.instance != null || IslandService.running

    const val ASSISTANT_ACTION = "com.maiconerick.xassistant.STATE"
    private val PLAY_TOKEN = Any()
    private val GREET_TOKEN = Any()
}

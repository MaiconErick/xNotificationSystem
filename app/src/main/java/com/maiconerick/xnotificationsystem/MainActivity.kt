package com.maiconerick.xnotificationsystem

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.BackHandler
import com.maiconerick.xnotificationsystem.island.Activity
import com.maiconerick.xnotificationsystem.island.AodLayout
import com.maiconerick.xnotificationsystem.island.AodTint
import com.maiconerick.xnotificationsystem.island.Badge
import com.maiconerick.xnotificationsystem.island.Category
import com.maiconerick.xnotificationsystem.island.Face
import com.maiconerick.xnotificationsystem.island.GreetStyle
import com.maiconerick.xnotificationsystem.island.Island
import com.maiconerick.xnotificationsystem.island.IslandSettings
import com.maiconerick.xnotificationsystem.island.IslandStyle
import com.maiconerick.xnotificationsystem.island.LockMode
import com.maiconerick.xnotificationsystem.island.Mascot
import com.maiconerick.xnotificationsystem.island.Motion
import com.maiconerick.xnotificationsystem.island.PanelColor
import com.maiconerick.xnotificationsystem.island.PillButton
import com.maiconerick.xnotificationsystem.island.Scenario
import com.maiconerick.xnotificationsystem.island.Skin
import com.maiconerick.xnotificationsystem.island.TextDim
import com.maiconerick.xnotificationsystem.island.TextPrimary
import com.maiconerick.xnotificationsystem.island.TextSecondary
import kotlin.math.roundToInt

private val Bg = Color(0xFF000000)
private val Surface = Color(0xFF111114)
private val Accent = Color(0xFFB9A4FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        IslandSettings.init(this)
        setContent { Root() }
    }
}

private fun dotColor(sc: Scenario) = when (sc) {
    Scenario.Hello -> Color(0xFFFFFFFF)
    Scenario.Session -> Color(0xFF3B82F6)
    Scenario.Permission -> Color(0xFFF5A524)
    Scenario.Finished -> Color(0xFF22C55E)
    Scenario.Files -> Color(0xFF5EE3A8)
    Scenario.Chat -> Color(0xFF8B5CF6)
    Scenario.Message -> Color(0xFF25D366)
    Scenario.RateLimit -> Color(0xFFFF6B9A)
    Scenario.FullDemo -> Accent
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var aboveBar by remember { mutableStateOf(IslandAccessibilityService.instance != null) }
    var running by remember { mutableStateOf(IslandRuntime.isRunning) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                canOverlay = Settings.canDrawOverlays(ctx)
                running = IslandRuntime.isRunning
                aboveBar = IslandAccessibilityService.instance != null
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Column(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Mascot(Modifier.size(64.dp, 54.dp), face = Face.Happy, badge = Badge.Working, hands = true, onTap = {})
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("xNotificationSystem", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (aboveBar) "Island active · above the status bar" else if (running) "Island active · fallback overlay" else "Island not running",
                    color = if (aboveBar || running) Color(0xFF5EE3A8) else TextSecondary, fontSize = 12.sp,
                )
            }
        }
        Row(
            Modifier.padding(16.dp).clip(CircleShape).background(Surface).padding(4.dp),
        ) {
            listOf("Tests", "Categories", "AOD", "Customize").forEachIndexed { i, label ->
                val sel = tab == i
                val bg by animateColorAsState(if (sel) Color(0xFFF3F3F6) else Color.Transparent, tween(200), label = "tab")
                val fg by animateColorAsState(if (sel) Color(0xFF111114) else TextSecondary, tween(200), label = "tabFg")
                Box(
                    Modifier.weight(1f).clip(CircleShape).background(bg).clickable { tab = i }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        when (tab) {
            0 -> TestsTab(canOverlay, aboveBar, running, onRunning = { running = it }, onNeedOverlay = { canOverlay = false })
            1 -> CategoriesTab()
            2 -> AodTab()
            else -> CustomizeTab()
        }
    }
}

// ---- Tests -----------------------------------------------------------------------------------------

@Composable
private fun TestsTab(
    canOverlay: Boolean,
    aboveBar: Boolean,
    running: Boolean,
    onRunning: (Boolean) -> Unit,
    onNeedOverlay: () -> Unit,
) {
    val ctx = LocalContext.current
    var delayMs by remember { mutableLongStateOf(2000L) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }

    fun launch(sc: Scenario) {
        if (IslandAccessibilityService.instance == null && !Settings.canDrawOverlays(ctx)) {
            onNeedOverlay(); return
        }
        IslandRuntime.play(ctx, sc, delayMs)
        onRunning(true)
        if (delayMs > 0) Toast.makeText(ctx, "${sc.title} in ${delayMs / 1000}s — go anywhere", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!canOverlay && !aboveBar) item {
            Section("Overlay permission needed") {
                Note("The island draws over other apps. Allow \"Display over other apps\" for xNotificationSystem.")
                Spacer(Modifier.height(10.dp))
                PillButton("Open settings", light = true) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                }
            }
        }
        if (!aboveBar) item {
            Section("Draw above the status bar") {
                Note(
                    "Normal overlays sit under the clock and battery icons. Turn on the \"xNotificationSystem island\" " +
                            "accessibility service so the island wraps the camera (it does not read your screen). " +
                            "It's also needed for the unlock greeting and lock screen to work reliably."
                )
                Spacer(Modifier.height(10.dp))
                PillButton("Accessibility settings", light = true) {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        item {
            Section("Show after") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0L to "Now", 2000L to "2 s", 5000L to "5 s", 10000L to "10 s").forEach { (ms, label) ->
                        Chip(label, selected = delayMs == ms) { delayMs = ms }
                    }
                }
            }
        }
        items(Scenario.entries.toList()) { sc -> ScenarioRow(sc) { launch(sc) } }
        item {
            Section("Extras") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Say hi", light = true) {
                        if (IslandRuntime.isRunning) Island.greet() else launch(Scenario.Hello)
                    }
                    PillButton("Lock screen preview") { LockActivity.start(ctx, alwaysOn = false, preview = true) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(if (running || aboveBar) "Collapse" else "Start island") {
                        if (aboveBar || running) Island.collapse()
                        else if (!Settings.canDrawOverlays(ctx)) onNeedOverlay()
                        else { IslandService.start(ctx); onRunning(true) }
                    }
                    if (running && !aboveBar) PillButton("Stop service") {
                        IslandService.stop(ctx); onRunning(false)
                    }
                }
            }
        }
        item {
            Section("About") {
                Text(
                    "xNotificationSystem v${version?.versionName ?: "?"} (build ${version?.longVersionCode ?: 0})",
                    color = TextPrimary, fontSize = 14.sp,
                )
                Note("Tests only — not connected to real notifications yet. Tip: poke the mascot 5 times fast.")
            }
        }
    }
}

// ---- Categories ------------------------------------------------------------------------------------

private fun catColor(c: Category) = when (c) {
    Category.Messages -> Color(0xFF25D366)
    Category.Tasks -> Color(0xFF9DA8FF)
    Category.Builds -> Color(0xFFF5A524)
    Category.Greeting -> Color(0xFFFFFFFF)
    Category.Assistant -> Color(0xFFB48CFF)
}

private fun catSummary(c: Category): String {
    val p = IslandSettings.cat(c)
    if (!p.enabled) return "Off"
    val time = when {
        p.sticky -> "stays open"
        p.timeoutSec > 0 -> "${p.timeoutSec} s"
        else -> "default time"
    }
    val skin = if (p.skinName.isEmpty()) "" else " · ${IslandSettings.skinFor(c).label}"
    return "$time · sound ${if (p.sound) "on" else "off"}$skin"
}

/** List of categories; tapping one opens its own settings page. */
@Composable
private fun CategoriesTab() {
    var open by remember { mutableStateOf<Category?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val c = open
    if (c != null) {
        CategoryScreen(c) { open = null }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Note("Each kind of card has its own timeout, sounds, mascot and options. Tap one to edit it.") }
        items(Category.entries.toList()) { cat ->
            val p = IslandSettings.cat(cat)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
                    .clickable { open = cat }.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(catColor(cat)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(cat.label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(cat.blurb, color = TextSecondary, fontSize = 12.sp)
                    Text(catSummary(cat), color = TextDim, fontSize = 11.sp)
                }
                Switch(
                    checked = p.enabled, onCheckedChange = { p.enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF111114), checkedTrackColor = Accent,
                        uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Color(0xFF222227),
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryScreen(cat: Category, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s = IslandSettings
    val p = s.cat(cat)
    val mode = when {
        p.sticky -> 2
        p.timeoutSec > 0 -> 1
        else -> 0
    }

    fun ready(): Boolean {
        if (IslandRuntime.isRunning) return true
        Toast.makeText(ctx, "Start the island first (Tests tab)", Toast.LENGTH_SHORT).show()
        return false
    }

    fun preview() {
        if (!ready()) return
        when (cat) {
            Category.Messages -> Island.onMessage("WhatsApp", "Mom", "Are you coming for dinner on Sunday? I'm making lasagna", "", false)
            Category.Greeting -> Island.greet()
            Category.Assistant -> {
                val main = android.os.Handler(android.os.Looper.getMainLooper())
                Island.onAssistant("Listening", "", "", "")
                main.postDelayed({ Island.onAssistant("Thinking", "nossa, tá muito quente aqui, liga o ar", "", "") }, 1500)
                main.postDelayed({ Island.onAssistant("Speaking", "nossa, tá muito quente aqui, liga o ar", "Pode deixar, liguei o ar no 23.", "") }, 3000)
                main.postDelayed({ Island.onAssistant("Waiting", "nossa, tá muito quente aqui, liga o ar", "Pode deixar, liguei o ar no 23.", "") }, 5500)
            }
            Category.Tasks, Category.Builds -> {
                val now = System.currentTimeMillis()
                Island.onActivity(
                    Activity(
                        id = "preview-${cat.id}", title = if (cat == Category.Builds) "xFinances" else "Fix 7 bugs",
                        source = if (cat == Category.Builds) "xBuildServer" else "Claude", kind = if (cat == Category.Builds) "build" else "task",
                        step = 2, total = 5, label = if (cat == Category.Builds) "Compiling" else "Fixing the screen",
                        detail = "Preview card", startedMs = now - 12_000, updatedMs = now,
                    ),
                    quiet = false,
                )
            }
        }
    }

    BackHandler(onBack = onBack)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Surface).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Text("‹", color = TextPrimary, fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(10.dp).clip(CircleShape).background(catColor(cat)))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(cat.label, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(cat.blurb, color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        item {
            Section("General") {
                ToggleRow("Show these cards", null, p.enabled) { p.enabled = it }
                Spacer(Modifier.height(4.dp))
                PillButton("Preview", light = true) { preview() }
            }
        }
        item {
            Section("Timeout") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Default (${s.autoHideSec} s)", mode == 0) { p.sticky = false; p.timeoutSec = 0 }
                    Chip("Custom", mode == 1) { p.sticky = false; if (p.timeoutSec <= 0) p.timeoutSec = 10 }
                    Chip("Stay open", mode == 2) { p.sticky = true }
                }
                if (mode == 1) {
                    Spacer(Modifier.height(6.dp))
                    SliderRow("Close after", p.timeoutSec.toFloat(), 2f..120f, steps = 117, fmt = { "${it.roundToInt()} s" }) {
                        p.timeoutSec = it.roundToInt()
                    }
                }
                Note(
                    when (mode) {
                        0 -> "Follows the global auto-hide time (Customize → Motion)."
                        1 -> "These cards close by themselves after this long."
                        else -> "These cards stay until you swipe them up or tap outside."
                    }
                )
            }
        }
        item {
            Section("Alerts") {
                ToggleRow("Sound", "Chime when a card appears", p.sound) { p.sound = it }
                ToggleRow("Vibration", "Haptic tick when a card appears", p.haptics) { p.haptics = it }
                Note("The global Sounds / Vibration switches (Customize → Motion) still apply on top.")
            }
        }
        item {
            Section("Mascot") {
                Text("Pick the character that appears on these cards.", color = TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Same as global", p.skinName.isEmpty()) { p.skinName = "" }
                    Skin.entries.forEach { sk -> Chip(sk.label, p.skinName == sk.name) { p.skinName = sk.name } }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Mascot(Modifier.size(100.dp, 85.dp), skin = s.skinFor(cat), face = Face.Happy, hands = true)
                }
            }
        }
        when (cat) {
            Category.Messages -> item {
                Section("WhatsApp options") {
                    ToggleRow("Typewriter text", "Message text types itself out, Matrix style, with a _ cursor", p.typewriter) { p.typewriter = it }
                    if (p.typewriter) {
                        SliderRow("Typing speed", p.typeSpeedMs.toFloat(), 10f..120f, steps = 21, fmt = { "${it.roundToInt()} ms / letter" }) {
                            p.typeSpeedMs = it.roundToInt()
                        }
                    }
                    ToggleRow("Profile photo", "The chat photo next to the name", p.photo) { p.photo = it }
                    ToggleRow("Message preview", "Off = only the sender's name", p.preview) { p.preview = it }
                    Note("Only real messages from xWapp pop up; \"typing…\" events are ignored. Muted and archived chats stay quiet. The timeout above starts once the text has finished typing.")
                }
            }
            Category.Tasks, Category.Builds -> item {
                Section("Progress options") {
                    ToggleRow("Pop up on every step", "Off = only when it starts and when it ends (the pill still counts parts)", p.everyStep) {
                        p.everyStep = it
                    }
                }
            }
            Category.Greeting -> item {
                Section("Greeting text") {
                    Note("Style, name and message are in the Customize tab → Greeting.")
                }
            }
            Category.Assistant -> item {
                Section("Umbra") {
                    Note("Shown by the xAssistant app when it hears \"Umbra\". With the phone locked and the screen off it lights xAOD so you can see the card, then turns the screen off again.")
                }
            }
        }
    }
}

// ---- xAOD ------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AodTab() {
    val ctx = LocalContext.current
    val s = IslandSettings
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Section("xAOD") {
                ToggleRow("Use xAOD", "Our own always-on display, replacing Samsung's", s.lockScreen) { s.lockScreen = it }
                ToggleRow(
                    "Fingerprint straight from the AOD",
                    "Draws over the real lock screen so the sensor works without a tap (needs the accessibility service). Off = full-screen activity.",
                    s.aodOverlay,
                ) { s.aodOverlay = it }
                Note(
                    "Turn Samsung's own Always On Display off (Settings → Lock screen and AOD) so the two don't fight. " +
                            "Tap the screen or swipe up to unlock as usual; the fingerprint sensor works while it is lit."
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Preview", light = true) { LockActivity.start(ctx, alwaysOn = false, preview = true) }
                    PillButton("Preview always-on") { LockActivity.start(ctx, alwaysOn = true, preview = true) }
                    PillButton("Test now") { IslandRuntime.showAod(ctx, alwaysOn = true) }
                    PillButton("Display settings") {
                        ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }

        item {
            Section("When") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LockMode.entries.forEach { m -> Chip(m.label, s.lockMode == m) { s.lockMode = m } }
                }
                Note(
                    when (s.lockMode) {
                        LockMode.OnWake -> "Dark when locked; the screen shows xAOD for a moment when you wake the phone."
                        LockMode.AlwaysOn -> "Stays lit (very dim) right after locking, until the time below runs out."
                        LockMode.WhileCharging -> "Lights up when you plug in the charger and stays on while charging, like a bedside clock. Goes dark when you unplug."
                    }
                )
                if (s.lockMode == LockMode.AlwaysOn) {
                    SliderRow("Turn off after", s.lockMinutes.toFloat(), 1f..60f, fmt = { "${it.roundToInt()} min" }) {
                        s.lockMinutes = it.roundToInt()
                    }
                }
                if (s.lockMode != LockMode.OnWake) {
                    Spacer(Modifier.height(4.dp))
                    ToggleRow("Only during certain hours", "Outside them the phone behaves like \"When I wake it\"", s.aodSchedule) { s.aodSchedule = it }
                    AnimatedVisibility(s.aodSchedule) {
                        Column {
                            SliderRow("From", s.aodFromHour.toFloat(), 0f..23f, steps = 22, fmt = { "%02d:00".format(it.roundToInt()) }) {
                                s.aodFromHour = it.roundToInt()
                            }
                            SliderRow("Until", s.aodToHour.toFloat(), 0f..23f, steps = 22, fmt = { "%02d:00".format(it.roundToInt()) }) {
                                s.aodToHour = it.roundToInt()
                            }
                        }
                    }
                    ToggleRow("Black in a pocket / face down", "The proximity sensor turns the lit screen fully black", s.aodPocket) { s.aodPocket = it }
                }
            }
        }

        item {
            Section("Look") {
                Label("Layout")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AodLayout.entries.forEach { l -> Chip(l.label, s.aodLayout == l) { s.aodLayout = l } }
                }
                Spacer(Modifier.height(10.dp))
                Label("Clock colour")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AodTint.entries.forEach { t -> ColorChip(t.label, t.color, s.aodTint == t) { s.aodTint = t } }
                }
                Spacer(Modifier.height(8.dp))
                SliderRow("Brightness", s.lockBrightness, 0.2f..1f, fmt = { "${(it * 100).roundToInt()}%" }) { s.lockBrightness = it }
                SliderRow("Screen backlight", s.aodBacklight, 0f..0.05f, fmt = { "%.1f%%".format(it * 100) }) { s.aodBacklight = it }
                Note("Backlight while the screen is lit (0% = the panel's minimum, about 1 nit). Samsung's fingerprint icon is drawn above xAOD and can't be hidden, so lower this to dim it.")
                ToggleRow(
                    "Extra dim while lit",
                    if (ExtraDim.available(ctx)) "Goes below the panel minimum (dims the fingerprint icon too); your own Extra dim setting comes back on touch"
                    else "Needs: adb shell pm grant com.maiconerick.xnotificationsystem android.permission.WRITE_SECURE_SETTINGS",
                    s.aodExtraDim,
                ) { s.aodExtraDim = it }
                ToggleRow("Clock", null, s.lockClock) { s.lockClock = it }
                ToggleRow("Date", null, s.lockDate) { s.lockDate = it }
                ToggleRow("Battery", null, s.lockBattery) { s.lockBattery = it }
                ToggleRow("Island pill while charging", "Bolt + percentage in the pill by the camera", s.aodPill) { s.aodPill = it }
                ToggleRow("Breathing glow", "Slow fade in and out (also helps against burn-in)", s.aodBreathe) { s.aodBreathe = it }
                ToggleRow("\"Tap to wake\" hint", null, s.aodHint) { s.aodHint = it }
            }
        }

        item {
            Section("Battery use") {
                if (s.aodMinutes >= 30f) {
                    val perHour = s.aodDrop / (s.aodMinutes / 60f)
                    Text("≈ ${"%.1f".format(perHour)} % per hour", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Note(
                        "Measured over ${"%.1f".format(s.aodMinutes / 60f)} h of always-on time on battery " +
                                "(${"%.1f".format(s.aodDrop)} points lost). Sessions on the charger don't count."
                    )
                } else {
                    Text("Not enough data yet", color = TextPrimary, fontSize = 15.sp)
                    Note(
                        "Use \"Always on\" on battery for a while (${s.aodMinutes.roundToInt()} of 30 min recorded). " +
                                "Only sessions of 5+ minutes on battery are counted."
                    )
                }
                Spacer(Modifier.height(8.dp))
                PillButton("Reset statistics") { s.aodMinutes = 0f; s.aodDrop = 0f }
            }
        }
    }
}

// ---- Customize -------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomizeTab() {
    val ctx = LocalContext.current
    val s = IslandSettings
    /** Settings that change the open panel: pop it open so the change is visible right away. */
    val peek = { Island.open() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Section("Mascot") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skin.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { sk -> SkinCard(sk, s.skin == sk, Modifier.weight(1f)) { s.skin = sk } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SliderRow("Size", s.mascotScale, 0.6f..1.5f, fmt = { "${(it * 100).roundToInt()}%" }, onDone = peek) { s.mascotScale = it }
                ToggleRow("Halo", "Soft glow behind the mascot", s.mascotGlow) { s.mascotGlow = it }
            }
        }

        item {
            Section("Island") {
                Label("Style")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IslandStyle.entries.forEach { st -> Chip(st.label, s.style == st) { s.style = st } }
                }
                Note(if (s.style == IslandStyle.Notch) "Attached to the top edge, like the video." else "A floating pill around the camera, like a Dynamic Island.")
                Spacer(Modifier.height(10.dp))
                SliderRow("Pill width", s.pillWidth, 90f..260f, fmt = { "${it.roundToInt()} dp" }) { s.pillWidth = it }
                SliderRow("Pill height", s.pillHeightExtra, -12f..24f, fmt = { signed(it) }) { s.pillHeightExtra = it }
                SliderRow("Distance from top", s.topOffset, 0f..80f, fmt = { "${it.roundToInt()} dp" }, onDone = peek) { s.topOffset = it }
                SliderRow("Side margins", s.sideMargin, 0f..40f, fmt = { "${it.roundToInt()} dp" }, onDone = peek) { s.sideMargin = it }
                SliderRow("Corner roundness", s.corner, 10f..56f, fmt = { "${it.roundToInt()} dp" }, onDone = peek) { s.corner = it }
                SliderRow("Card height", s.islandHeight, 0.8f..1.5f, fmt = { "${(it * 100).roundToInt()}%" }, onDone = peek) { s.islandHeight = it }
                Spacer(Modifier.height(6.dp))
                Label("Background")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelColor.entries.forEach { pc ->
                        ColorChip(pc.label, pc.card, s.panelColor == pc) { s.panelColor = pc; peek() }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SliderRow("Card glow", s.glow, 0f..1.8f, fmt = { "${(it * 100).roundToInt()}%" }, onDone = peek) { s.glow = it }
                ToggleRow("Starfield", "Warp-in stars on the home card", s.starfield) { s.starfield = it }
                ToggleRow("Shadow", "Soft drop shadow under the open island", s.shadow) { s.shadow = it }
                ToggleRow("Apps icon", "Colored grid on the right of the pill", s.gridIcon) { s.gridIcon = it }
                ToggleRow("Keep the pill visible", "Off = the island only appears for notifications", s.keepPill) {
                    s.keepPill = it
                    Island.collapse()
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Preview", light = true) { Island.goHome() }
                    PillButton("Reset look") { s.resetLook() }
                }
            }
        }

        item {
            Section("Motion") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Motion.entries.forEach { m -> Chip(m.label, s.motion == m) { s.motion = m; Island.goHome() } }
                }
                Spacer(Modifier.height(8.dp))
                SliderRow("Auto-hide after", s.autoHideSec.toFloat(), 2f..20f, steps = 17, fmt = { "${it.roundToInt()} s" }) {
                    s.autoHideSec = it.roundToInt()
                }
                ToggleRow("Sounds", "Little chimes on new cards", s.soundOn) { s.soundOn = it }
                ToggleRow("Vibration", "A haptic tick on new cards", s.hapticsOn) { s.hapticsOn = it }
                ToggleRow("Hide in landscape", "No island, sounds or vibration while playing / watching sideways", s.hideInLandscape) {
                    s.hideInLandscape = it
                }
            }
        }

        item {
            Section("Greeting") {
                ToggleRow("Say hi when I unlock", "The mascot pops out with a greeting", s.cat(Category.Greeting).enabled) {
                    s.cat(Category.Greeting).enabled = it
                }
                Spacer(Modifier.height(8.dp))
                Label("Style")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GreetStyle.entries.forEach { g -> Chip(g.label, s.greetStyle == g) { s.greetStyle = g } }
                }
                Spacer(Modifier.height(8.dp))
                if (s.greetStyle == GreetStyle.Custom) {
                    DarkField("Message", s.greetCustom) { s.greetCustom = it }
                } else {
                    DarkField("Your name", s.greetName) { s.greetName = it }
                }
                Spacer(Modifier.height(6.dp))
                Note("Preview: \"${s.greetingText()}\"")
                Spacer(Modifier.height(8.dp))
                PillButton("Try it", light = true) { Island.greet() }
            }
        }

        item {
            Section("Connections") {
                val st = HubClient.status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(
                            when (st) { "online" -> Color(0xFF5EE3A8); "offline" -> Color(0xFFFF6B9A); else -> TextDim }
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (st) { "online" -> "Pi hub connected · ${HubClient.route.removePrefix("http://")}"; "offline" -> "Pi hub unreachable"; else -> "Pi hub off" },
                        color = TextPrimary, fontSize = 14.sp,
                    )
                }
                Note("Live activities (Claude tasks, xBuildServer builds) and WhatsApp messages from xWapp come through the island hub on the Pi.")
                Spacer(Modifier.height(6.dp))
                ToggleRow("Use the Pi hub", null, s.hubEnabled) { s.hubEnabled = it; HubClient.restart() }
                DarkField("Hub address", s.hubUrl) { s.hubUrl = it }
                Spacer(Modifier.height(4.dp))
                ToggleRow("Live activities", "Task / build progress cards and the progress pill", s.cat(Category.Tasks).enabled) {
                    s.cat(Category.Tasks).enabled = it; s.cat(Category.Builds).enabled = it
                }
                ToggleRow("WhatsApp messages (xWapp)", "Muted and archived chats stay quiet", s.cat(Category.Messages).enabled) {
                    s.cat(Category.Messages).enabled = it
                }
                Note("Per-category timeout, sound, mascot and more: Categories tab.")
                Spacer(Modifier.height(6.dp))
                PillButton("Reconnect", light = true) { HubClient.restart() }
            }
        }
    }
}

private fun signed(v: Float): String {
    val r = v.roundToInt()
    return if (r > 0) "+$r dp" else "$r dp"
}

// ---- building blocks -------------------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color(0xFFF3F3F6) else Color(0xFF1E1E23), tween(200), label = "chip")
    val fg by animateColorAsState(if (selected) Color(0xFF111114) else TextPrimary, tween(200), label = "chipFg")
    Box(
        Modifier.clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ColorChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Color(0xFF1E1E23))
            .border(1.5.dp, if (selected) Accent else Color.Transparent, CircleShape)
            .clickable(onClick = onClick).padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun SkinCard(skin: Skin, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) Accent else Color.White.copy(alpha = 0.05f), tween(200), label = "skin")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B0B0D))
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Mascot(
            Modifier.size(76.dp, 64.dp), skin = skin,
            face = if (selected) Face.Happy else Face.Normal, hands = selected,
        )
        Spacer(Modifier.height(4.dp))
        Text(skin.label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(skin.tag, color = TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    fmt: (Float) -> String,
    onDone: (() -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(fmt(value), color = TextSecondary, fontSize = 13.sp)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            onValueChangeFinished = onDone,
            colors = SliderDefaults.colors(
                thumbColor = Color.White, activeTrackColor = Accent, inactiveTrackColor = Color(0xFF2A2A30),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ScenarioRow(sc: Scenario, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor(sc)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(sc.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sc.subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF232328)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = TextDim, fontSize = 12.sp)
        }
        Switch(
            checked = value, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF111114), checkedTrackColor = Accent,
                uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Color(0xFF222227),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun DarkField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            focusedBorderColor = Accent, unfocusedBorderColor = Color(0xFF2A2A30),
            focusedLabelColor = Accent, unfocusedLabelColor = TextSecondary, cursorColor = Accent,
        ),
    )
}

package com.maiconerick.xnotificationsystem.island

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class IslandStyle(val label: String) { Notch("Notch"), Floating("Floating") }

enum class PanelColor(val label: String, val color: Color, val card: Color) {
    Amoled("AMOLED", Color(0xFF000000), Color(0xFF111114)),
    Ink("Ink", Color(0xFF050506), Color(0xFF131316)),
    Graphite("Graphite", Color(0xFF16161A), Color(0xFF202026)),
    Midnight("Midnight", Color(0xFF0A0716), Color(0xFF17112B)),
    Blood("Blood moon", Color(0xFF0E0406), Color(0xFF1E0A10)),
}

enum class Motion(val label: String, val damping: Float, val stiffness: Float) {
    Bouncy("Bouncy", 0.6f, 1f),
    Normal("Normal", 0.76f, 1f),
    Snappy("Snappy", 0.85f, 1.8f),
    Slow("Slow", 0.82f, 0.5f),
}

enum class GreetStyle(val label: String) { Hi("Hi"), TimeOfDay("Time of day"), Custom("Custom") }

enum class LockMode(val label: String) {
    OnWake("When I wake it"), AlwaysOn("Always on"), WhileCharging("While charging")
}

/** xAOD screen designs. */
enum class AodLayout(val label: String) {
    Classic("Classic"), Stacked("Stacked"), Minimal("Minimal"), Mascot("Mascot")
}

enum class AodTint(val label: String, val color: Color) {
    White("White", Color(0xFFE8E6F2)),
    Lilac("Lilac", Color(0xFFB9A4FF)),
    Mint("Mint", Color(0xFF5EE3A8)),
    Amber("Amber", Color(0xFFF5A524)),
    Rose("Rose", Color(0xFFFF6B9A)),
}

/** Kinds of things the island shows; each has its own settings page (timeout, sound, skin …). */
enum class Category(
    val id: String,
    val label: String,
    val blurb: String,
    /** Pref key of the on/off switch (kept stable for the older top-level toggles). */
    val enabledKey: String,
) {
    Messages("messages", "WhatsApp", "Messages from xWapp", "showMessages"),
    Tasks("tasks", "Claude tasks", "Progress of long jobs, part by part", "showActivities"),
    Builds("builds", "Builds", "xBuildServer compile + install progress", "showBuilds"),
    Greeting("greeting", "Greeting", "The mascot says hi when you unlock", "greetOnUnlock"),
    Assistant("assistant", "Umbra", "Voice assistant: listening, thinking and its answers", "showAssistant"),
}

/**
 * Every user-tunable knob. Values are Compose state (the overlay and the settings screen live in
 * the same process, so changes show up on the island instantly) and are written through to prefs.
 */
object IslandSettings {
    private var sp: SharedPreferences? = null
    private val all = mutableListOf<Pref<*>>()

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)
        sp = p
        all.forEach { it.load(p) }
    }

    // mascot
    var skin by enumPref("skin", Skin.Classic)
    var mascotScale by floatPref("mascotScale", 1f)
    var mascotGlow by boolPref("mascotGlow", true)

    // island shape
    var style by enumPref("style", IslandStyle.Notch)
    var pillWidth by floatPref("pillWidth", 150f)
    var pillHeightExtra by floatPref("pillHeightExtra", 0f)
    var topOffset by floatPref("topOffset", 0f)
    var sideMargin by floatPref("sideMargin", 0f)
    var corner by floatPref("corner", 34f)
    var islandHeight by floatPref("islandHeight", 1f)
    var panelColor by enumPref("panelColor", PanelColor.Ink)
    var glow by floatPref("glow", 1f)
    var starfield by boolPref("starfield", true)
    var shadow by boolPref("shadow", true)
    var gridIcon by boolPref("gridIcon", true)

    // behaviour
    var motion by enumPref("motion", Motion.Normal)
    var autoHideSec by intPref("autoHideSec", 6)
    var keepPill by boolPref("keepPill", true)
    var soundOn by boolPref("sound", true)
    var hapticsOn by boolPref("haptics", true)
    /** Hide (and silence) the island while the screen is landscape — games, videos. */
    var hideInLandscape by boolPref("hideInLandscape", true)

    // greeting
    var greetName by stringPref("greetName", "Maicon")
    var greetStyle by enumPref("greetStyle", GreetStyle.TimeOfDay)
    var greetCustom by stringPref("greetCustom", "Welcome back!")

    // connections (Pi island hub)
    var hubEnabled by boolPref("hubEnabled", true)
    var hubUrl by stringPref("hubUrl", "192.168.1.128:8773")

    /** Per-category settings pages (WhatsApp, Claude tasks, Builds, Greeting). */
    class CatPrefs(val cat: Category) {
        var enabled by boolPref(cat.enabledKey, true)
        /** 0 = follow the global auto-hide time. */
        var timeoutSec by intPref("${cat.id}.timeout", 0)
        /** Stay open until dismissed (swipe up / tap outside). */
        var sticky by boolPref("${cat.id}.sticky", false)
        var sound by boolPref("${cat.id}.sound", true)
        var haptics by boolPref("${cat.id}.haptics", true)
        /** Skin name, empty = the global mascot. */
        var skinName by stringPref("${cat.id}.skin", "")
        // category specific
        /** Matrix-style typewriter reveal of the message text. */
        var typewriter by boolPref("${cat.id}.typewriter", true)
        /** Typewriter speed, ms per letter (long texts speed up to fit [Typewriter.MAX_MS]). */
        var typeSpeedMs by intPref("${cat.id}.typeSpeed", 40)
        var photo by boolPref("${cat.id}.photo", true)
        var preview by boolPref("${cat.id}.preview", true)
        var everyStep by boolPref("${cat.id}.everyStep", true)
    }

    val categories: Map<Category, CatPrefs> = Category.entries.associateWith { CatPrefs(it) }
    fun cat(c: Category): CatPrefs = categories.getValue(c)

    fun skinFor(c: Category): Skin =
        cat(c).skinName.let { n -> Skin.entries.firstOrNull { it.name == n } } ?: skin

    /** How long a card of this category stays: 0 = until dismissed. [fallback] = the built-in default. */
    fun hideMsFor(c: Category, fallback: Long): Long {
        val p = cat(c)
        return when {
            p.sticky -> 0L
            p.timeoutSec > 0 -> p.timeoutSec * 1000L
            else -> fallback
        }
    }

    // lock screen
    var lockScreen by boolPref("lockScreen", false)
    var lockMode by enumPref("lockMode", LockMode.OnWake)
    var lockMinutes by intPref("lockMinutes", 10)
    var lockClock by boolPref("lockClock", true)
    var lockDate by boolPref("lockDate", true)
    var lockBattery by boolPref("lockBattery", true)
    var lockBrightness by floatPref("lockBrightness", 0.6f)
    /** Display backlight while xAOD is lit (0..1). Samsung's fingerprint icon sits above xAOD, so this is what dims it. */
    var aodBacklight by floatPref("aodBacklight", 0f)
    /** Turn Android's Extra dim on (full strength) while the dim xAOD shows; see ExtraDim. */
    var aodExtraDim by boolPref("aodExtraDim", true)
    /** Crash-safe record of the user's own Extra dim state while we override it. */
    var extraDimSaved by boolPref("extraDimSaved", false)
    var extraDimPrevOn by boolPref("extraDimPrevOn", false)
    var extraDimPrevLevel by intPref("extraDimPrevLevel", -1)

    // xAOD
    var aodLayout by enumPref("aodLayout", AodLayout.Classic)
    var aodTint by enumPref("aodTint", AodTint.White)
    /** Draw xAOD as an overlay over the live lock screen (fingerprint works from it) instead of an activity. */
    var aodOverlay by boolPref("aodOverlay", true)
    var aodBreathe by boolPref("aodBreathe", true)
    var aodHint by boolPref("aodHint", true)
    /** Covered (pocket / face down): the screen goes fully black instead of staying lit. */
    var aodPocket by boolPref("aodPocket", true)
    var aodSchedule by boolPref("aodSchedule", false)
    var aodFromHour by intPref("aodFrom", 22)
    var aodToHour by intPref("aodTo", 7)
    /** Show the island pill (bolt + percentage) on the xAOD while charging. */
    var aodPill by boolPref("aodPill", true)
    /** Battery-use statistics: minutes shown on battery and the percent points lost meanwhile. */
    var aodMinutes by floatPref("aodMinutes", 0f)
    var aodDrop by floatPref("aodDrop", 0f)

    /** True when the schedule is off or the current hour falls in the [from, to) window (may cross midnight). */
    fun aodInSchedule(): Boolean {
        if (!aodSchedule) return true
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val a = aodFromHour
        val b = aodToHour
        return if (a == b) true else if (a < b) h in a until b else h >= a || h < b
    }

    val autoHideMs get() = autoHideSec * 1000L

    fun greetingText(): String {
        val name = greetName.trim()
        val suffix = if (name.isEmpty()) "!" else ", $name!"
        return when (greetStyle) {
            GreetStyle.Hi -> "Hi$suffix 👋"
            GreetStyle.Custom -> greetCustom.ifBlank { "Hi$suffix" }
            GreetStyle.TimeOfDay -> {
                val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val part = when (h) {
                    in 5..11 -> "Good morning"
                    in 12..17 -> "Good afternoon"
                    in 18..22 -> "Good evening"
                    else -> "Up late"
                }
                "$part$suffix"
            }
        }
    }

    fun resetLook() {
        skin = Skin.Classic; mascotScale = 1f; mascotGlow = true
        style = IslandStyle.Notch; pillWidth = 150f; pillHeightExtra = 0f; topOffset = 0f; sideMargin = 0f
        corner = 34f; islandHeight = 1f; panelColor = PanelColor.Ink; glow = 1f; starfield = true
        shadow = true; gridIcon = true; motion = Motion.Normal; autoHideSec = 6
    }

    // ---- pref delegates ---------------------------------------------------------------------

    private abstract class Pref<T>(val key: String) : ReadWriteProperty<Any?, T> {
        abstract fun load(p: SharedPreferences)
        abstract fun write(e: SharedPreferences.Editor, v: T)
        fun save(v: T) { sp?.edit()?.also { write(it, v) }?.apply() }
    }

    private fun floatPref(key: String, def: Float) = object : Pref<Float>(key) {
        val s = mutableFloatStateOf(def)
        override fun load(p: SharedPreferences) { s.floatValue = p.getFloat(key, def) }
        override fun write(e: SharedPreferences.Editor, v: Float) { e.putFloat(key, v) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.floatValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) { s.floatValue = value; save(value) }
    }.also { all += it }

    private fun intPref(key: String, def: Int) = object : Pref<Int>(key) {
        val s = mutableIntStateOf(def)
        override fun load(p: SharedPreferences) { s.intValue = p.getInt(key, def) }
        override fun write(e: SharedPreferences.Editor, v: Int) { e.putInt(key, v) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.intValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) { s.intValue = value; save(value) }
    }.also { all += it }

    private fun boolPref(key: String, def: Boolean) = object : Pref<Boolean>(key) {
        val s = mutableStateOf(def)
        override fun load(p: SharedPreferences) { s.value = p.getBoolean(key, def) }
        override fun write(e: SharedPreferences.Editor, v: Boolean) { e.putBoolean(key, v) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) { s.value = value; save(value) }
    }.also { all += it }

    private fun stringPref(key: String, def: String) = object : Pref<String>(key) {
        val s = mutableStateOf(def)
        override fun load(p: SharedPreferences) { s.value = p.getString(key, def) ?: def }
        override fun write(e: SharedPreferences.Editor, v: String) { e.putString(key, v) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) { s.value = value; save(value) }
    }.also { all += it }

    private inline fun <reified E : Enum<E>> enumPref(key: String, def: E) = object : Pref<E>(key) {
        val s = mutableStateOf(def)
        override fun load(p: SharedPreferences) {
            s.value = runCatching { enumValueOf<E>(p.getString(key, def.name)!!) }.getOrDefault(def)
        }
        override fun write(e: SharedPreferences.Editor, v: E) { e.putString(key, v.name) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: E) { s.value = value; save(value) }
    }.also { all += it }
}

/** Spring that follows the user's motion preset. */
fun <T> islandSpring(damping: Float, stiffness: Float, threshold: T? = null): FiniteAnimationSpec<T> {
    val m = IslandSettings.motion
    val d = if (m == Motion.Normal) damping else (damping + m.damping) / 2f
    return spring(dampingRatio = d, stiffness = stiffness * m.stiffness, visibilityThreshold = threshold)
}

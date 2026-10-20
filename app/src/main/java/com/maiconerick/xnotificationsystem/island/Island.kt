package com.maiconerick.xnotificationsystem.island

import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Every layout the island can morph into. */
enum class Kind { Hidden, Collapsed, Home, Session, Permission, Finished, Drop, Upload, Ready, Chat, RateLimit, Message, Task, Assistant }

val Kind.isExpanded get() = this != Kind.Hidden && this != Kind.Collapsed

data class ChatMsg(val fromUser: Boolean, val text: String, val id: Int)

data class IslandState(
    val kind: Kind = Kind.Collapsed,
    val step: Int = 0,
    val pushed: Boolean = false,
    val chat: List<ChatMsg> = emptyList(),
    val typing: Boolean = false,
    val draft: String = "",
    val msgApp: String = "",
    val msgTitle: String = "",
    val msgBody: String = "",
    val greeting: String = "",
    val msgChat: String = "",
    val msgAvatar: Boolean = false,
    val activityId: String = "",
    /** Voice assistant (xAssistant app): Listening / Thinking / Speaking / Done / Error + transcript. */
    val asstPhase: String = "",
    val asstUser: String = "",
    val asstReply: String = "",
    val nonce: Int = 0,
)

/** A live activity pushed by the Pi hub: a Claude task, an xBuildServer build, … */
data class Activity(
    val id: String,
    val title: String,
    val source: String = "",
    val kind: String = "task",
    val step: Int = 0,
    val total: Int = 0,
    val label: String = "",
    val detail: String = "",
    val status: String = "running",
    val progress: Float? = null,
    val steps: List<String> = emptyList(),
    val startedMs: Long = 0,
    val updatedMs: Long = 0,
    val finishedMs: Long = 0,
) {
    val running get() = status == "running"
    val failed get() = status == "failed"
    val done get() = status == "done"

    /** 0..1 for the bars: explicit progress inside the current step, else completed steps. */
    val fraction: Float
        get() = when {
            done -> 1f
            total > 0 -> ((step - 1).coerceAtLeast(0) + (progress ?: 0f)) / total
            else -> progress ?: 0f
        }.coerceIn(0f, 1f)
}

/** One step of the fake coding-agent session: verb + target. */
val SESSION_STEPS = listOf(
    "Read" to "src/billing.ts",
    "Edit" to "billing.ts",
    "Run" to "npm test",
    "Run" to "git push origin main",
)

enum class Scenario(val title: String, val subtitle: String) {
    Hello("Hello", "Island opens with the warp starfield; mascot blinks, waves and hops"),
    Session("Agent session", "Live step list 1/4 → 4/4, then asks for permission"),
    Permission("Permission request", "Amber card with Deny / Allow / Always"),
    Finished("Task finished", "Mint card, sparkles and a happy mascot"),
    Files("File drop + upload", "Mascot eats a PDF, upload bar, then \"ready\" card"),
    Chat("Chat", "Expands into a mini chat with a streamed answer"),
    Message("Incoming message", "Generic notification card with Reply / Mark as read"),
    RateLimit("Too many hits", "Dizzy mascot with a 3 second cooldown (also: poke it 5×)"),
    FullDemo("Full demo", "Hello → session → permission → finished → files → chat"),
}

/**
 * The island's brain. UI only renders [state]; every transition goes through here so the
 * overlay service, the test screen and the panel buttons all share one source of truth.
 */
object Island {
    private val _state = MutableStateFlow(IslandState())
    val state: StateFlow<IslandState> = _state.asStateFlow()
    private val current get() = _state.value

    private val scope = MainScope()
    private var flowJob: Job? = null
    private var hideJob: Job? = null
    private var hideAfterMs = 0L
    private var demo = false
    private var msgIds = 0
    private val pokes = ArrayDeque<Long>()
    private var beforeRateLimit: IslandState? = null

    private val keepPill get() = IslandSettings.keepPill
    private val autoHide get() = IslandSettings.autoHideMs

    /** Called on attention-worthy transitions (sound + haptic); installed by the service. */
    var onPing: ((Kind, Boolean, Boolean) -> Unit)? = null
    /** Opens the app's own screen; installed by the service. */
    var onOpenApp: (() -> Unit)? = null
    /** Sends a WhatsApp reply through the hub; installed by the runtime. */
    var onReply: (suspend (chat: String, text: String) -> Result<Unit>)? = null
    var onMarkRead: ((chat: String) -> Unit)? = null

    private val _activities = MutableStateFlow<Map<String, Activity>>(emptyMap())
    /** Live activities by id, oldest first. */
    val activities: StateFlow<Map<String, Activity>> = _activities.asStateFlow()
    private val dropJobs = HashMap<String, Job>()

    /** The activity the collapsed pill should show (newest running, or one that just ended). */
    fun pillActivity(map: Map<String, Activity>): Activity? =
        map.values.filter { it.running }.maxByOrNull { it.updatedMs }
            ?: map.values.filter { !it.running }.maxByOrNull { it.updatedMs }

    private fun go(s: IslandState, ping: Boolean = false, cat: Category? = null) {
        _state.value = s.copy(nonce = current.nonce + 1)
        if (ping) emitPing(s.kind, cat)
    }

    /** Sound + haptic for a card, honouring the category's own switches. */
    private fun emitPing(kind: Kind, cat: Category?) {
        val p = cat?.let { IslandSettings.cat(it) }
        onPing?.invoke(kind, p?.sound ?: true, p?.haptics ?: true)
    }

    /** Live charging state for the lock-screen pill (null = not on the lock screen / not charging). */
    private val _lockCharge = MutableStateFlow<Int?>(null)
    val lockCharge: StateFlow<Int?> = _lockCharge.asStateFlow()

    /** Lock screen: show the pill with a charging badge; null hides it again. */
    fun setLockCharge(percent: Int?) {
        val was = _lockCharge.value
        _lockCharge.value = percent
        if (percent != null && (was == null || current.kind == Kind.Hidden) && !current.kind.isExpanded) go(IslandState(Kind.Collapsed))
        if (percent == null && was != null && !current.kind.isExpanded && !IslandSettings.keepPill) {
            go(IslandState(Kind.Hidden))
        }
    }

    fun categoryOf(a: Activity) = if (a.kind == "build") Category.Builds else Category.Tasks

    private fun cancelAll() {
        flowJob?.cancel(); flowJob = null
        cancelHide()
    }

    private fun cancelHide() {
        hideJob?.cancel(); hideJob = null; hideAfterMs = 0
    }

    private fun hideLater(ms: Long) {
        cancelHide()
        if (ms <= 0) return // sticky: stays until the user dismisses it
        hideAfterMs = ms
        hideJob = scope.launch { delay(ms); collapse() }
    }

    /** Any touch on the panel restarts a pending auto-hide so it never closes under the finger. */
    fun touched() {
        if (hideAfterMs > 0) hideLater(hideAfterMs)
    }

    fun play(sc: Scenario) {
        cancelAll()
        demo = sc == Scenario.FullDemo
        when (sc) {
            Scenario.Hello -> hello()
            Scenario.Session -> startSession()
            Scenario.Permission -> go(IslandState(Kind.Permission, step = 3), ping = true)
            Scenario.Finished -> finished()
            Scenario.Files -> go(IslandState(Kind.Drop))
            Scenario.Chat -> ask()
            Scenario.Message -> message()
            Scenario.RateLimit -> rateLimit()
            Scenario.FullDemo -> {
                hello()
                cancelHide()
                flowJob = scope.launch { delay(3600); startSession() }
            }
        }
    }

    fun hello() {
        go(IslandState(Kind.Home), ping = true)
        hideLater(autoHide)
    }

    /** Tap on the collapsed pill. */
    fun open() {
        if (current.kind.isExpanded) return
        pillActivity(_activities.value)?.let { showActivity(it.id); return }
        go(IslandState(Kind.Home))
        hideLater(autoHide)
    }

    /** Mascot pops out and says hi (unlock greeting / settings preview). */
    fun greet(text: String = IslandSettings.greetingText()) {
        cancelAll()
        go(IslandState(Kind.Home, greeting = text), ping = true, cat = Category.Greeting)
        hideLater(IslandSettings.hideMsFor(Category.Greeting, maxOf(autoHide, 4500)))
    }

    // ---- toolbar tabs ----------------------------------------------------------------------

    fun goHome() {
        cancelAll()
        go(IslandState(Kind.Home))
        hideLater(autoHide)
    }

    fun openChat() {
        if (current.kind == Kind.Chat) return
        cancelAll()
        go(IslandState(Kind.Chat))
    }

    fun openDrop() {
        if (current.kind == Kind.Drop) return
        cancelAll()
        go(IslandState(Kind.Drop))
    }

    fun collapse() {
        cancelAll()
        demo = false
        go(IslandState(if (keepPill || _lockCharge.value != null) Kind.Collapsed else Kind.Hidden))
    }

    fun hideCompletely() {
        cancelAll()
        go(IslandState(Kind.Hidden))
    }

    fun outsideTap() {
        when (current.kind) {
            Kind.Permission, Kind.Drop, Kind.Upload -> Unit // these wait for an answer / finish on their own
            else -> if (current.kind.isExpanded) collapse()
        }
    }

    fun startSession() {
        cancelAll()
        flowJob = scope.launch {
            for (i in 0..3) {
                go(IslandState(Kind.Session, step = i))
                delay(if (i == 3) 1100 else 1800)
            }
            go(IslandState(Kind.Permission, step = 3), ping = true)
        }
    }

    fun allow() {
        cancelAll()
        flowJob = scope.launch {
            go(IslandState(Kind.Session, step = 3, pushed = true))
            delay(1700)
            finished()
        }
    }

    fun deny() {
        collapse()
    }

    private fun finished() {
        go(IslandState(Kind.Finished, step = 3), ping = true)
        if (demo) {
            flowJob = scope.launch { delay(3800); go(IslandState(Kind.Drop)) }
        } else hideLater(autoHide + 1000)
    }

    fun openApp() {
        onOpenApp?.invoke()
        collapse()
    }

    // ---- files -----------------------------------------------------------------------------

    fun dropDone() {
        if (current.kind == Kind.Drop) go(IslandState(Kind.Upload))
    }

    fun uploadDone() {
        if (current.kind != Kind.Upload) return
        go(IslandState(Kind.Ready), ping = true)
        if (demo) flowJob = scope.launch { delay(2600); ask() }
    }

    // ---- chat ------------------------------------------------------------------------------

    fun ask() {
        cancelAll()
        go(IslandState(Kind.Chat))
        flowJob = scope.launch {
            delay(800)
            val q = "What's the total?"
            for (i in 1..q.length) {
                _state.value = current.copy(draft = q.take(i))
                delay(45)
            }
            delay(350)
            send()
        }
    }

    fun setDraft(text: String) {
        _state.value = current.copy(draft = text)
    }

    fun send() {
        val text = current.draft.trim()
        if (text.isEmpty() || current.typing) return
        flowJob?.cancel()
        val userTurns = current.chat.count { it.fromUser }
        val chat = (current.chat + ChatMsg(true, text, ++msgIds)).takeLast(4)
        _state.value = current.copy(chat = chat, draft = "", typing = true)
        val target = current.msgChat
        val sender = onReply
        if (target.isNotEmpty() && sender != null) {
            flowJob = scope.launch {
                val r = sender(target, text)
                val note = r.fold({ "Sent ✓" }, { "Couldn't send: ${it.message}" })
                _state.value = current.copy(typing = false, chat = (current.chat + ChatMsg(false, note, ++msgIds)).takeLast(4))
                if (r.isSuccess) hideLater(2500)
            }
            return
        }
        flowJob = scope.launch {
            delay(1300)
            val reply = when {
                current.msgApp.isNotEmpty() -> "Sent! I'll let you know when they answer."
                userTurns == 0 -> "The total is €1,240 excl. VAT (€1,488 incl. VAT) for Atelier Brun — valid until October 30."
                else -> "I'm only a demo for now — soon I'll read your real notifications too."
            }
            _state.value = current.copy(
                typing = false,
                chat = (current.chat + ChatMsg(false, reply, ++msgIds)).takeLast(4),
            )
            emitPing(Kind.Chat, null)
        }
    }

    // ---- misc ------------------------------------------------------------------------------

    fun message() {
        go(
            IslandState(
                Kind.Message,
                msgApp = "WhatsApp",
                msgTitle = "Mom",
                msgBody = "Are you coming for dinner on Sunday? I'm making lasagna 🍝",
            ),
            ping = true, cat = Category.Messages,
        )
        val hide = IslandSettings.hideMsFor(Category.Messages, autoHide + 2000)
        hideLater(if (hide > 0) hide + Typewriter.durationMs(current.msgBody) else 0L)
    }

    fun reply() {
        cancelAll()
        val s = current
        go(
            IslandState(
                Kind.Chat,
                msgApp = s.msgApp,
                msgChat = s.msgChat,
                msgTitle = s.msgTitle,
                chat = listOf(ChatMsg(false, "${s.msgTitle}: ${s.msgBody}", ++msgIds)),
            ),
        )
    }

    /** Mascot was tapped — five quick pokes make it dizzy. */
    fun poke() {
        val now = System.currentTimeMillis()
        pokes.addLast(now)
        while (pokes.isNotEmpty() && now - pokes.first() > 2500) pokes.removeFirst()
        touched()
        if (pokes.size >= 5 && current.kind != Kind.RateLimit) {
            pokes.clear()
            rateLimit()
        }
    }

    fun rateLimit() {
        val prev = current
        cancelAll()
        beforeRateLimit = prev.takeIf {
            it.kind in setOf(Kind.Home, Kind.Finished, Kind.Permission, Kind.Ready, Kind.Message)
        }
        go(IslandState(Kind.RateLimit), ping = true)
        flowJob = scope.launch {
            delay(3300)
            val back = beforeRateLimit
            beforeRateLimit = null
            if (back != null) {
                go(back)
                if (back.kind != Kind.Permission) hideLater(autoHide)
            } else collapse()
        }
    }

    fun markRead() {
        val chat = current.msgChat
        if (chat.isNotEmpty()) onMarkRead?.invoke(chat)
        collapse()
    }

    // ---- hub events ------------------------------------------------------------------------

    /** A WhatsApp (or other) message from the hub. */
    fun onMessage(app: String, title: String, text: String, chat: String, hasAvatar: Boolean) {
        val k = current.kind
        // never yank the user out of something they're doing
        if (k == Kind.Chat && current.draft.isNotEmpty() || k == Kind.Permission) return
        if (k == Kind.Chat && current.msgChat == chat && chat.isNotEmpty()) {
            _state.value = current.copy(chat = (current.chat + ChatMsg(false, text, ++msgIds)).takeLast(4))
            emitPing(Kind.Chat, null)
            return
        }
        cancelAll()
        go(
            IslandState(Kind.Message, msgApp = app, msgTitle = title, msgBody = text, msgChat = chat, msgAvatar = hasAvatar),
            ping = true, cat = Category.Messages,
        )
        // the timeout starts once the typewriter has finished writing the text
        val hide = IslandSettings.hideMsFor(Category.Messages, autoHide + 2000)
        hideLater(if (hide > 0) hide + Typewriter.durationMs(text) else 0L)
    }

    fun onChatRead(chat: String) {
        if (chat.isNotEmpty() && current.kind == Kind.Message && current.msgChat == chat) collapse()
    }

    /** xAssistant state broadcast. Live phases keep the card open; the end lingers a moment, then folds. */
    fun onAssistant(phase: String, user: String, reply: String, error: String) {
        if (!IslandSettings.cat(Category.Assistant).enabled) return
        val live = phase == "Listening" || phase == "Thinking" || phase == "Speaking"
        val showing = current.kind == Kind.Assistant
        if (!live && !showing) return
        when {
            live -> {
                cancelAll()
                if (showing) _state.value = current.copy(asstPhase = phase, asstUser = user, asstReply = reply)
                else go(IslandState(Kind.Assistant, asstPhase = phase, asstUser = user, asstReply = reply))
            }
            phase == "Error" -> {
                _state.value = current.copy(asstPhase = "Error", asstReply = error.ifBlank { "Something went wrong" })
                hideLater(5000)
            }
            else -> {
                _state.value = current.copy(asstPhase = "Done", asstUser = user.ifBlank { current.asstUser },
                    asstReply = reply.ifBlank { current.asstReply })
                hideLater(IslandSettings.hideMsFor(Category.Assistant, 3500))
            }
        }
    }

    fun resetActivities() {
        _activities.value = emptyMap()
    }

    /**
     * New/changed activity. Step and status changes pop the card open for a moment; detail-only
     * updates just refresh it (and the pill). [quiet] = replayed on reconnect, never pops.
     */
    fun onActivity(a: Activity, quiet: Boolean) {
        val before = _activities.value[a.id]
        _activities.value = LinkedHashMap(_activities.value).apply { put(a.id, a) }
        dropJobs.remove(a.id)?.cancel()
        if (!a.running) {
            // finished ones leave the pill after a while
            dropJobs[a.id] = scope.launch { delay(45_000); dropActivity(a.id) }
        }
        // replayed on reconnect: only something that *just* finished is worth popping
        // (e.g. "Installed ✓" for this very app, which restarts us mid-build)
        val justEnded = !a.running && System.currentTimeMillis() - a.updatedMs < 30_000
        if (quiet && !justEnded) return
        val everyStep = IslandSettings.cat(categoryOf(a)).everyStep
        // Claude tasks: only pop open on completion/failure, not on every intermediate step.
        // Builds keep the old behaviour (everyStep still matters for them).
        val isClaudeTask = a.kind == "task" && (a.source.equals("Claude", ignoreCase = true) || a.source.isEmpty())
        // Claude tasks: skip intermediate step changes, but always show first appearance and done/failed.
        val statusChanged = before == null || before.status != a.status
        val stepChanged = everyStep && before?.step != a.step
        val changed = if (isClaudeTask) statusChanged else statusChanged || stepChanged
        val showing = current.kind == Kind.Task && current.activityId == a.id
        if (!changed) {
            if (showing) touched()
            return
        }
        val busy = current.kind in setOf(Kind.Permission, Kind.Chat, Kind.Drop, Kind.Upload) ||
            (current.kind == Kind.Message && hideJob != null)
        if (busy && a.running) return
        showActivity(a.id, ping = true)
    }

    fun showActivity(id: String, ping: Boolean = false) {
        val a = _activities.value[id] ?: return
        cancelAll()
        val same = current.kind == Kind.Task && current.activityId == id
        if (same) _state.value = current.copy(activityId = id) else go(IslandState(Kind.Task, activityId = id))
        if (ping) emitPing(
            when {
                a.done -> Kind.Finished
                a.failed -> Kind.Permission
                else -> Kind.Chat
            },
            categoryOf(a),
        )
        hideLater(IslandSettings.hideMsFor(categoryOf(a), if (a.running) autoHide else autoHide + 2000))
    }

    fun dropActivity(id: String) {
        dropJobs.remove(id)?.cancel()
        if (id !in _activities.value) return
        _activities.value = LinkedHashMap(_activities.value).apply { remove(id) }
        if (current.kind == Kind.Task && current.activityId == id) collapse()
    }

    fun reset() {
        cancelAll()
        _state.value = IslandState(Kind.Collapsed)
    }
}

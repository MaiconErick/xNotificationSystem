package com.maiconerick.xnotificationsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.maiconerick.xnotificationsystem.island.Activity
import com.maiconerick.xnotificationsystem.island.Category
import com.maiconerick.xnotificationsystem.island.Island
import com.maiconerick.xnotificationsystem.island.IslandSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Long-polls the island hub on the Pi (~/MaiconErick/IslandHub, :8773) and turns its events into
 * island cards: live activities (Claude tasks, xBuildServer builds) and xWapp WhatsApp messages.
 */
object HubClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var boot = ""
    private var seq = 0L
    private var routeIndex = 0
    private val avatars = ConcurrentHashMap<String, Bitmap>()

    /** "online" / "offline" / "off" — shown in settings. */
    var status by mutableStateOf("off")
        private set
    var route by mutableStateOf("")
        private set

    private fun bases(): List<String> {
        val custom = IslandSettings.hubUrl.trim().removeSuffix("/")
        val first = if (custom.startsWith("http")) custom else "http://$custom"
        return listOf(first, "http://100.64.0.128:8773").distinct()
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        status = "off"
    }

    fun restart() {
        stop()
        boot = ""; seq = 0
        start()
    }

    private suspend fun loop() {
        var failures = 0
        while (scope.isActive) {
            if (!IslandSettings.hubEnabled) {
                status = "off"; delay(3000); continue
            }
            val base = bases()[routeIndex % bases().size]
            try {
                val url = "$base/events?since=$seq&boot=$boot&wait=25"
                val body = get(url, readTimeout = 35_000) ?: throw IllegalStateException("no response")
                val json = JSONObject(body)
                val reset = json.optBoolean("reset")
                val events = json.optJSONArray("events") ?: JSONArray()
                boot = json.optString("boot")
                seq = json.optLong("seq")
                status = "online"; route = base; failures = 0
                withContext(Dispatchers.Main) {
                    if (reset) Island.resetActivities()
                    for (i in 0 until events.length()) handle(events.getJSONObject(i), quiet = reset)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                failures++
                status = "offline"
                if (failures % 2 == 0) routeIndex++ // two misses in a row: try the other route
                delay(if (failures < 3) 1500L else 8_000L)
            }
        }
    }

    private fun handle(e: JSONObject, quiet: Boolean) {
        when (e.optString("type")) {
            "activity" -> e.optJSONObject("activity")?.let { a ->
                val act = parseActivity(a)
                if (IslandSettings.cat(Island.categoryOf(act)).enabled) Island.onActivity(act, quiet)
            }
            "dismiss" -> Island.dropActivity(e.optString("id"))
            // "typing" events (someone composing) are ignored on purpose: only real messages pop up.
            // the hub only replays messages from the last few seconds, so show them even on reset
            "message" -> if (IslandSettings.cat(Category.Messages).enabled) {
                val chat = e.optString("chat")
                Island.onMessage(
                    app = e.optString("app", "WhatsApp"),
                    title = e.optString("title"),
                    text = e.optString("text"),
                    chat = chat,
                    hasAvatar = e.optBoolean("avatar"),
                )
            }
            "read" -> Island.onChatRead(e.optString("chat"))
        }
    }

    private fun parseActivity(a: JSONObject): Activity {
        val steps = a.optJSONArray("steps")?.let { arr -> List(arr.length()) { arr.optString(it) } } ?: emptyList()
        return Activity(
            id = a.optString("id"),
            title = a.optString("title"),
            source = a.optString("source"),
            kind = a.optString("kind", "task"),
            step = a.optInt("step", 0),
            total = a.optInt("total", 0),
            label = a.optString("label"),
            detail = a.optString("detail"),
            status = a.optString("status", "running"),
            progress = if (a.has("progress") && !a.isNull("progress")) a.optDouble("progress").toFloat() else null,
            steps = steps,
            startedMs = (a.optDouble("started", 0.0) * 1000).toLong(),
            updatedMs = (a.optDouble("updated", 0.0) * 1000).toLong(),
            finishedMs = if (a.has("finished")) (a.optDouble("finished") * 1000).toLong() else 0L,
        )
    }

    // ---- calls used by the cards -----------------------------------------------------------

    suspend fun reply(chat: String, text: String): Result<Unit> = post("/reply", JSONObject().put("chat", chat).put("text", text))

    suspend fun markRead(chat: String): Result<Unit> = post("/read", JSONObject().put("chat", chat))

    suspend fun avatar(chat: String): Bitmap? {
        avatars[chat]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val c = URL("${route.ifEmpty { bases().first() }}/avatar?chat=${java.net.URLEncoder.encode(chat, "UTF-8")}")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 8000
                try {
                    if (c.responseCode != 200) null
                    else BitmapFactory.decodeStream(c.inputStream)?.also { avatars[chat] = it }
                } finally { c.disconnect() }
            }.getOrNull()
        }
    }

    private suspend fun post(path: String, body: JSONObject): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("${route.ifEmpty { bases().first() }}$path").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 4000; c.readTimeout = 20_000
            c.setRequestProperty("Content-Type", "application/json")
            try {
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: ""
                if (code >= 400) error(JSONObject(text.ifEmpty { "{}" }).optString("error", "HTTP $code"))
            } finally { c.disconnect() }
        }
    }

    private fun get(url: String, readTimeout: Int): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000
        c.readTimeout = readTimeout
        return try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally { c.disconnect() }
    }
}

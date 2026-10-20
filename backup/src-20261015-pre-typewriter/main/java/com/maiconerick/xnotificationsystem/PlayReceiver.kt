package com.maiconerick.xnotificationsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.maiconerick.xnotificationsystem.island.Island
import com.maiconerick.xnotificationsystem.island.Scenario

/**
 * Lets adb / Tasker fire a test card:
 * am broadcast -a com.maiconerick.xnotificationsystem.PLAY --es scenario Permission --el delay 2000
 * Extra scenarios: Greet (mascot says hi, optional --es text "...") and Lock (lock screen preview).
 */
class PlayReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val name = intent.getStringExtra("scenario") ?: "Hello"
        val delay = intent.getLongExtra("delay", 0)
        val main = Handler(Looper.getMainLooper())
        when (name) {
            "Greet" -> {
                val text = intent.getStringExtra("text")
                main.postDelayed({ if (text.isNullOrBlank()) Island.greet() else Island.greet(text) }, delay)
            }
            "ExportIcons" -> exportIcons(ctx)
            "Lock" -> main.postDelayed({ LockActivity.start(ctx.applicationContext, alwaysOn = false, preview = true) }, delay)
            else -> {
                val sc = runCatching { Scenario.valueOf(name) }.getOrNull() ?: return
                IslandRuntime.play(ctx, sc, delay)
            }
        }
    }

    /**
     * Dev helper (adb): renders every launcher icon on the phone (adaptive ones included) to
     * /sdcard/Android/data/<pkg>/files/icons/<package>.png + index.json {package: label}, so the
     * Pi's xBugTracker can show the real app icons.
     */
    private fun exportIcons(ctx: Context) {
        val pm = ctx.packageManager
        val dir = java.io.File(ctx.getExternalFilesDir(null), "icons").apply { mkdirs() }
        val index = org.json.JSONObject()
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        for (ri in pm.queryIntentActivities(launch, 0)) {
            val pkg = ri.activityInfo.packageName
            runCatching {
                val d = ri.loadIcon(pm)
                val bmp = android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 192, 192)
                d.draw(android.graphics.Canvas(bmp))
                java.io.File(dir, "$pkg.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                index.put(pkg, ri.loadLabel(pm).toString())
            }
        }
        java.io.File(dir, "index.json").writeText(index.toString())
    }
}

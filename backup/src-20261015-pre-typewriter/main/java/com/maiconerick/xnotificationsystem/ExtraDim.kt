package com.maiconerick.xnotificationsystem

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.maiconerick.xnotificationsystem.island.IslandSettings

/**
 * Android's "Extra dim" (reduce bright colours) dims the whole display below the panel's minimum
 * backlight, Samsung's fingerprint icon included, which xAOD itself can't draw over. Needs
 * WRITE_SECURE_SETTINGS, granted once over adb:
 *   adb shell pm grant com.maiconerick.xnotificationsystem android.permission.WRITE_SECURE_SETTINGS
 * The user's own Extra dim state is saved first and put back as soon as xAOD goes away.
 */
object ExtraDim {
    private const val ACTIVATED = "reduce_bright_colors_activated"
    private const val LEVEL = "reduce_bright_colors_level"

    fun available(c: Context) =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun apply(c: Context) {
        Log.d(TAG, "apply enabled=${IslandSettings.aodExtraDim} granted=${available(c)} saved=${IslandSettings.extraDimSaved}")
        if (!IslandSettings.aodExtraDim || !available(c) || IslandSettings.extraDimSaved) return
        val r = c.contentResolver
        // hidden secure keys may not be readable by apps; then assume the stock state (off, default level)
        IslandSettings.extraDimPrevOn = read { Settings.Secure.getInt(r, ACTIVATED, 0) == 1 } ?: false
        IslandSettings.extraDimPrevLevel = read { Settings.Secure.getInt(r, LEVEL, -1) } ?: -1
        IslandSettings.extraDimSaved = true
        runCatching {
            Settings.Secure.putInt(r, LEVEL, 100)
            Settings.Secure.putInt(r, ACTIVATED, 1)
        }.onFailure { Log.w(TAG, "apply failed", it) }
    }

    private fun <T> read(block: () -> T): T? = runCatching(block).onFailure { Log.w(TAG, "read failed", it) }.getOrNull()

    private const val TAG = "xAOD-ExtraDim"

    /** Puts the user's Extra dim back; also called when the service starts in case we died while dimmed. */
    fun restore(c: Context) {
        if (!IslandSettings.extraDimSaved) return
        val r = c.contentResolver
        runCatching {
            Settings.Secure.putInt(r, ACTIVATED, if (IslandSettings.extraDimPrevOn) 1 else 0)
            val lvl = IslandSettings.extraDimPrevLevel
            if (lvl >= 0) Settings.Secure.putInt(r, LEVEL, lvl) else Settings.Secure.putString(r, LEVEL, null)
        }.onFailure { Log.w(TAG, "restore failed", it) }
        IslandSettings.extraDimSaved = false
    }
}

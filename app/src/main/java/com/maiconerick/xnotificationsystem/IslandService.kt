package com.maiconerick.xnotificationsystem

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.ServiceCompat
import com.maiconerick.xnotificationsystem.island.Scenario

/**
 * Fallback host (plain app overlay, drawn below the status bar) for when the accessibility
 * service is off. Runs in the foreground so delayed tests survive leaving the app.
 */
class IslandService : Service() {

    companion object {
        private const val ACTION_PLAY = "play"
        private const val ACTION_STOP = "stop"
        private const val ACTION_START = "start"
        private const val EXTRA_SCENARIO = "scenario"
        private const val EXTRA_DELAY = "delay"
        private const val CHANNEL = "island"

        var running = false
            private set

        fun play(ctx: Context, scenario: Scenario, delayMs: Long) {
            ctx.startForegroundService(
                Intent(ctx, IslandService::class.java)
                    .setAction(ACTION_PLAY)
                    .putExtra(EXTRA_SCENARIO, scenario.name)
                    .putExtra(EXTRA_DELAY, delayMs)
            )
        }

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, IslandService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, IslandService::class.java))
        }
    }

    private var host: OverlayHost? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sc = runCatching { Scenario.valueOf(intent?.getStringExtra(EXTRA_SCENARIO) ?: "") }.getOrNull()
        val delay = intent?.getLongExtra(EXTRA_DELAY, 0) ?: 0
        if (intent?.action == ACTION_STOP || !Settings.canDrawOverlays(this) ||
            IslandAccessibilityService.instance != null
        ) {
            // the accessibility host took over — hand the request to it
            if (sc != null && IslandAccessibilityService.instance != null) IslandRuntime.schedule(sc, delay)
            stopSelf()
            return START_NOT_STICKY
        }
        if (host == null) {
            IslandRuntime.install(this)
            host = OverlayHost(this).also { it.attach() }
        }
        if (intent?.action == ACTION_PLAY && sc != null) IslandRuntime.schedule(sc, delay)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        if (host != null) {
            host?.detach()
            host = null
            if (IslandAccessibilityService.instance == null) IslandRuntime.uninstall()
        }
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Island overlay", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, IslandService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Island is running")
            .setContentText("xNotificationSystem overlay (fallback mode)")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, 42, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }
}

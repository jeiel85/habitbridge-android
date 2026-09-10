package com.habitbridge.detect

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.habitbridge.HabitBridgeApp
import com.habitbridge.R
import com.habitbridge.ui.MainActivity
import com.habitbridge.util.Permissions

/**
 * Primary detector (PRD §6.1 path A): polls UsageStatsManager once per second
 * while the screen is on and forwards foreground changes to the coordinator.
 * Runs as a foreground service of type "specialUse".
 */
class DetectionService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var usm: UsageStatsManager
  private var lastQuery = 0L
  private var screenOn = true
  private var running = false

  private val screenReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      when (intent.action) {
        Intent.ACTION_SCREEN_OFF -> { screenOn = false; handler.removeCallbacks(tick) }
        Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> { if (!screenOn) { screenOn = true; lastQuery = System.currentTimeMillis() - 2_000; schedule() } }
      }
    }
  }

  private val tick = object : Runnable {
    override fun run() {
      if (!running) return
      pollOnce()
      if (screenOn) handler.postDelayed(this, POLL_MS)
    }
  }

  override fun onCreate() {
    super.onCreate()
    usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    screenOn = pm.isInteractive
    val filter = IntentFilter().apply {
      addAction(Intent.ACTION_SCREEN_ON)
      addAction(Intent.ACTION_SCREEN_OFF)
      addAction(Intent.ACTION_USER_PRESENT)
    }
    ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    startInForeground()
    if (!Permissions.hasUsageAccess(this)) {
      Log.w(TAG, "usage access missing; service idles until granted")
    }
    running = true
    lastQuery = System.currentTimeMillis() - 5_000
    schedule()
    return START_STICKY
  }

  private fun schedule() {
    handler.removeCallbacks(tick)
    handler.post(tick)
  }

  private fun pollOnce() {
    if (!Permissions.hasUsageAccess(this)) return
    val now = System.currentTimeMillis()
    val begin = if (lastQuery == 0L) now - 5_000 else lastQuery
    lastQuery = now
    var latest: String? = null
    try {
      val events = usm.queryEvents(begin, now)
      val ev = UsageEvents.Event()
      while (events.hasNextEvent()) {
        events.getNextEvent(ev)
        if (isForegroundEvent(ev.eventType)) latest = ev.packageName
      }
    } catch (t: Throwable) {
      Log.w(TAG, "queryEvents failed", t)
    }
    val coordinator = HabitBridgeApp.of(this).coordinator
    if (latest != null) coordinator.onForeground(latest, "usage")
    coordinator.tickUsage(coordinator.currentForeground())
  }

  @Suppress("DEPRECATION")
  private fun isForegroundEvent(type: Int): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) type == UsageEvents.Event.ACTIVITY_RESUMED
    else type == UsageEvents.Event.MOVE_TO_FOREGROUND

  private fun startInForeground() {
    val open = PendingIntent.getActivity(
      this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notification: Notification = NotificationCompat.Builder(this, HabitBridgeApp.CHANNEL_DETECTION)
      .setSmallIcon(R.drawable.ic_stat_bridge)
      .setContentTitle(getString(R.string.notif_title))
      .setContentText(getString(R.string.notif_text))
      .setContentIntent(open)
      .setOngoing(true)
      .setSilent(true)
      .setPriority(NotificationCompat.PRIORITY_MIN)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .build()
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
  }

  override fun onDestroy() {
    running = false
    handler.removeCallbacks(tick)
    runCatching { unregisterReceiver(screenReceiver) }
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    private const val TAG = "HabitBridge/Detect"
    private const val POLL_MS = 1_000L
    const val NOTIFICATION_ID = 1001
    const val ACTION_STOP = "com.habitbridge.action.STOP"

    fun start(context: Context) {
      val intent = Intent(context, DetectionService::class.java)
      ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
      context.startService(Intent(context, DetectionService::class.java).setAction(ACTION_STOP))
    }

    fun isRunning(context: Context): Boolean = Permissions.isServiceRunning(context, DetectionService::class.java)
  }
}

package com.habitbridge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.habitbridge.data.AppDatabase
import com.habitbridge.data.HabitRepository
import com.habitbridge.detect.InterceptCoordinator
import com.habitbridge.overlay.OverlayController
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container. Small app, no DI framework (DECISIONS: minimal architecture). */
class HabitBridgeApp : Application() {
  private val crashGuard = CoroutineExceptionHandler { _, t -> Log.e("HabitBridge/App", "background failure", t) }
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + crashGuard)
  lateinit var repository: HabitRepository
    private set
  lateinit var overlay: OverlayController
    private set
  lateinit var coordinator: InterceptCoordinator
    private set

  override fun onCreate() {
    super.onCreate()
    repository = HabitRepository(AppDatabase.get(this))
    overlay = OverlayController(this, repository, appScope)
    coordinator = InterceptCoordinator(this, repository, overlay, appScope)
    createChannels()
  }

  private fun createChannels() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(CHANNEL_DETECTION, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN).apply {
      setShowBadge(false)
      description = getString(R.string.notif_text)
    }
    nm.createNotificationChannel(channel)
  }

  companion object {
    const val CHANNEL_DETECTION = "detection"
    fun of(context: Context): HabitBridgeApp = context.applicationContext as HabitBridgeApp
  }
}

package com.habitbridge.detect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.habitbridge.data.HabitRepository
import com.habitbridge.overlay.OverlayController
import com.habitbridge.policy.Decision
import com.habitbridge.policy.Signals
import com.habitbridge.util.CalendarSignal
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Receives foreground-app events from either detector (UsageStats poller or the
 * optional accessibility service), de-duplicates them, asks the repository for a
 * decision and drives the overlay. Also accumulates usage time for targets.
 */
class InterceptCoordinator(
  private val context: Context,
  private val repo: HabitRepository,
  private val overlay: OverlayController,
  private val scope: CoroutineScope,
) {
  private val selfPackage = context.packageName
  private val ignored = setOf("com.android.systemui", "android", "com.google.android.permissioncontroller", "com.android.permissioncontroller")
  private val lastForeground = AtomicReference<String?>(null)
  private val mutex = Mutex()

  // usage accounting
  private var usagePkg: String? = null
  private var usageSeconds = 0
  private var lastFlush = System.currentTimeMillis()

  fun currentForeground(): String? = lastForeground.get()

  fun onForeground(pkg: String?, source: String) {
    if (pkg.isNullOrBlank()) return
    if (pkg in ignored) return
    val previous = lastForeground.getAndSet(pkg)
    if (previous == pkg) return
    Log.d(TAG, "foreground=$pkg via $source")
    scope.launch { handleForeground(pkg) }
  }

  private suspend fun handleForeground(pkg: String) {
    mutex.withLock {
      flushUsage(force = true)
      if (overlay.isShowing) {
        if (overlay.currentPackage == pkg) return@withLock
        // The user left the covered app (home/recents/another app, or HabitBridge itself).
        overlay.dismissAsHome()
      }
      if (pkg == selfPackage) return@withLock
      if (pkg == defaultHomePackage()) { Log.d(TAG, "ignore launcher $pkg"); return@withLock }
      val now = System.currentTimeMillis()
      val settings = repo.settingsEntity()
      val signals = Signals(calendarBusy = settings.calendarEnabled && CalendarSignal.isBusyNow(context, now))
      when (val d = repo.decide(pkg, now, signals)) {
        is Decision.Intercept -> { Log.d(TAG, "intercept $pkg ${d.mode}/${d.shield}"); overlay.show(pkg, d, now) }
        is Decision.Allow -> Log.d(TAG, "allow $pkg: ${d.reason}")
      }
    }
  }

  /** Called once per second by the poller while the screen is on. */
  fun tickUsage(foregroundPkg: String?, seconds: Int = 1) {
    scope.launch {
      mutex.withLock {
        if (foregroundPkg == null || overlay.isShowing) return@withLock
        if (usagePkg != foregroundPkg) {
          flushUsage(force = true)
          usagePkg = if (repo.isTarget(foregroundPkg)) foregroundPkg else null
        }
        if (usagePkg != null) usageSeconds += seconds
        flushUsage(force = false)
      }
    }
  }

  private suspend fun flushUsage(force: Boolean) {
    val now = System.currentTimeMillis()
    if (usageSeconds > 0 && (force || now - lastFlush > 30_000)) {
      usagePkg?.let { repo.addUsageSeconds(it, usageSeconds, now) }
      usageSeconds = 0
      lastFlush = now
    }
    if (force) usagePkg = null
  }

  /**
   * Only the *default* home app counts as the launcher. Querying every HOME-capable
   * activity would also match com.android.settings (its boot-time FallbackHome).
   */
  private fun defaultHomePackage(): String? {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
  }

  companion object {
    private const val TAG = "HabitBridge/Coord"
  }
}

/** Off-main helper for callers that only have a Context. */
fun Context.coordinator(): InterceptCoordinator = com.habitbridge.HabitBridgeApp.of(this).coordinator

@Suppress("unused")
private val ioDispatcher = Dispatchers.IO

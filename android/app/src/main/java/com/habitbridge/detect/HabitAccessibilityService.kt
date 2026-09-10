package com.habitbridge.detect

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import com.habitbridge.HabitBridgeApp

/**
 * Optional detector (PRD §6.1 path B). Only window-state changes are observed and
 * no window content is retrieved (see accessibility_service_config.xml). Its
 * second job is [goHome], the cleanest way to leave an app from an overlay.
 */
class HabitAccessibilityService : AccessibilityService() {

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    val pkg = event.packageName?.toString() ?: return
    val cls = event.className?.toString() ?: return
    // Only activities count as "the app came to the front" (skips IME popups, dialogs, toasts).
    if (!isActivity(pkg, cls)) return
    HabitBridgeApp.of(this).coordinator.onForeground(pkg, "a11y")
  }

  private fun isActivity(pkg: String, cls: String): Boolean = try {
    packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
    true
  } catch (_: PackageManager.NameNotFoundException) {
    false
  }

  override fun onInterrupt() = Unit

  override fun onUnbind(intent: android.content.Intent?): Boolean {
    instance = null
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    instance = null
    super.onDestroy()
  }

  companion object {
    @Volatile var instance: HabitAccessibilityService? = null
      private set

    /** Returns true when the global HOME action was dispatched through the accessibility service. */
    fun goHome(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
  }
}

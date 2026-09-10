package com.habitbridge.detect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.habitbridge.util.Permissions

/** Restarts detection after reboot or app update, but only if the user finished onboarding. */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
    if (Permissions.hasUsageAccess(context) && Permissions.canDrawOverlays(context)) {
      DetectionService.start(context)
    }
  }
}

package com.habitbridge.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class InstalledApp(val packageName: String, val label: String)

object InstalledApps {
  /** Launchable apps other than ourselves, sorted by label. Requires the <queries> block in the manifest on API 30+. */
  fun launchable(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
      .asSequence()
      .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
      .filter { it.first != context.packageName }
      .distinctBy { it.first }
      .map { InstalledApp(it.first, it.second) }
      .sortedBy { it.label.lowercase() }
      .toList()
  }

  fun label(context: Context, pkg: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
  } catch (_: PackageManager.NameNotFoundException) {
    pkg
  }

  fun icon(context: Context, pkg: String): Drawable? = try {
    context.packageManager.getApplicationIcon(pkg)
  } catch (_: PackageManager.NameNotFoundException) {
    null
  }
}

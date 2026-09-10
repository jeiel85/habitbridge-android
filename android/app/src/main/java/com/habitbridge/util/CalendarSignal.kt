package com.habitbridge.util

import android.content.Context
import android.provider.CalendarContract
import android.util.Log

/** Reads "am I in a busy event right now?" from the device calendar. Cached for 60 s. Never throws. */
object CalendarSignal {
  private var cachedAt = 0L
  private var cached = false

  fun isBusyNow(context: Context, now: Long): Boolean {
    if (!Permissions.hasCalendar(context)) return false
    if (now - cachedAt < 60_000) return cached
    cached = query(context, now)
    cachedAt = now
    return cached
  }

  private fun query(context: Context, now: Long): Boolean = try {
    val projection = arrayOf(CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.SELF_ATTENDEE_STATUS)
    CalendarContract.Instances.query(context.contentResolver, projection, now, now)?.use { c ->
      var busy = false
      while (c.moveToNext()) {
        val availability = c.getInt(0)
        val allDay = c.getInt(1) == 1
        val status = c.getInt(2)
        val declined = status == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
        if (!allDay && !declined && availability == CalendarContract.Events.AVAILABILITY_BUSY) busy = true
      }
      busy
    } ?: false
  } catch (t: Throwable) {
    Log.w("HabitBridge/Calendar", "calendar query failed", t)
    false
  }
}

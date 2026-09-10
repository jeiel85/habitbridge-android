package com.habitbridge.policy

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mirrors tests/engine.test.js of the web prototype so both engines agree. */
class PolicyTest {
  private val zone: ZoneId = ZoneId.of("Asia/Seoul")
  private val settings = PolicySettings()
  private val ig = "com.instagram.android"
  private val yt = "com.google.android.youtube"
  private val minute = 60_000L

  private fun at(y: Int, m: Int, d: Int, hh: Int, mm: Int = 0): Long =
    LocalDateTime.of(y, m, d, hh, mm).atZone(zone).toInstant().toEpochMilli()

  private val targets = listOf(
    TargetRule(ig, true, ShieldSetting.AUTO, 30),
    TargetRule(yt, true, ShieldSetting.AUTO, 45),
    TargetRule("com.zhiliaoapp.musically", true, ShieldSetting.AUTO, 20),
    TargetRule("com.twitter.android", false, ShieldSetting.AUTO, 0),
  )

  private fun target(pkg: String) = targets.firstOrNull { it.packageName == pkg }

  private var nextId = 1L
  private fun log(pkg: String, action: ActionTaken, at: Long, swap: SwapType? = null, emotion: Emotion? = null) =
    InterceptLog(nextId++, pkg, at, action, swap, emotion)

  @Test fun `time windows wrap midnight`() {
    assertTrue(Policy.inWindow(23 * 60 + 30, "23:00", "07:00"))
    assertTrue(Policy.inWindow(2 * 60, "23:00", "07:00"))
    assertEquals(false, Policy.inWindow(12 * 60, "23:00", "07:00"))
    assertTrue(Policy.inWindow(9 * 60, "09:00", "18:00"))
    assertEquals(false, Policy.inWindow(18 * 60, "09:00", "18:00"))
    assertEquals(false, Policy.inWindow(10 * 60, "10:00", "10:00"))
  }

  @Test fun `context priority sleep over meeting over work over free`() {
    val tue = { hh: Int, mm: Int -> at(2026, 9, 8, hh, mm) } // Tuesday
    assertEquals(ContextMode.SLEEP, Policy.evaluateContext(settings, tue(23, 30), zone).mode)
    assertEquals(ContextMode.SLEEP, Policy.evaluateContext(settings, tue(23, 30), zone, Signals(calendarBusy = true)).mode)
    assertEquals(ContextMode.MEETING, Policy.evaluateContext(settings, tue(10, 0), zone, Signals(calendarBusy = true)).mode)
    assertEquals(ContextResult(ContextMode.WORK, ShieldLevel.NORMAL), Policy.evaluateContext(settings, tue(10, 0), zone))
    assertEquals(ContextResult(ContextMode.FREE, ShieldLevel.SOFT), Policy.evaluateContext(settings, tue(20, 0), zone))
    assertEquals(ContextMode.FREE, Policy.evaluateContext(settings, at(2026, 9, 12, 10), zone).mode) // Saturday
  }

  @Test fun `allowlist and non-targets are never intercepted`() {
    val now = at(2026, 9, 8, 10)
    assertEquals(Decision.Allow(AllowReason.ALLOWLIST), Policy.decide(settings, target(ig), true, emptyList(), 0, now, zone))
    assertEquals(Decision.Allow(AllowReason.NOT_TARGET), Policy.decide(settings, null, false, emptyList(), 0, now, zone))
    assertEquals(Decision.Allow(AllowReason.NOT_TARGET), Policy.decide(settings, target("com.twitter.android"), false, emptyList(), 0, now, zone))
  }

  @Test fun `AUTO follows context and fixed shields override it`() {
    val work = at(2026, 9, 8, 10)
    val d1 = Policy.decide(settings, target(ig), false, emptyList(), 0, work, zone) as Decision.Intercept
    assertEquals(ShieldLevel.NORMAL, d1.shield)
    assertEquals(5, d1.holdSeconds)
    assertTrue(d1.canBypass)
    val soft = Policy.decide(settings, target(ig)!!.copy(shield = ShieldSetting.SOFT), false, emptyList(), 0, work, zone) as Decision.Intercept
    assertEquals(ShieldLevel.SOFT, soft.shield)
    val hard = Policy.decide(settings, target(ig)!!.copy(shield = ShieldSetting.HARDCORE), false, emptyList(), 0, work, zone) as Decision.Intercept
    assertEquals(false, hard.canBypass)
  }

  @Test fun `bypass starts a cool-down but a swap does not`() {
    val t0 = at(2026, 9, 8, 20)
    val logs = mutableListOf(log(ig, ActionTaken.BYPASSED, t0))
    val d = Policy.decide(settings, target(ig), false, logs, 0, t0 + 5 * minute, zone)
    assertEquals(Decision.Allow(AllowReason.COOLDOWN, 300), d)
    assertTrue(Policy.decide(settings, target(ig), false, logs, 0, t0 + 10 * minute, zone) is Decision.Intercept)
    logs += log(ig, ActionTaken.SWAPPED, t0 + 11 * minute, SwapType.STRETCH)
    assertTrue(Policy.decide(settings, target(ig), false, logs, 0, t0 + 11 * minute + 1000, zone) is Decision.Intercept)
    // per-app: YouTube has its own log list
    assertTrue(Policy.decide(settings, target(yt), false, emptyList(), 0, t0 + minute, zone) is Decision.Intercept)
  }

  @Test fun `exhausted quota forces HARDCORE with no bypass`() {
    val now = at(2026, 9, 8, 20)
    val d = Policy.decide(settings, target(ig), false, emptyList(), 30, now, zone) as Decision.Intercept
    assertEquals(ContextMode.QUOTA, d.mode)
    assertEquals(ShieldLevel.HARDCORE, d.shield)
    assertEquals(false, d.canBypass)
    assertEquals(0, d.quotaLeftMinutes)
    val fresh = Policy.decide(settings, target(ig), false, emptyList(), 0, now, zone) as Decision.Intercept
    assertEquals(ShieldLevel.SOFT, fresh.shield)
    assertEquals(30, fresh.quotaLeftMinutes)
  }

  @Test fun `friction fatigue guard softens NORMAL but never HARDCORE`() {
    val t0 = at(2026, 9, 8, 10)
    val logs = (0 until 6).map { log(ig, ActionTaken.DISMISSED_HOME, t0 + it * minute) }
    val d = Policy.decide(settings, target(ig), false, logs, 0, t0 + 7 * minute, zone) as Decision.Intercept
    assertEquals(ShieldLevel.SOFT, d.shield)
    val night = at(2026, 9, 8, 23, 30)
    val nightLogs = (0 until 6).map { log(yt, ActionTaken.DISMISSED_HOME, night + it * minute) }
    val n = Policy.decide(settings, target(yt), false, nightLogs, 0, night + 7 * minute, zone) as Decision.Intercept
    assertEquals(ShieldLevel.HARDCORE, n.shield)
  }

  @Test fun `stats swap ratio saved minutes focus score streak`() {
    val logs = mutableListOf<InterceptLog>()
    for (d in listOf(6, 7, 8)) {
      logs += log(ig, ActionTaken.SWAPPED, at(2026, 9, d, 9), SwapType.VOCAB)
      logs += log(yt, ActionTaken.DISMISSED_HOME, at(2026, 9, d, 13))
      if (d == 7) logs += log(ig, ActionTaken.BYPASSED, at(2026, 9, d, 21))
    }
    val now = at(2026, 9, 8, 22)
    val st = Policy.stats(settings, logs, targets, emptyMap(), now, zone)
    assertEquals(1, st.today.swaps)
    assertEquals(1, st.today.exits)
    assertEquals(10, st.today.savedMinutes)
    assertEquals(7, st.week.intercepts)
    assertEquals(6, st.week.avoided)
    assertEquals(Math.round(70 * (6.0 / 7) + 30).toInt(), st.focusScore)
    assertEquals(3, st.streakDays)
    val over = mapOf(ig to 31, yt to 46, "com.zhiliaoapp.musically" to 21)
    val st2 = Policy.stats(settings, logs, targets, over, now, zone)
    assertEquals(0.0, st2.quotaAdherence)
    assertEquals(Math.round(70 * (6.0 / 7)).toInt(), st2.focusScore)
  }

  @Test fun `focus score is null without data and streak breaks on bypass-majority days`() {
    val now = at(2026, 9, 8, 22)
    assertNull(Policy.stats(settings, emptyList(), targets, emptyMap(), now, zone).focusScore)
    val logs = listOf(
      log(ig, ActionTaken.BYPASSED, at(2026, 9, 7, 9)),
      log(ig, ActionTaken.BYPASSED, at(2026, 9, 7, 10)),
      log(ig, ActionTaken.SWAPPED, at(2026, 9, 7, 11), SwapType.EMOTION, Emotion.BORED),
      log(ig, ActionTaken.SWAPPED, at(2026, 9, 8, 11), SwapType.EMOTION, Emotion.BORED),
    )
    assertEquals(1, Policy.streakDays(logs, now, zone))
  }

  @Test fun `future-stamped logs are ignored by stats`() {
    val logs = listOf(log(ig, ActionTaken.SWAPPED, at(2026, 9, 8, 10, 15), SwapType.EMOTION, Emotion.BORED))
    val earlier = at(2026, 9, 8, 0, 40)
    val st = Policy.stats(settings, logs, targets, emptyMap(), earlier, zone)
    assertEquals(0, st.today.swaps)
    assertNull(st.focusScore)
    assertEquals(1, Policy.stats(settings, logs, targets, emptyMap(), at(2026, 9, 8, 22), zone).today.swaps)
  }

  @Test fun `settings and targets validate their input`() {
    assertFailsWith<IllegalArgumentException> { PolicySettings(sleepStart = "25:00") }
    assertFailsWith<IllegalArgumentException> { PolicySettings(cooldownMinutes = -1) }
    assertFailsWith<IllegalArgumentException> { TargetRule(ig, quotaMinutes = -5) }
    val zeroCooldown = PolicySettings(cooldownMinutes = 0)
    val t0 = at(2026, 9, 8, 20)
    val logs = listOf(log(ig, ActionTaken.BYPASSED, t0))
    assertTrue(Policy.decide(zeroCooldown, target(ig), false, logs, 0, t0 + 1000, zone) is Decision.Intercept)
    assertEquals("2026-01-05", Policy.dateKey(at(2026, 1, 5, 0), zone))
  }

  @Test fun `notes markdown lists the day's notes with time and source app`() {
    val now = at(2026, 9, 8, 12)
    val notes = listOf(QuickNote(1, now, "Idea: swap, do not block", ig, false), QuickNote(2, at(2026, 9, 7, 9), "yesterday", ig, false))
    val md = Policy.notesMarkdown(notes, Policy.dateKey(now, zone), zone) { "Instagram" }
    assertTrue(md.startsWith("# HabitBridge Notes 2026-09-08"))
    assertTrue(md.contains("- [12:00] Idea: swap, do not block _(instead of Instagram)_"))
    assertEquals(false, md.contains("yesterday"))
  }
}

package com.habitbridge.policy

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * HabitBridge policy engine — pure Kotlin, no Android, no wall clock.
 *
 * Every function takes `nowMillis` (epoch ms) and a [ZoneId] so the rules are
 * deterministic and unit-testable. This file is a 1:1 port of the web
 * prototype's `src/engine.js`; the test vectors in `tests/engine.test.js`
 * are mirrored in `PolicyTest.kt`.
 */

enum class ShieldLevel(val holdSeconds: Int, val canBypass: Boolean) {
  SOFT(3, true),
  NORMAL(5, true),
  HARDCORE(5, false),
}

/** Per-app shield preference. AUTO follows the context rules. */
enum class ShieldSetting { AUTO, SOFT, NORMAL, HARDCORE;
  fun fixedLevel(): ShieldLevel? = when (this) {
    AUTO -> null
    SOFT -> ShieldLevel.SOFT
    NORMAL -> ShieldLevel.NORMAL
    HARDCORE -> ShieldLevel.HARDCORE
  }
}

/** Context modes in priority order (first match wins). */
enum class ContextMode(val shield: ShieldLevel) {
  QUOTA(ShieldLevel.HARDCORE),
  SLEEP(ShieldLevel.HARDCORE),
  MEETING(ShieldLevel.HARDCORE),
  WORK(ShieldLevel.NORMAL),
  FREE(ShieldLevel.SOFT),
}

enum class ActionTaken { SWAPPED, BYPASSED, DISMISSED_HOME }
enum class SwapType { STRETCH, VOCAB, QUICK_NOTE, EMOTION }
enum class Emotion { BORED, ANXIOUS, LONELY, HABIT, TIRED }

data class PolicySettings(
  val sleepStart: String = "23:00",
  val sleepEnd: String = "07:00",
  val workStart: String = "09:00",
  val workEnd: String = "18:00",
  /** ISO day numbers: 1 = Monday … 7 = Sunday. */
  val workDays: Set<Int> = setOf(1, 2, 3, 4, 5),
  val cooldownMinutes: Int = 10,
  val swapGraceMinutes: Int = 0,
  val savedMinutesPerAvoid: Int = 5,
  val maxInterceptsPerHour: Int = 6,
  val swapRatioWeight: Double = 0.7,
  val quotaAdherenceWeight: Double = 0.3,
) {
  init {
    Policy.parseHHMM(sleepStart); Policy.parseHHMM(sleepEnd)
    Policy.parseHHMM(workStart); Policy.parseHHMM(workEnd)
    require(cooldownMinutes in 0..240) { "cooldownMinutes out of range: $cooldownMinutes" }
    require(swapGraceMinutes in 0..240) { "swapGraceMinutes out of range: $swapGraceMinutes" }
    require(savedMinutesPerAvoid in 0..120) { "savedMinutesPerAvoid out of range" }
    require(maxInterceptsPerHour in 1..60) { "maxInterceptsPerHour out of range" }
  }
}

data class TargetRule(
  val packageName: String,
  val enabled: Boolean = true,
  val shield: ShieldSetting = ShieldSetting.AUTO,
  /** 0 = no quota. */
  val quotaMinutes: Int = 0,
) {
  init { require(quotaMinutes in 0..(24 * 60)) { "quotaMinutes out of range: $quotaMinutes" } }
}

data class InterceptLog(
  val id: Long,
  val packageName: String,
  val at: Long,
  val action: ActionTaken,
  val swapType: SwapType? = null,
  val emotion: Emotion? = null,
  val shield: ShieldLevel? = null,
  val mode: ContextMode? = null,
)

data class QuickNote(val id: Long, val at: Long, val content: String, val packageName: String, val exported: Boolean)

data class Signals(val calendarBusy: Boolean = false)

data class ContextResult(val mode: ContextMode, val shield: ShieldLevel)

enum class AllowReason { ALLOWLIST, NOT_TARGET, COOLDOWN, SWAP_GRACE }

sealed class Decision {
  data class Allow(val reason: AllowReason, val cooldownRemainingSec: Int = 0) : Decision()
  data class Intercept(
    val mode: ContextMode,
    val shield: ShieldLevel,
    /** null when the target has no quota. */
    val quotaLeftMinutes: Int?,
  ) : Decision() {
    val holdSeconds: Int get() = shield.holdSeconds
    val canBypass: Boolean get() = shield.canBypass
  }
}

data class Summary(
  val intercepts: Int,
  val swaps: Int,
  val bypasses: Int,
  val exits: Int,
  val savedMinutes: Int,
  val bySwapType: Map<SwapType, Int>,
  val byEmotion: Map<Emotion, Int>,
) {
  val avoided: Int get() = swaps + exits
  /** null when there were no intercepts. */
  val swapRatio: Double? get() = if (intercepts == 0) null else avoided.toDouble() / intercepts
}

data class Stats(
  val today: Summary,
  val week: Summary,
  val focusScore: Int?,
  val streakDays: Int,
  val quotaAdherence: Double,
)

object Policy {
  private const val MINUTE_MS = 60_000L
  private const val HOUR_MS = 60 * MINUTE_MS
  private const val DAY_MS = 24 * HOUR_MS
  private val DATE_KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  // -------------------------------------------------------------------------
  // Time helpers
  // -------------------------------------------------------------------------

  fun parseHHMM(s: String): Int {
    val parts = s.split(":")
    require(parts.size == 2) { "bad time: $s" }
    val h = parts[0].toIntOrNull() ?: throw IllegalArgumentException("bad time: $s")
    val m = parts[1].toIntOrNull() ?: throw IllegalArgumentException("bad time: $s")
    require(h in 0..23 && m in 0..59) { "bad time: $s" }
    return h * 60 + m
  }

  /** Is [minuteOfDay] inside [start, end)? Windows may wrap midnight. start == end means "off". */
  fun inWindow(minuteOfDay: Int, startHHMM: String, endHHMM: String): Boolean {
    val s = parseHHMM(startHHMM)
    val e = parseHHMM(endHHMM)
    if (s == e) return false
    return if (s < e) minuteOfDay in s until e else minuteOfDay >= s || minuteOfDay < e
  }

  fun localDateTime(nowMillis: Long, zone: ZoneId): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)

  fun dateKey(nowMillis: Long, zone: ZoneId): String = localDateTime(nowMillis, zone).toLocalDate().format(DATE_KEY)

  fun dateKey(date: LocalDate): String = date.format(DATE_KEY)

  // -------------------------------------------------------------------------
  // Context-aware smart shield
  // -------------------------------------------------------------------------

  fun evaluateContext(settings: PolicySettings, nowMillis: Long, zone: ZoneId, signals: Signals = Signals()): ContextResult {
    val ldt = localDateTime(nowMillis, zone)
    val minute = ldt.hour * 60 + ldt.minute
    val isoDay = ldt.dayOfWeek.value // Monday = 1
    if (inWindow(minute, settings.sleepStart, settings.sleepEnd)) return ContextResult(ContextMode.SLEEP, ContextMode.SLEEP.shield)
    if (signals.calendarBusy) return ContextResult(ContextMode.MEETING, ContextMode.MEETING.shield)
    if (isoDay in settings.workDays && inWindow(minute, settings.workStart, settings.workEnd)) {
      return ContextResult(ContextMode.WORK, ContextMode.WORK.shield)
    }
    return ContextResult(ContextMode.FREE, ContextMode.FREE.shield)
  }

  // -------------------------------------------------------------------------
  // Decision
  // -------------------------------------------------------------------------

  /**
   * @param logsForPackage every log entry for this package (any order).
   * @param usageTodayMinutes minutes the package was in the foreground today.
   */
  fun decide(
    settings: PolicySettings,
    target: TargetRule?,
    allowlisted: Boolean,
    logsForPackage: List<InterceptLog>,
    usageTodayMinutes: Int,
    nowMillis: Long,
    zone: ZoneId,
    signals: Signals = Signals(),
  ): Decision {
    if (allowlisted) return Decision.Allow(AllowReason.ALLOWLIST)
    if (target == null || !target.enabled) return Decision.Allow(AllowReason.NOT_TARGET)

    val lastBypass = logsForPackage.filter { it.action == ActionTaken.BYPASSED }.maxByOrNull { it.at }
    if (lastBypass != null) {
      val remaining = lastBypass.at + settings.cooldownMinutes * MINUTE_MS - nowMillis
      if (remaining > 0) return Decision.Allow(AllowReason.COOLDOWN, ceilSeconds(remaining))
    }
    if (settings.swapGraceMinutes > 0) {
      val lastSwap = logsForPackage.filter { it.action == ActionTaken.SWAPPED }.maxByOrNull { it.at }
      if (lastSwap != null) {
        val remaining = lastSwap.at + settings.swapGraceMinutes * MINUTE_MS - nowMillis
        if (remaining > 0) return Decision.Allow(AllowReason.SWAP_GRACE, ceilSeconds(remaining))
      }
    }

    val ctx = evaluateContext(settings, nowMillis, zone, signals)
    var mode = ctx.mode
    var shield = target.shield.fixedLevel() ?: ctx.shield

    val quotaLeft: Int? = if (target.quotaMinutes > 0) target.quotaMinutes - usageTodayMinutes else null
    if (quotaLeft != null && quotaLeft <= 0) {
      mode = ContextMode.QUOTA
      shield = ContextMode.QUOTA.shield
    } else if (shield != ShieldLevel.HARDCORE) {
      // Friction-fatigue guard: many intercepts in an hour means the pause is
      // not working as a pause any more; soften instead of nagging harder.
      val since = nowMillis - HOUR_MS
      val recent = logsForPackage.count { it.at > since && it.at <= nowMillis }
      if (recent >= settings.maxInterceptsPerHour) shield = ShieldLevel.SOFT
    }
    return Decision.Intercept(mode = mode, shield = shield, quotaLeftMinutes = quotaLeft?.coerceAtLeast(0))
  }

  private fun ceilSeconds(ms: Long): Int = ((ms + 999) / 1000).toInt()

  // -------------------------------------------------------------------------
  // Statistics & Focus Score
  // -------------------------------------------------------------------------

  fun summarize(logs: List<InterceptLog>, settings: PolicySettings): Summary {
    var swaps = 0; var bypasses = 0; var exits = 0
    val byType = mutableMapOf<SwapType, Int>()
    val byEmotion = mutableMapOf<Emotion, Int>()
    for (l in logs) {
      when (l.action) {
        ActionTaken.SWAPPED -> {
          swaps++
          l.swapType?.let { byType[it] = (byType[it] ?: 0) + 1 }
          l.emotion?.let { byEmotion[it] = (byEmotion[it] ?: 0) + 1 }
        }
        ActionTaken.BYPASSED -> bypasses++
        ActionTaken.DISMISSED_HOME -> exits++
      }
    }
    return Summary(
      intercepts = logs.size,
      swaps = swaps,
      bypasses = bypasses,
      exits = exits,
      savedMinutes = (swaps + exits) * settings.savedMinutesPerAvoid,
      bySwapType = byType,
      byEmotion = byEmotion,
    )
  }

  /** Share of quota-bearing enabled targets whose usage today is within quota. 1.0 when no quotas exist. */
  fun quotaAdherence(targets: List<TargetRule>, usageTodayMinutes: Map<String, Int>): Double {
    val withQuota = targets.filter { it.enabled && it.quotaMinutes > 0 }
    if (withQuota.isEmpty()) return 1.0
    val ok = withQuota.count { (usageTodayMinutes[it.packageName] ?: 0) <= it.quotaMinutes }
    return ok.toDouble() / withQuota.size
  }

  /** Focus Score 0..100 = 70% swap ratio (7 days) + 30% quota adherence (today). null with no data. */
  fun focusScore(settings: PolicySettings, weekLogs: List<InterceptLog>, quotaAdherence: Double): Int? {
    val s = summarize(weekLogs, settings)
    val ratio = s.swapRatio ?: return null
    val score = 100 * (settings.swapRatioWeight * ratio + settings.quotaAdherenceWeight * quotaAdherence)
    return Math.round(score).toInt().coerceIn(0, 100)
  }

  /**
   * Consecutive days with at least one avoided session and no bypass majority.
   * If today does not qualify yet, counting starts from yesterday.
   */
  fun streakDays(logs: List<InterceptLog>, nowMillis: Long, zone: ZoneId): Int {
    data class Day(var avoided: Int = 0, var bypassed: Int = 0)
    val byDay = HashMap<String, Day>()
    for (l in logs) {
      val d = byDay.getOrPut(dateKey(l.at, zone)) { Day() }
      if (l.action == ActionTaken.BYPASSED) d.bypassed++ else d.avoided++
    }
    fun good(d: Day?) = d != null && d.avoided > 0 && d.avoided >= d.bypassed
    var cursor = localDateTime(nowMillis, zone).toLocalDate()
    if (!good(byDay[dateKey(cursor)])) cursor = cursor.minusDays(1)
    var streak = 0
    repeat(365) {
      if (!good(byDay[dateKey(cursor)])) return streak
      streak++
      cursor = cursor.minusDays(1)
    }
    return streak
  }

  fun stats(
    settings: PolicySettings,
    logs: List<InterceptLog>,
    targets: List<TargetRule>,
    usageTodayMinutes: Map<String, Int>,
    nowMillis: Long,
    zone: ZoneId,
  ): Stats {
    val todayKey = dateKey(nowMillis, zone)
    // Logs stamped after `now` are "not yet happened" (clock adjustments, demo clocks).
    val today = logs.filter { it.at <= nowMillis && dateKey(it.at, zone) == todayKey }
    val week = logs.filter { it.at <= nowMillis && it.at > nowMillis - 7 * DAY_MS }
    val adherence = quotaAdherence(targets, usageTodayMinutes)
    return Stats(
      today = summarize(today, settings),
      week = summarize(week, settings),
      focusScore = focusScore(settings, week, adherence),
      streakDays = streakDays(logs, nowMillis, zone),
      quotaAdherence = adherence,
    )
  }

  // -------------------------------------------------------------------------
  // Markdown export
  // -------------------------------------------------------------------------

  fun notesMarkdown(notes: List<QuickNote>, dateKey: String, zone: ZoneId, appName: (String) -> String): String {
    val sb = StringBuilder()
    sb.append("# HabitBridge Notes ").append(dateKey).append("\n\n")
    val dayNotes = notes.filter { dateKey(it.at, zone) == dateKey }.sortedBy { it.at }
    if (dayNotes.isEmpty()) sb.append("_No notes yet._\n")
    for (n in dayNotes) {
      val t = localDateTime(n.at, zone)
      sb.append("- [").append("%02d:%02d".format(t.hour, t.minute)).append("] ")
        .append(n.content).append(" _(instead of ").append(appName(n.packageName)).append(")_\n")
    }
    return sb.toString()
  }
}

/** ISO day-of-week helper for UI code. */
fun DayOfWeek.isoValue(): Int = this.value

package com.habitbridge.data

import com.habitbridge.policy.ActionTaken
import com.habitbridge.policy.ContextMode
import com.habitbridge.policy.Decision
import com.habitbridge.policy.Emotion
import com.habitbridge.policy.InterceptLog
import com.habitbridge.policy.Policy
import com.habitbridge.policy.PolicySettings
import com.habitbridge.policy.QuickNote
import com.habitbridge.policy.ShieldLevel
import com.habitbridge.policy.ShieldSetting
import com.habitbridge.policy.Signals
import com.habitbridge.policy.Stats
import com.habitbridge.policy.SwapType
import com.habitbridge.policy.TargetRule
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Single entry point for the app's data. Translates Room rows into the pure
 * policy types and back. All time arguments are epoch millis; the zone is the
 * device zone captured at construction (re-created on locale/zone change).
 */
class HabitRepository(private val db: AppDatabase, val zone: ZoneId = ZoneId.systemDefault()) {

  private val DAY_MS = 24L * 60 * 60 * 1000

  // ---------------------------------------------------------------------
  // Flows for UI
  // ---------------------------------------------------------------------

  val targets: Flow<List<TargetAppEntity>> = db.targets().observeAll()
  val allowlist: Flow<List<AllowlistEntity>> = db.allowlist().observeAll()
  val settings: Flow<SettingsEntity> = db.settings().observe().map { it ?: SettingsEntity() }
  val notes: Flow<List<QuickNoteEntity>> = db.notes().observeAll()

  fun usageToday(now: Long): Flow<Map<String, Int>> =
    db.usage().observeDay(Policy.dateKey(now, zone)).map { rows -> rows.associate { it.packageName to it.seconds / 60 } }

  fun recentLogs(now: Long): Flow<List<SwapLogEntity>> = db.logs().observeSince(now - 7 * DAY_MS)

  /** Live stats for the dashboard: recomputed whenever logs, targets, usage or settings change. */
  fun stats(now: Long): Flow<Stats> =
    combine(db.logs().observeSince(now - 400 * DAY_MS), targets, usageToday(now), settings) { logs, tg, usage, st ->
      Policy.stats(st.toPolicy(), logs.map { it.toPolicy() }, tg.map { it.toRule() }, usage, now, zone)
    }

  // ---------------------------------------------------------------------
  // Decisions & recording (called from the detection pipeline)
  // ---------------------------------------------------------------------

  suspend fun policySettings(): PolicySettings = (db.settings().get() ?: SettingsEntity()).toPolicy()

  suspend fun settingsEntity(): SettingsEntity = db.settings().get() ?: SettingsEntity()

  suspend fun isTarget(pkg: String): Boolean = db.targets().get(pkg)?.isEnabled == true

  suspend fun decide(pkg: String, now: Long, signals: Signals): Decision {
    val allowlisted = db.allowlist().packages().contains(pkg)
    val target = db.targets().get(pkg)?.toRule()
    val logs = db.logs().forPackageSince(pkg, now - DAY_MS).map { it.toPolicy() }
    val used = (db.usage().seconds(Policy.dateKey(now, zone), pkg) ?: 0) / 60
    return Policy.decide(policySettings(), target, allowlisted, logs, used, now, zone, signals)
  }

  suspend fun record(
    pkg: String,
    action: ActionTaken,
    now: Long,
    swapType: SwapType? = null,
    emotion: Emotion? = null,
    note: String? = null,
    shield: ShieldLevel? = null,
    mode: ContextMode? = null,
  ) {
    require(action != ActionTaken.SWAPPED || swapType != null) { "swapType required for SWAPPED" }
    db.logs().insert(SwapLogEntity(0, pkg, now, action, swapType, emotion, shield, mode))
    if (swapType == SwapType.QUICK_NOTE) {
      val content = note?.trim().orEmpty()
      require(content.isNotEmpty()) { "empty note" }
      db.notes().insert(QuickNoteEntity(0, now, content, pkg))
    }
  }

  suspend fun addUsageSeconds(pkg: String, seconds: Int, now: Long) {
    if (seconds <= 0) return
    val key = Policy.dateKey(now, zone)
    val current = db.usage().seconds(key, pkg) ?: 0
    db.usage().upsert(UsageDailyEntity(key, pkg, current + seconds))
  }

  suspend fun pruneOldUsage(now: Long, keepDays: Long = 90) {
    val cutoff = Policy.dateKey(LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now - keepDays * DAY_MS), zone))
    db.usage().prune(cutoff)
  }

  // ---------------------------------------------------------------------
  // Settings & targets
  // ---------------------------------------------------------------------

  suspend fun upsertTarget(pkg: String, appName: String, enabled: Boolean = true, shield: ShieldSetting = ShieldSetting.AUTO, quota: Int = 0) {
    val existing = db.targets().get(pkg)
    db.targets().upsert(
      TargetAppEntity(pkg, appName, enabled, shield, quota.coerceIn(0, 24 * 60), existing?.createdAt ?: System.currentTimeMillis()),
    )
    db.allowlist().delete(pkg)
  }

  suspend fun updateTarget(entity: TargetAppEntity) = db.targets().upsert(entity)

  suspend fun removeTarget(pkg: String) = db.targets().delete(pkg)

  suspend fun addToAllowlist(pkg: String, appName: String) {
    db.allowlist().upsert(AllowlistEntity(pkg, appName))
    db.targets().delete(pkg)
  }

  suspend fun removeFromAllowlist(pkg: String) = db.allowlist().delete(pkg)

  suspend fun updateSettings(transform: (SettingsEntity) -> SettingsEntity) {
    val current = db.settings().get() ?: SettingsEntity()
    val next = transform(current)
    next.toPolicy() // validates; throws IllegalArgumentException on bad input
    db.settings().upsert(next)
  }

  // ---------------------------------------------------------------------
  // Vocab & notes
  // ---------------------------------------------------------------------

  suspend fun nextVocab(excludeId: Int? = null): VocabEntity? =
    if (excludeId == null) db.vocab().next() else (db.vocab().nextExcept(excludeId) ?: db.vocab().next())

  suspend fun markVocabSeen(id: Int) = db.vocab().markSeen(id)

  fun dayBounds(now: Long): Pair<Long, Long> {
    val start = Policy.localDateTime(now, zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    return start to start + DAY_MS
  }

  suspend fun notesMarkdown(now: Long, appName: (String) -> String): String {
    val (from, to) = dayBounds(now)
    val notes = db.notes().between(from, to).map { QuickNote(it.id, it.timestamp, it.content, it.triggeredPackage, it.isExportedToMd) }
    return Policy.notesMarkdown(notes, Policy.dateKey(now, zone), zone, appName)
  }

  suspend fun markNotesExported(now: Long): Int {
    val (from, to) = dayBounds(now)
    return db.notes().markExported(from, to)
  }

  // ---------------------------------------------------------------------
  // Mappers
  // ---------------------------------------------------------------------

  private fun SwapLogEntity.toPolicy() = InterceptLog(logId, targetPackage, timestamp, actionTaken, swappedType, emotion, shieldApplied, contextMode)
  private fun TargetAppEntity.toRule() = TargetRule(packageName, isEnabled, shieldLevel, dailyQuotaMinutes)

  companion object {
    fun SettingsEntity.toPolicy() = PolicySettings(
      sleepStart = sleepStart,
      sleepEnd = sleepEnd,
      workStart = workStart,
      workEnd = workEnd,
      workDays = workDays.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet(),
      cooldownMinutes = cooldownMinutes,
      swapGraceMinutes = swapGraceMinutes,
      savedMinutesPerAvoid = savedMinutesPerAvoid,
      maxInterceptsPerHour = maxInterceptsPerHour,
    )
  }
}

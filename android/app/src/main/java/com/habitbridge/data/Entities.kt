package com.habitbridge.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.habitbridge.policy.ActionTaken
import com.habitbridge.policy.ContextMode
import com.habitbridge.policy.Emotion
import com.habitbridge.policy.ShieldLevel
import com.habitbridge.policy.ShieldSetting
import com.habitbridge.policy.SwapType

// Room stores enums by name via its built-in converters (PRD §5).

@Entity(tableName = "target_apps")
data class TargetAppEntity(
  @PrimaryKey val packageName: String,
  val appName: String,
  val isEnabled: Boolean = true,
  val shieldLevel: ShieldSetting = ShieldSetting.AUTO,
  val dailyQuotaMinutes: Int = 0,
  val createdAt: Long,
)

@Entity(tableName = "allowlist")
data class AllowlistEntity(
  @PrimaryKey val packageName: String,
  val appName: String,
)

@Entity(tableName = "swap_logs", indices = [Index("timestamp"), Index("targetPackage")])
data class SwapLogEntity(
  @PrimaryKey(autoGenerate = true) val logId: Long = 0,
  val targetPackage: String,
  val timestamp: Long,
  val actionTaken: ActionTaken,
  val swappedType: SwapType? = null,
  val emotion: Emotion? = null,
  val shieldApplied: ShieldLevel? = null,
  val contextMode: ContextMode? = null,
)

@Entity(tableName = "quick_notes", indices = [Index("timestamp")])
data class QuickNoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestamp: Long,
  val content: String,
  val triggeredPackage: String,
  val isExportedToMd: Boolean = false,
)

@Entity(tableName = "usage_daily", primaryKeys = ["dateKey", "packageName"])
data class UsageDailyEntity(
  val dateKey: String,
  val packageName: String,
  val seconds: Int,
)

@Entity(tableName = "settings")
data class SettingsEntity(
  @PrimaryKey val id: Int = 1,
  val sleepStart: String = "23:00",
  val sleepEnd: String = "07:00",
  val workStart: String = "09:00",
  val workEnd: String = "18:00",
  /** ISO day numbers, comma separated: "1,2,3,4,5". */
  val workDays: String = "1,2,3,4,5",
  val cooldownMinutes: Int = 10,
  val swapGraceMinutes: Int = 0,
  val savedMinutesPerAvoid: Int = 5,
  val maxInterceptsPerHour: Int = 6,
  val calendarEnabled: Boolean = false,
  val onboardingDone: Boolean = false,
)

@Entity(tableName = "vocab")
data class VocabEntity(
  @PrimaryKey val id: Int,
  val word: String,
  val meaning: String,
  val example: String,
  val seenCount: Int = 0,
)

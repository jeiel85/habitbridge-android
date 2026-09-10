package com.habitbridge.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
  @Query("SELECT * FROM target_apps ORDER BY appName COLLATE NOCASE")
  fun observeAll(): Flow<List<TargetAppEntity>>

  @Query("SELECT * FROM target_apps")
  suspend fun getAll(): List<TargetAppEntity>

  @Query("SELECT * FROM target_apps WHERE packageName = :pkg LIMIT 1")
  suspend fun get(pkg: String): TargetAppEntity?

  @Upsert
  suspend fun upsert(entity: TargetAppEntity)

  @Query("DELETE FROM target_apps WHERE packageName = :pkg")
  suspend fun delete(pkg: String)
}

@Dao
interface AllowlistDao {
  @Query("SELECT * FROM allowlist ORDER BY appName COLLATE NOCASE")
  fun observeAll(): Flow<List<AllowlistEntity>>

  @Query("SELECT packageName FROM allowlist")
  suspend fun packages(): List<String>

  @Upsert
  suspend fun upsert(entity: AllowlistEntity)

  @Query("DELETE FROM allowlist WHERE packageName = :pkg")
  suspend fun delete(pkg: String)
}

@Dao
interface SwapLogDao {
  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insert(entity: SwapLogEntity): Long

  @Query("SELECT * FROM swap_logs WHERE timestamp >= :since ORDER BY timestamp DESC")
  fun observeSince(since: Long): Flow<List<SwapLogEntity>>

  @Query("SELECT * FROM swap_logs WHERE timestamp >= :since ORDER BY timestamp ASC")
  suspend fun since(since: Long): List<SwapLogEntity>

  @Query("SELECT * FROM swap_logs WHERE targetPackage = :pkg AND timestamp >= :since ORDER BY timestamp ASC")
  suspend fun forPackageSince(pkg: String, since: Long): List<SwapLogEntity>
}

@Dao
interface QuickNoteDao {
  @Insert
  suspend fun insert(entity: QuickNoteEntity): Long

  @Query("SELECT * FROM quick_notes ORDER BY timestamp DESC")
  fun observeAll(): Flow<List<QuickNoteEntity>>

  @Query("SELECT * FROM quick_notes WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
  suspend fun between(from: Long, to: Long): List<QuickNoteEntity>

  @Query("UPDATE quick_notes SET isExportedToMd = 1 WHERE timestamp >= :from AND timestamp < :to")
  suspend fun markExported(from: Long, to: Long): Int
}

@Dao
interface UsageDao {
  @Query("SELECT * FROM usage_daily WHERE dateKey = :dateKey")
  fun observeDay(dateKey: String): Flow<List<UsageDailyEntity>>

  @Query("SELECT * FROM usage_daily WHERE dateKey = :dateKey")
  suspend fun day(dateKey: String): List<UsageDailyEntity>

  @Query("SELECT seconds FROM usage_daily WHERE dateKey = :dateKey AND packageName = :pkg LIMIT 1")
  suspend fun seconds(dateKey: String, pkg: String): Int?

  @Upsert
  suspend fun upsert(entity: UsageDailyEntity)

  @Query("DELETE FROM usage_daily WHERE dateKey < :olderThanKey")
  suspend fun prune(olderThanKey: String): Int
}

@Dao
interface SettingsDao {
  @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
  fun observe(): Flow<SettingsEntity?>

  @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
  suspend fun get(): SettingsEntity?

  @Upsert
  suspend fun upsert(entity: SettingsEntity)
}

@Dao
interface VocabDao {
  @Query("SELECT COUNT(*) FROM vocab")
  suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(items: List<VocabEntity>)

  /** Least-seen first, random among ties, so the deck rotates. */
  @Query("SELECT * FROM vocab ORDER BY seenCount ASC, RANDOM() LIMIT 1")
  suspend fun next(): VocabEntity?

  @Query("SELECT * FROM vocab WHERE id != :excludeId ORDER BY seenCount ASC, RANDOM() LIMIT 1")
  suspend fun nextExcept(excludeId: Int): VocabEntity?

  @Query("UPDATE vocab SET seenCount = seenCount + 1 WHERE id = :id")
  suspend fun markSeen(id: Int)

  @Delete
  suspend fun delete(item: VocabEntity)
}

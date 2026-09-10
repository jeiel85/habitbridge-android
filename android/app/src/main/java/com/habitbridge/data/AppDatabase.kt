package com.habitbridge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
  entities = [
    TargetAppEntity::class,
    AllowlistEntity::class,
    SwapLogEntity::class,
    QuickNoteEntity::class,
    UsageDailyEntity::class,
    SettingsEntity::class,
    VocabEntity::class,
  ],
  version = 1,
  exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun targets(): TargetDao
  abstract fun allowlist(): AllowlistDao
  abstract fun logs(): SwapLogDao
  abstract fun notes(): QuickNoteDao
  abstract fun usage(): UsageDao
  abstract fun settings(): SettingsDao
  abstract fun vocab(): VocabDao

  companion object {
    const val NAME = "habitbridge.db"

    @Volatile private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
      instance ?: build(context.applicationContext).also { instance = it }
    }

    private fun build(context: Context): AppDatabase {
      lateinit var db: AppDatabase
      db = Room.databaseBuilder(context, AppDatabase::class.java, NAME)
        // No destructive fallback: local-only data cannot be recovered (PRD §5).
        .addCallback(object : Callback() {
          override fun onCreate(sqlite: SupportSQLiteDatabase) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { seed(db) }
          }
          override fun onOpen(sqlite: SupportSQLiteDatabase) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { seed(db) }
          }
        })
        .build()
      return db
    }

    private suspend fun seed(db: AppDatabase) {
      if (db.settings().get() == null) db.settings().upsert(SettingsEntity())
      if (db.vocab().count() == 0) db.vocab().insertAll(VocabSeed.items)
    }
  }
}

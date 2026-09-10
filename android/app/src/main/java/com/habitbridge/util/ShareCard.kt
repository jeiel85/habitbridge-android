package com.habitbridge.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.habitbridge.R
import com.habitbridge.policy.Stats
import com.habitbridge.policy.SwapType
import java.io.File
import java.io.FileOutputStream

/** 1080×1920 "dopamine swap report" (PRD §4.8). Contains no app names, notes or emotions (DECISIONS D-006). */
object ShareCard {
  private const val W = 1080
  private const val H = 1920
  private val bg = Color.parseColor("#0B0F0E")
  private val mint = Color.parseColor("#34D399")
  private val ink = Color.parseColor("#E6F4EF")
  private val muted = Color.parseColor("#7C9A8E")
  private val ring = Color.parseColor("#16302A")
  private val amber = Color.parseColor("#FBBF24")
  private val red = Color.parseColor("#F87171")

  fun render(context: Context, stats: Stats, dateKey: String): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(bg)
    val mono = Typeface.MONOSPACE
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono }

    paint.color = Color.argb(20, 52, 211, 153); paint.strokeWidth = 2f
    var y = 0f
    while (y < H) { c.drawLine(0f, y, W.toFloat(), y, paint); y += 80f }

    paint.color = mint; paint.textSize = 40f; paint.typeface = Typeface.create(mono, Typeface.BOLD)
    c.drawText("~/habitbridge/report", 80f, 140f, paint)
    paint.color = ink; paint.textSize = 64f
    c.drawText(context.getString(R.string.card_title), 80f, 250f, paint)
    paint.color = muted; paint.textSize = 36f; paint.typeface = mono
    c.drawText(dateKey, 80f, 310f, paint)

    val cx = W / 2f; val cy = 700f; val r = 260f
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 34f; strokeCap = Paint.Cap.ROUND }
    stroke.color = ring
    c.drawCircle(cx, cy, r, stroke)
    val score = stats.focusScore
    if (score != null) {
      stroke.color = when { score >= 70 -> mint; score >= 40 -> amber; else -> red }
      c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * score / 100f, false, stroke)
    }
    paint.color = ink; paint.textSize = 200f; paint.typeface = Typeface.create(mono, Typeface.BOLD); paint.textAlign = Paint.Align.CENTER
    c.drawText(score?.toString() ?: "–", cx, cy + 70f, paint)
    paint.color = muted; paint.textSize = 40f; paint.typeface = mono
    c.drawText("FOCUS SCORE", cx, cy + 150f, paint)
    paint.textAlign = Paint.Align.LEFT

    val ratio = stats.week.swapRatio
    val rows = listOf(
      context.getString(R.string.card_saved) to "${stats.week.savedMinutes}m",
      context.getString(R.string.card_ratio) to (ratio?.let { "${Math.round(it * 100)}%" } ?: "–"),
      context.getString(R.string.card_streak) to stats.streakDays.toString(),
    )
    var ry = 1130f
    for ((k, v) in rows) {
      paint.color = muted; paint.textSize = 36f; paint.typeface = mono
      c.drawText(k, 120f, ry, paint)
      paint.color = ink; paint.textSize = 96f; paint.typeface = Typeface.create(mono, Typeface.BOLD)
      c.drawText(v, 120f, ry + 100f, paint)
      ry += 200f
    }

    val counts = SwapType.entries.map { stats.week.bySwapType[it] ?: 0 }
    val max = maxOf(1, counts.maxOrNull() ?: 0)
    val baseY = 1740f
    SwapType.entries.forEachIndexed { i, type ->
      val x = 120f + i * 220f
      paint.color = ring
      c.drawRect(x, baseY - 120f, x + 160f, baseY, paint)
      paint.color = mint
      val h = 120f * counts[i] / max
      c.drawRect(x, baseY - h, x + 160f, baseY, paint)
      paint.color = muted; paint.textSize = 26f; paint.typeface = mono
      c.drawText(type.name.replace('_', ' '), x, baseY + 40f, paint)
    }
    paint.color = mint; paint.textSize = 32f
    c.drawText(context.getString(R.string.card_footer), 120f, 1860f, paint)
    return bmp
  }

  fun share(context: Context, bitmap: Bitmap) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, "habitbridge-report.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.card_share_via)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }
}

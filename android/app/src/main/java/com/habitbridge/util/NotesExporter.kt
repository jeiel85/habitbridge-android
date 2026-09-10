package com.habitbridge.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

/**
 * Writes Documents/HabitBridge/<name>.md through MediaStore (API 29+), replacing a
 * file of the same name that this app created earlier (idempotent daily export,
 * PRD §4.9). Returns null when MediaStore is unavailable; callers fall back to SAF.
 */
object NotesExporter {
  private const val RELATIVE_DIR = "HabitBridge"

  fun exportToDocuments(context: Context, fileName: String, content: String): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val resolver = context.contentResolver
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$RELATIVE_DIR/"
    return try {
      val existing = findExisting(context, collection, fileName, relativePath)
      val uri = existing ?: resolver.insert(
        collection,
        ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
          put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
          put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
          put(MediaStore.MediaColumns.IS_PENDING, 1)
        },
      ) ?: return null
      resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: return null
      if (existing == null) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
      }
      uri
    } catch (t: Throwable) {
      Log.w("HabitBridge/Export", "export failed", t)
      null
    }
  }

  private fun findExisting(context: Context, collection: Uri, fileName: String, relativePath: String): Uri? {
    val projection = arrayOf(MediaStore.MediaColumns._ID)
    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
    context.contentResolver.query(collection, projection, selection, arrayOf(fileName, relativePath), null)?.use { c ->
      if (c.moveToFirst()) return Uri.withAppendedPath(collection, c.getLong(0).toString())
    }
    return null
  }

  fun writeTo(context: Context, uri: Uri, content: String): Boolean = try {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } != null
  } catch (t: Throwable) {
    Log.w("HabitBridge/Export", "SAF write failed", t)
    false
  }
}

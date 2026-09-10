package com.habitbridge.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitbridge.HabitBridgeApp
import com.habitbridge.R
import com.habitbridge.policy.Policy
import com.habitbridge.ui.theme.Muted
import com.habitbridge.util.InstalledApps
import com.habitbridge.util.NotesExporter
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
fun NotesScreen(app: HabitBridgeApp, modifier: Modifier = Modifier, onSafExport: (fileName: String, content: String) -> Unit) {
  val context = LocalContext.current
  val repo = app.repository
  val notes by repo.notes.collectAsStateWithLifecycle(initialValue = emptyList())
  val scope = rememberCoroutineScope()

  LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item {
      ScreenTitle(stringResource(R.string.notes_title))
      Button(onClick = {
        scope.launch {
          val now = System.currentTimeMillis()
          val name = "Notes_${Policy.dateKey(now, repo.zone).replace("-", "")}.md"
          val md = repo.notesMarkdown(now) { InstalledApps.label(context, it) }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = NotesExporter.exportToDocuments(context, name, md)
            if (uri != null) {
              repo.markNotesExported(now)
              Toast.makeText(context, context.getString(R.string.notes_exported, name), Toast.LENGTH_LONG).show()
            } else Toast.makeText(context, R.string.notes_export_failed, Toast.LENGTH_LONG).show()
          } else onSafExport(name, md)
        }
      }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.notes_export)) }
    }
    if (notes.isEmpty()) item { Text(stringResource(R.string.notes_empty), color = Muted) }
    items(notes, key = { it.id }) { n ->
      val time = remember(n.timestamp) { fmt.format(Instant.ofEpochMilli(n.timestamp).atZone(repo.zone)) }
      val appName = remember(n.triggeredPackage) { InstalledApps.label(context, n.triggeredPackage) }
      SectionCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(time, fontFamily = FontFamily.Monospace, color = Muted, style = MaterialTheme.typography.bodySmall)
          Column {
            Text(n.content)
            Text(stringResource(R.string.notes_instead_of, appName) + if (n.isExportedToMd) " · .md" else "", color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
          }
        }
      }
    }
  }
}

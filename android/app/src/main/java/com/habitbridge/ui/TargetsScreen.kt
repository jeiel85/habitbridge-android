package com.habitbridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitbridge.HabitBridgeApp
import com.habitbridge.R
import com.habitbridge.data.TargetAppEntity
import com.habitbridge.policy.ShieldSetting
import com.habitbridge.ui.theme.Muted
import com.habitbridge.util.InstalledApp
import com.habitbridge.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun shieldLabel(s: ShieldSetting): String = when (s) {
  ShieldSetting.AUTO -> stringResource(R.string.shield_auto)
  ShieldSetting.SOFT -> stringResource(R.string.shield_soft)
  ShieldSetting.NORMAL -> stringResource(R.string.shield_normal)
  ShieldSetting.HARDCORE -> stringResource(R.string.shield_hardcore)
}

@Composable
fun TargetsScreen(app: HabitBridgeApp, modifier: Modifier = Modifier) {
  val repo = app.repository
  val targets by repo.targets.collectAsStateWithLifecycle(initialValue = emptyList())
  val allow by repo.allowlist.collectAsStateWithLifecycle(initialValue = emptyList())
  val now = remember { System.currentTimeMillis() }
  val usage by repo.usageToday(now).collectAsStateWithLifecycle(initialValue = emptyMap())
  val scope = rememberCoroutineScope()
  var picker by remember { mutableStateOf(false) }

  LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      ScreenTitle(stringResource(R.string.targets_title))
      Text(stringResource(R.string.targets_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(8.dp))
      Button(onClick = { picker = true }) { Text(stringResource(R.string.targets_add)) }
    }
    if (targets.isEmpty()) item { Text(stringResource(R.string.targets_none), color = Muted) }
    items(targets, key = { it.packageName }) { t ->
      TargetRow(t, usage[t.packageName] ?: 0, onChange = { updated -> scope.launch { repo.updateTarget(updated) } }, onRemove = { scope.launch { repo.removeTarget(t.packageName) } })
    }
    item { Text(stringResource(R.string.targets_allowlist).uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(top = 8.dp)) }
    if (allow.isEmpty()) item { Text(stringResource(R.string.targets_allowlist_none), color = Muted) }
    items(allow, key = { "allow-" + it.packageName }) { a ->
      SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(a.appName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
          TextButton(onClick = { scope.launch { repo.removeFromAllowlist(a.packageName) } }) { Text(stringResource(R.string.targets_remove)) }
        }
      }
    }
  }

  if (picker) AppPickerDialog(onDismiss = { picker = false }, onPick = { appInfo, asTarget ->
    scope.launch {
      if (asTarget) repo.upsertTarget(appInfo.packageName, appInfo.label) else repo.addToAllowlist(appInfo.packageName, appInfo.label)
    }
    picker = false
  })
}

@Composable
private fun TargetRow(t: TargetAppEntity, usedMinutes: Int, onChange: (TargetAppEntity) -> Unit, onRemove: () -> Unit) {
  var menu by remember { mutableStateOf(false) }
  var quotaText by remember(t.packageName, t.dailyQuotaMinutes) { mutableStateOf(t.dailyQuotaMinutes.toString()) }
  SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(t.appName, fontWeight = FontWeight.SemiBold)
        Text(t.packageName, color = Muted, style = MaterialTheme.typography.labelSmall)
      }
      Switch(checked = t.isEnabled, onCheckedChange = { onChange(t.copy(isEnabled = it)) })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Box {
        OutlinedButton(onClick = { menu = true }) { Text(stringResource(R.string.targets_shield) + ": " + shieldLabel(t.shieldLevel)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          ShieldSetting.entries.forEach { s ->
            DropdownMenuItem(text = { Text(shieldLabel(s)) }, onClick = { menu = false; onChange(t.copy(shieldLevel = s)) })
          }
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedTextField(
        value = quotaText,
        onValueChange = { v ->
          quotaText = v.filter { it.isDigit() }.take(4)
          quotaText.toIntOrNull()?.let { q -> if (q in 0..1440) onChange(t.copy(dailyQuotaMinutes = q)) }
        },
        label = { Text(stringResource(R.string.targets_quota)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.weight(1f),
      )
      Column {
        Text(stringResource(R.string.targets_used_today, usedMinutes), style = MaterialTheme.typography.bodySmall, color = if (t.dailyQuotaMinutes in 1..usedMinutes) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRemove) { Text(stringResource(R.string.targets_remove)) }
      }
    }
  }
}

@Composable
private fun AppPickerDialog(onDismiss: () -> Unit, onPick: (InstalledApp, Boolean) -> Unit) {
  val context = LocalContext.current
  var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
  var query by remember { mutableStateOf("") }
  var chosen by remember { mutableStateOf<InstalledApp?>(null) }
  LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { InstalledApps.launchable(context) } }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("✕") } },
    title = { Text(stringResource(R.string.targets_add)) },
    text = {
      Column {
        val c = chosen
        if (c != null) {
          Text(c.label, fontWeight = FontWeight.SemiBold)
          Text(c.packageName, color = Muted, style = MaterialTheme.typography.labelSmall)
          Spacer(Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onPick(c, true) }) { Text(stringResource(R.string.targets_make_target)) }
            OutlinedButton(onClick = { onPick(c, false) }) { Text(stringResource(R.string.targets_make_allow)) }
          }
          return@Column
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.targets_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val list = apps
        if (list == null) { Text("…", color = Muted); return@Column }
        val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
        LazyColumn(Modifier.height(360.dp)) {
          items(filtered, key = { it.packageName }) { a ->
            Row(Modifier.fillMaxWidth().clickable { chosen = a }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(a.label)
                Text(a.packageName, color = Muted, style = MaterialTheme.typography.labelSmall)
              }
              Spacer(Modifier.width(8.dp))
            }
          }
        }
      }
    },
  )
}

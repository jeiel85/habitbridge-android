package com.habitbridge.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitbridge.BuildConfig
import com.habitbridge.HabitBridgeApp
import com.habitbridge.R
import com.habitbridge.data.SettingsEntity
import com.habitbridge.policy.Policy
import com.habitbridge.ui.theme.Mint
import com.habitbridge.ui.theme.Muted
import com.habitbridge.util.Permissions
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: HabitBridgeApp, perms: PermissionState, actions: PermissionActions, modifier: Modifier = Modifier, onOpenOnboarding: () -> Unit) {
  val context = LocalContext.current
  val repo = app.repository
  val settings by repo.settings.collectAsStateWithLifecycle(initialValue = SettingsEntity())
  val scope = rememberCoroutineScope()
  fun update(transform: (SettingsEntity) -> SettingsEntity) = scope.launch { runCatching { repo.updateSettings(transform) } }

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    ScreenTitle(stringResource(R.string.settings_title))

    SectionCard(stringResource(R.string.settings_sleep)) {
      TimeWindowRow(settings.sleepStart, settings.sleepEnd) { s, e -> update { it.copy(sleepStart = s, sleepEnd = e) } }
    }
    SectionCard(stringResource(R.string.settings_work)) {
      TimeWindowRow(settings.workStart, settings.workEnd) { s, e -> update { it.copy(workStart = s, workEnd = e) } }
      Text(stringResource(R.string.settings_workdays), color = Muted, style = MaterialTheme.typography.labelMedium)
      val days = settings.workDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
      val labels = listOf(R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, res ->
          val day = i + 1
          FilterChip(selected = day in days, onClick = {
            val next = if (day in days) days - day else days + day
            update { it.copy(workDays = next.sorted().joinToString(",")) }
          }, label = { Text(stringResource(res)) })
        }
      }
    }
    SectionCard(stringResource(R.string.settings_cooldown)) {
      var text by remember(settings.cooldownMinutes) { mutableStateOf(settings.cooldownMinutes.toString()) }
      OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v.filter { it.isDigit() }.take(3); text.toIntOrNull()?.let { m -> if (m in 0..240) update { it.copy(cooldownMinutes = m) } } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Text(stringResource(R.string.settings_fatigue, settings.maxInterceptsPerHour), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    SectionCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.settings_calendar))
          if (!perms.calendar) Text(stringResource(R.string.settings_calendar_needs_permission), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = settings.calendarEnabled && perms.calendar, onCheckedChange = { on ->
          if (on && !perms.calendar) actions.requestCalendar() else update { it.copy(calendarEnabled = on) }
        })
      }
    }
    SectionCard(stringResource(R.string.settings_permissions)) {
      StatusLine(stringResource(R.string.perm_usage_title), perms.usage)
      StatusLine(stringResource(R.string.perm_overlay_title), perms.overlay)
      StatusLine(stringResource(R.string.perm_notif_title), perms.notifications)
      StatusLine(stringResource(R.string.perm_a11y_title), perms.accessibility)
      StatusLine(stringResource(R.string.perm_battery_title), perms.battery)
      StatusLine(if (perms.serviceRunning) stringResource(R.string.onb_service_running) else stringResource(R.string.onb_service_stopped), perms.serviceRunning)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (perms.serviceRunning) OutlinedButton(onClick = actions.stopService) { Text("■") }
        else Button(onClick = actions.startService, enabled = perms.requiredOk) { Text(stringResource(R.string.onb_start_service)) }
        TextButton(onClick = onOpenOnboarding) { Text(stringResource(R.string.onb_title)) }
        TextButton(onClick = { runCatching { context.startActivity(Permissions.appSettingsIntent(context)) } }) { Text("⚙") }
      }
    }
    Text(stringResource(R.string.settings_privacy), color = Muted, style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = Muted, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun StatusLine(label: String, ok: Boolean) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if (ok) "●" else "○", color = if (ok) Mint else Muted)
  }
}

@Composable
private fun TimeWindowRow(start: String, end: String, onChange: (String, String) -> Unit) {
  var s by remember(start) { mutableStateOf(start) }
  var e by remember(end) { mutableStateOf(end) }
  fun valid(v: String) = runCatching { Policy.parseHHMM(v) }.isSuccess
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(value = s, onValueChange = { v -> s = v.take(5); if (valid(s) && valid(e)) onChange(s, e) }, label = { Text(stringResource(R.string.settings_start)) }, isError = !valid(s), singleLine = true, modifier = Modifier.weight(1f))
    OutlinedTextField(value = e, onValueChange = { v -> e = v.take(5); if (valid(s) && valid(e)) onChange(s, e) }, label = { Text(stringResource(R.string.settings_end)) }, isError = !valid(e), singleLine = true, modifier = Modifier.weight(1f))
  }
  if (!valid(s) || !valid(e)) Text(stringResource(R.string.settings_invalid_time), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Suppress("unused")
private fun Intent.noop() = this

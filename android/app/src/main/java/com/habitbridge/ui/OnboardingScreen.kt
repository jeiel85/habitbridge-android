package com.habitbridge.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitbridge.R
import com.habitbridge.ui.theme.Mint
import com.habitbridge.ui.theme.Muted
import com.habitbridge.util.Permissions

@Composable
fun OnboardingScreen(perms: PermissionState, actions: PermissionActions, onContinue: () -> Unit) {
  val context = LocalContext.current
  fun open(intent: Intent) = runCatching { context.startActivity(intent) }

  Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.onb_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)

    PermissionRow(stringResource(R.string.perm_usage_title), stringResource(R.string.perm_usage_why), perms.usage, required = true) { open(Permissions.usageAccessIntent()) }
    PermissionRow(stringResource(R.string.perm_overlay_title), stringResource(R.string.perm_overlay_why), perms.overlay, required = true) { open(Permissions.overlayIntent(context)) }
    PermissionRow(stringResource(R.string.perm_notif_title), stringResource(R.string.perm_notif_why), perms.notifications, required = false) { actions.requestNotifications() }
    PermissionRow(stringResource(R.string.perm_a11y_title), stringResource(R.string.perm_a11y_why), perms.accessibility, required = false) { open(Permissions.accessibilityIntent()) }
    PermissionRow(stringResource(R.string.perm_calendar_title), stringResource(R.string.perm_calendar_why), perms.calendar, required = false) { actions.requestCalendar() }
    PermissionRow(stringResource(R.string.perm_battery_title), stringResource(R.string.perm_battery_why), perms.battery, required = false) { open(Permissions.batteryIntent(context)) }

    Text(stringResource(R.string.onb_backup_note), color = Muted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    Button(onClick = onContinue, enabled = perms.requiredOk, modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.onb_continue), fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun PermissionRow(title: String, why: String, granted: Boolean, required: Boolean, onGrant: () -> Unit) {
  SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
          if (required) stringResource(R.string.onb_required) else stringResource(R.string.onb_optional),
          color = if (required) MaterialTheme.colorScheme.primary else Muted,
          style = MaterialTheme.typography.labelSmall,
        )
        Text(why, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
      }
      Spacer(Modifier.width(10.dp))
      if (granted) Text(stringResource(R.string.onb_granted), color = Mint, fontWeight = FontWeight.SemiBold)
      else OutlinedButton(onClick = onGrant) { Text(stringResource(R.string.onb_grant)) }
    }
  }
}

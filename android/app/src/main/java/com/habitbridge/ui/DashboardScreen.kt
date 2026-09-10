package com.habitbridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitbridge.HabitBridgeApp
import com.habitbridge.R
import com.habitbridge.data.SwapLogEntity
import com.habitbridge.policy.ActionTaken
import com.habitbridge.policy.Policy
import com.habitbridge.policy.Summary
import com.habitbridge.policy.SwapType
import com.habitbridge.ui.theme.Amber
import com.habitbridge.ui.theme.Edge
import com.habitbridge.ui.theme.Mint
import com.habitbridge.ui.theme.Muted
import com.habitbridge.ui.theme.Red
import com.habitbridge.util.InstalledApps
import com.habitbridge.util.ShareCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(app: HabitBridgeApp, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val now = remember { System.currentTimeMillis() }
  val stats by app.repository.stats(now).collectAsStateWithLifecycle(initialValue = null)
  val logs by app.repository.recentLogs(now).collectAsStateWithLifecycle(initialValue = emptyList())
  val scope = rememberCoroutineScope()
  val st = stats

  LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { ScreenTitle(stringResource(R.string.app_name)) }
    item {
      SectionCard(stringResource(R.string.dash_focus)) {
        if (st == null) { CircularProgressIndicator(color = Mint); return@SectionCard }
        Row(verticalAlignment = Alignment.CenterVertically) {
          val score = st.focusScore
          Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { (score ?: 0) / 100f }, modifier = Modifier.size(96.dp), color = when { score == null -> Edge; score >= 70 -> Mint; score >= 40 -> Amber; else -> Red }, trackColor = Edge, strokeWidth = 6.dp)
            Text(score?.toString() ?: "–", fontFamily = FontFamily.Monospace, fontSize = 30.sp, fontWeight = FontWeight.Bold)
          }
          Spacer(Modifier.width(16.dp))
          Column(Modifier.weight(1f)) {
            val ratio = st.week.swapRatio
            LinearProgressIndicator(progress = { (ratio ?: 0.0).toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Mint, trackColor = Edge)
            Spacer(Modifier.height(6.dp))
            Text(
              (ratio?.let { stringResource(R.string.dash_swap_ratio, Math.round(it * 100).toInt()) } ?: stringResource(R.string.dash_no_data)) + " · " + stringResource(R.string.dash_streak, st.streakDays),
              color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            )
            Text(stringResource(R.string.dash_focus_formula), color = Muted, style = MaterialTheme.typography.labelSmall)
          }
        }
        Button(onClick = {
          scope.launch {
            val bmp = withContext(Dispatchers.Default) { ShareCard.render(context, st, Policy.dateKey(now, app.repository.zone)) }
            ShareCard.share(context, bmp)
          }
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dash_share_card)) }
      }
    }
    if (st != null) {
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Box(Modifier.weight(1f)) { SummaryCard(stringResource(R.string.dash_today), st.today) }
          Box(Modifier.weight(1f)) { SummaryCard(stringResource(R.string.dash_week), st.week) }
        }
      }
      item {
        SectionCard(stringResource(R.string.dash_week)) {
          SwapType.entries.forEach { type ->
            StatRow(swapLabel(type), (st.week.bySwapType[type] ?: 0).toString())
          }
        }
      }
    }
    item { Text(stringResource(R.string.dash_recent).uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted) }
    if (logs.isEmpty()) item { Text(stringResource(R.string.dash_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(logs.take(30), key = { it.logId }) { LogRow(it, app.repository.zone) }
  }
}

@Composable
private fun SummaryCard(title: String, s: Summary) {
  SectionCard(title) {
    StatRow(stringResource(R.string.dash_swaps), s.swaps.toString())
    StatRow(stringResource(R.string.dash_exits), s.exits.toString())
    StatRow(stringResource(R.string.dash_bypasses), s.bypasses.toString(), highlight = false)
    StatRow(stringResource(R.string.dash_saved), stringResource(R.string.dash_minutes, s.savedMinutes), highlight = true)
  }
}

@Composable
fun swapLabel(type: SwapType): String = when (type) {
  SwapType.STRETCH -> stringResource(R.string.swap_stretch)
  SwapType.VOCAB -> stringResource(R.string.swap_vocab)
  SwapType.QUICK_NOTE -> stringResource(R.string.swap_note)
  SwapType.EMOTION -> stringResource(R.string.swap_emotion)
}

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
private fun LogRow(log: SwapLogEntity, zone: ZoneId) {
  val context = LocalContext.current
  val time = remember(log.timestamp) { timeFmt.format(Instant.ofEpochMilli(log.timestamp).atZone(zone)) }
  val appName = remember(log.targetPackage) { InstalledApps.label(context, log.targetPackage) }
  val what = when (log.actionTaken) {
    ActionTaken.SWAPPED -> stringResource(R.string.action_swapped) + (log.swappedType?.let { " · " + swapLabel(it) } ?: "")
    ActionTaken.BYPASSED -> stringResource(R.string.action_bypassed)
    ActionTaken.DISMISSED_HOME -> stringResource(R.string.action_dismissed_home)
  }
  Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(time, fontFamily = FontFamily.Monospace, color = Muted, style = MaterialTheme.typography.bodySmall)
    Text(appName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    Text(what, color = when (log.actionTaken) { ActionTaken.BYPASSED -> Amber; ActionTaken.SWAPPED -> Mint; else -> MaterialTheme.colorScheme.onSurface }, style = MaterialTheme.typography.bodySmall)
    log.shieldApplied?.let { Text(it.name, fontFamily = FontFamily.Monospace, color = Muted, style = MaterialTheme.typography.labelSmall) }
  }
}

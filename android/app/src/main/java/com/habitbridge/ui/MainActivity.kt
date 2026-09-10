package com.habitbridge.ui

import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.habitbridge.HabitBridgeApp
import com.habitbridge.R
import com.habitbridge.detect.DetectionService
import com.habitbridge.ui.theme.HabitBridgeTheme
import com.habitbridge.util.Permissions
import kotlinx.coroutines.launch

/** State of every permission the app cares about, refreshed on each resume. */
data class PermissionState(
  val usage: Boolean,
  val overlay: Boolean,
  val notifications: Boolean,
  val accessibility: Boolean,
  val calendar: Boolean,
  val battery: Boolean,
  val serviceRunning: Boolean,
) {
  val requiredOk: Boolean get() = usage && overlay
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { HabitBridgeTheme { AppRoot() } }
  }
}

enum class Tab(val labelRes: Int, val iconRes: Int) {
  DASHBOARD(R.string.nav_dashboard, R.drawable.ic_tab_dashboard),
  TARGETS(R.string.nav_targets, R.drawable.ic_tab_targets),
  NOTES(R.string.nav_notes, R.drawable.ic_tab_notes),
  SETTINGS(R.string.nav_settings, R.drawable.ic_tab_settings),
}

@Composable
fun rememberPermissionState(): Pair<PermissionState, () -> Unit> {
  val context = LocalContext.current
  var tick by remember { mutableIntStateOf(0) }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  val state = remember(tick) {
    PermissionState(
      usage = Permissions.hasUsageAccess(context),
      overlay = Permissions.canDrawOverlays(context),
      notifications = Permissions.hasNotifications(context),
      accessibility = Permissions.isAccessibilityEnabled(context),
      calendar = Permissions.hasCalendar(context),
      battery = Permissions.ignoringBatteryOptimizations(context),
      serviceRunning = DetectionService.isRunning(context),
    )
  }
  return state to { tick++ }
}

@Composable
fun AppRoot() {
  val context = LocalContext.current
  val app = HabitBridgeApp.of(context)
  val settings by app.repository.settings.collectAsStateWithLifecycle(initialValue = null)
  val (perms, refresh) = rememberPermissionState()
  val scope = rememberCoroutineScope()
  var forceOnboarding by rememberSaveable { mutableStateOf(false) }

  val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
  val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    refresh()
    if (granted) scope.launch { app.repository.updateSettings { it.copy(calendarEnabled = true) } }
  }
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri: Uri? ->
    pendingExport?.let { content -> if (uri != null) com.habitbridge.util.NotesExporter.writeTo(context, uri, content) }
    pendingExport = null
  }

  val actions = remember(perms) {
    PermissionActions(
      requestNotifications = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
      requestCalendar = { calendarLauncher.launch(Manifest.permission.READ_CALENDAR) },
      startService = { DetectionService.start(context); refresh() },
      stopService = { DetectionService.stop(context); refresh() },
      refresh = refresh,
    )
  }

  val s = settings ?: return
  val showOnboarding = forceOnboarding || !s.onboardingDone || !perms.requiredOk
  if (showOnboarding) {
    OnboardingScreen(perms, actions, onContinue = {
      scope.launch { app.repository.updateSettings { it.copy(onboardingDone = true) } }
      DetectionService.start(context)
      forceOnboarding = false
      refresh()
    })
    return
  }

  var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
  Scaffold(
    bottomBar = {
      NavigationBar {
        Tab.entries.forEach { t ->
          NavigationBarItem(
            selected = tab == t,
            onClick = { tab = t },
            icon = { Icon(painterResource(t.iconRes), contentDescription = null) },
            label = { Text(stringResource(t.labelRes)) },
          )
        }
      }
    },
  ) { padding ->
    val modifier = Modifier.padding(padding)
    when (tab) {
      Tab.DASHBOARD -> DashboardScreen(app, modifier)
      Tab.TARGETS -> TargetsScreen(app, modifier)
      Tab.NOTES -> NotesScreen(app, modifier, onSafExport = { name, content -> pendingExport = content; exportLauncher.launch(name) })
      Tab.SETTINGS -> SettingsScreen(app, perms, actions, modifier, onOpenOnboarding = { forceOnboarding = true })
    }
  }
}

/** Content waiting for the SAF document picker (API < 29 export path). */
private var pendingExport: String? = null

data class PermissionActions(
  val requestNotifications: () -> Unit,
  val requestCalendar: () -> Unit,
  val startService: () -> Unit,
  val stopService: () -> Unit,
  val refresh: () -> Unit,
)

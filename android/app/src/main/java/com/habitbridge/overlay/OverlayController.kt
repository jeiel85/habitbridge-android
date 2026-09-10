package com.habitbridge.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.habitbridge.R
import com.habitbridge.data.HabitRepository
import com.habitbridge.data.VocabEntity
import com.habitbridge.detect.HabitAccessibilityService
import com.habitbridge.policy.ActionTaken
import com.habitbridge.policy.Decision
import com.habitbridge.policy.Emotion
import com.habitbridge.policy.SwapType
import com.habitbridge.ui.theme.HabitBridgeTheme
import com.habitbridge.util.InstalledApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface OverlayScreen {
  data object Choose : OverlayScreen
  data object Stretch : OverlayScreen
  data class Vocab(val entity: VocabEntity?, val flipped: Boolean) : OverlayScreen
  data object Note : OverlayScreen
  data object Emotion : OverlayScreen
}

data class OverlayUiState(
  val packageName: String,
  val appName: String,
  val decision: Decision.Intercept,
  /** SystemClock.elapsedRealtime() when the hold started. */
  val holdStartElapsed: Long,
  val screen: OverlayScreen,
  val cooldownMinutes: Int,
  val savedMinutesPerAvoid: Int,
)

/**
 * Owns the single interceptor window (PRD §6.2) and the state machine behind it
 * (§4.4–§4.6). All window operations happen on the main thread.
 */
class OverlayController(context: Context, private val repo: HabitRepository, private val scope: CoroutineScope) {
  private val appContext = context.applicationContext
  private val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private val main = Handler(Looper.getMainLooper())
  private var view: ComposeView? = null
  private var owner: OverlayLifecycleOwner? = null

  private val _state = MutableStateFlow<OverlayUiState?>(null)
  val state: StateFlow<OverlayUiState?> = _state

  val isShowing: Boolean get() = _state.value != null
  val currentPackage: String? get() = _state.value?.packageName

  fun show(pkg: String, decision: Decision.Intercept, now: Long) {
    scope.launch {
      val settings = repo.settingsEntity()
      val appName = InstalledApps.label(appContext, pkg)
      withContext(Dispatchers.Main) {
        _state.value = OverlayUiState(
          packageName = pkg,
          appName = appName,
          decision = decision,
          holdStartElapsed = SystemClock.elapsedRealtime(),
          screen = OverlayScreen.Choose,
          cooldownMinutes = settings.cooldownMinutes,
          savedMinutesPerAvoid = settings.savedMinutesPerAvoid,
        )
        if (view == null) attachWindow()
        Log.d(TAG, "show $pkg ${decision.mode}/${decision.shield} at $now")
      }
    }
  }

  private fun attachWindow() {
    val lifecycleOwner = OverlayLifecycleOwner().also { it.onCreate() }
    val composeView = ComposeView(appContext).apply {
      lifecycleOwner.attachTo(this)
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent { HabitBridgeTheme { InterceptorRoot(this@OverlayController) } }
    }
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      // Focusable (no FLAG_NOT_TOUCH_MODAL / FLAG_NOT_FOCUSABLE): touches never reach the app below
      // and the quick-note field can receive the keyboard (PRD §6.2).
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
    }
    try {
      wm.addView(composeView, params)
      view = composeView
      owner = lifecycleOwner
      lifecycleOwner.onResume()
    } catch (t: Throwable) {
      Log.e(TAG, "addView failed (overlay permission?)", t)
      lifecycleOwner.onDestroy()
      _state.value = null
    }
  }

  fun hide() {
    main.post {
      view?.let { v -> runCatching { wm.removeViewImmediate(v) }.onFailure { Log.w(TAG, "removeView failed", it) } }
      owner?.onDestroy()
      view = null
      owner = null
      _state.value = null
    }
  }

  // -------------------------------------------------------------------------
  // Actions from the UI
  // -------------------------------------------------------------------------

  fun chooseSwap(type: SwapType) {
    val s = _state.value ?: return
    when (type) {
      SwapType.STRETCH -> _state.value = s.copy(screen = OverlayScreen.Stretch)
      SwapType.QUICK_NOTE -> _state.value = s.copy(screen = OverlayScreen.Note)
      SwapType.EMOTION -> _state.value = s.copy(screen = OverlayScreen.Emotion)
      SwapType.VOCAB -> {
        _state.value = s.copy(screen = OverlayScreen.Vocab(null, false))
        scope.launch {
          val v = repo.nextVocab()
          withContext(Dispatchers.Main) { updateVocab { OverlayScreen.Vocab(v, false) } }
        }
      }
    }
  }

  fun backToChoose() {
    _state.value = _state.value?.copy(screen = OverlayScreen.Choose)
  }

  fun flipVocab() = updateVocab { it.copy(flipped = true) }

  fun nextVocab() {
    val current = (_state.value?.screen as? OverlayScreen.Vocab)?.entity
    scope.launch {
      val v = repo.nextVocab(excludeId = current?.id)
      withContext(Dispatchers.Main) { updateVocab { OverlayScreen.Vocab(v, false) } }
    }
  }

  private fun updateVocab(transform: (OverlayScreen.Vocab) -> OverlayScreen.Vocab) {
    val s = _state.value ?: return
    val screen = s.screen as? OverlayScreen.Vocab ?: return
    _state.value = s.copy(screen = transform(screen))
  }

  fun exitHome() = finish(ActionTaken.DISMISSED_HOME, goHome = true)

  fun bypass() {
    val s = _state.value ?: return
    if (!s.decision.canBypass) return
    if (SystemClock.elapsedRealtime() < s.holdStartElapsed + s.decision.holdSeconds * 1000L) return
    finish(ActionTaken.BYPASSED, goHome = false)
  }

  fun completeSwap(type: SwapType, note: String? = null, emotion: Emotion? = null) {
    val s = _state.value ?: return
    if (type == SwapType.VOCAB) (s.screen as? OverlayScreen.Vocab)?.entity?.let { v -> scope.launch { repo.markVocabSeen(v.id) } }
    finish(ActionTaken.SWAPPED, goHome = true, swapType = type, note = note, emotion = emotion)
  }

  /** The user left the covered app on their own (home/recents). Log it and drop the window. */
  fun dismissAsHome() {
    val s = _state.value ?: return
    _state.value = null
    scope.launch { runCatching { repo.record(s.packageName, ActionTaken.DISMISSED_HOME, System.currentTimeMillis(), shield = s.decision.shield, mode = s.decision.mode) } }
    hide()
  }

  private fun finish(action: ActionTaken, goHome: Boolean, swapType: SwapType? = null, note: String? = null, emotion: Emotion? = null) {
    val s = _state.value ?: return
    _state.value = null
    scope.launch {
      runCatching {
        repo.record(s.packageName, action, System.currentTimeMillis(), swapType, emotion, note, s.decision.shield, s.decision.mode)
      }.onFailure { Log.w(TAG, "record failed", it) }
      withContext(Dispatchers.Main) {
        if (goHome) goHomeNow()
        if (action == ActionTaken.SWAPPED) {
          Toast.makeText(appContext, appContext.getString(R.string.toast_swapped, s.savedMinutesPerAvoid), Toast.LENGTH_SHORT).show()
        }
        hide()
      }
    }
  }

  private fun goHomeNow() {
    if (HabitAccessibilityService.goHome()) return
    try {
      appContext.startActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      )
    } catch (t: Throwable) {
      Log.w(TAG, "home intent failed", t)
      Toast.makeText(appContext, R.string.toast_home_failed, Toast.LENGTH_LONG).show()
    }
  }

  companion object {
    private const val TAG = "HabitBridge/Overlay"
  }
}

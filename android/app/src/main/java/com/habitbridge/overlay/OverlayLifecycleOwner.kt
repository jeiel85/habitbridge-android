package com.habitbridge.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Minimal lifecycle plumbing so a ComposeView can live in a WindowManager window owned by a Service. */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
  private val registry = LifecycleRegistry(this)
  private val savedStateController = SavedStateRegistryController.create(this)
  private val store = ViewModelStore()

  override val lifecycle: Lifecycle get() = registry
  override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
  override val viewModelStore: ViewModelStore get() = store

  fun onCreate() {
    savedStateController.performAttach()
    savedStateController.performRestore(null)
    registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
  }

  fun onResume() {
    registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  fun onDestroy() {
    registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    store.clear()
  }

  fun attachTo(view: View) {
    view.setViewTreeLifecycleOwner(this)
    view.setViewTreeSavedStateRegistryOwner(this)
    view.setViewTreeViewModelStoreOwner(this)
  }
}

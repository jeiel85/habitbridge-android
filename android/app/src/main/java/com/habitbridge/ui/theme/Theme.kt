package com.habitbridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Mint = Color(0xFF34D399)
val MintDim = Color(0xFF1F8F6A)
val Bg = Color(0xFF0B0F0E)
val Panel = Color(0xFF121A18)
val Edge = Color(0xFF1F2B28)
val Ink = Color(0xFFE6F4EF)
val InkDim = Color(0xFFA7BDB4)
val Muted = Color(0xFF7C9A8E)
val Amber = Color(0xFFFBBF24)
val Red = Color(0xFFF87171)

private val Scheme = darkColorScheme(
  primary = Mint,
  onPrimary = Color(0xFF062A1F),
  secondary = MintDim,
  background = Bg,
  onBackground = Ink,
  surface = Panel,
  onSurface = Ink,
  surfaceVariant = Color(0xFF101615),
  onSurfaceVariant = InkDim,
  outline = Edge,
  error = Red,
)

/** Dark-only Material3 theme (PRD §7): monospace "terminal" is used only on the interceptor and the card. */
@Composable
fun HabitBridgeTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = Scheme) {
    Surface(color = Scheme.background, contentColor = Scheme.onBackground, content = content)
  }
}

package com.habitbridge.overlay

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitbridge.R
import com.habitbridge.policy.ContextMode
import com.habitbridge.policy.Emotion
import com.habitbridge.policy.SwapType
import kotlinx.coroutines.delay

private val TermBg = Color(0xFF060907)
private val TermPanel = Color(0xFF0C1411)
private val TermEdge = Color(0xFF2A4A40)
private val Mint = Color(0xFF34D399)
private val Amber = Color(0xFFFBBF24)
private val Red = Color(0xFFF87171)
private val Ink = Color(0xFFDFF5EA)
private val Muted = Color(0xFF7C9A8E)

@Composable
fun InterceptorRoot(controller: OverlayController) {
  val state by controller.state.collectAsState()
  val s = state ?: return
  Box(Modifier.fillMaxSize().background(TermBg).systemBarsPadding()) {
    when (val screen = s.screen) {
      OverlayScreen.Choose -> ChooseScreen(s, controller)
      OverlayScreen.Stretch -> SwapFrame(stringResource(R.string.swap_stretch), controller) { StretchContent(controller) }
      is OverlayScreen.Vocab -> SwapFrame(stringResource(R.string.swap_vocab), controller) { VocabContent(screen, controller) }
      OverlayScreen.Note -> SwapFrame(stringResource(R.string.swap_note), controller) { NoteContent(controller) }
      OverlayScreen.Emotion -> SwapFrame(stringResource(R.string.swap_emotion), controller) { EmotionContent(controller) }
    }
  }
}

@Composable
private fun modeLabel(mode: ContextMode): String = when (mode) {
  ContextMode.SLEEP -> stringResource(R.string.mode_sleep)
  ContextMode.MEETING -> stringResource(R.string.mode_meeting)
  ContextMode.WORK -> stringResource(R.string.mode_work)
  ContextMode.FREE -> stringResource(R.string.mode_free)
  ContextMode.QUOTA -> stringResource(R.string.mode_quota)
}

@Composable
private fun Mono(text: String, color: Color = Ink, size: Int = 13, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, align: TextAlign? = null, italic: Boolean = false) {
  Text(text, color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace, fontWeight = weight, modifier = modifier, textAlign = align, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, lineHeight = (size + 6).sp)
}

@Composable
private fun ChooseScreen(s: OverlayUiState, c: OverlayController) {
  val d = s.decision
  var remaining by remember(s.holdStartElapsed) { mutableIntStateOf(d.holdSeconds) }
  LaunchedEffect(s.holdStartElapsed) {
    while (true) {
      val leftMs = s.holdStartElapsed + d.holdSeconds * 1000L - SystemClock.elapsedRealtime()
      remaining = ((leftMs + 999) / 1000).toInt().coerceAtLeast(0)
      if (remaining <= 0) break
      delay(200)
    }
  }
  val done = remaining <= 0
  Column(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxWidth().background(Color(0xFF0D1512)).padding(horizontal = 14.dp, vertical = 10.dp)) {
      Mono(stringResource(R.string.ic_header), color = Mint, size = 12)
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
      Mono(stringResource(R.string.ic_title), color = Amber, size = 14, weight = FontWeight.Bold)
      Spacer(Modifier.height(10.dp))
      Mono("${stringResource(R.string.ic_target)}: ${s.packageName}", color = Color(0xFF9FC5B5), size = 12)
      Mono("${stringResource(R.string.ic_mode)}  : ${modeLabel(d.mode)} (${d.shield.name} Shield)", color = Color(0xFF9FC5B5), size = 12)
      Spacer(Modifier.height(12.dp))
      Mono("> " + stringResource(R.string.ic_quote), color = Color(0xFFE6F4EF), size = 14, italic = true)
      Spacer(Modifier.height(12.dp))
      Box(Modifier.fillMaxWidth().border(1.dp, TermEdge, RoundedCornerShape(4.dp))) {
        val progress = if (d.holdSeconds == 0) 1f else (1f - remaining.toFloat() / d.holdSeconds).coerceIn(0f, 1f)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(34.dp), color = Mint.copy(alpha = 0.18f), trackColor = Color.Transparent)
        Mono(
          if (done) "[ ${stringResource(R.string.ic_hold_done)} ]" else "[ 00:%02ds %s ]".format(remaining, stringResource(R.string.ic_hold)),
          color = if (done) Mint else Amber,
          size = 12,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
      }
      Spacer(Modifier.height(14.dp))
      Mono(stringResource(R.string.ic_choose), color = Muted, size = 12)
      Spacer(Modifier.height(6.dp))
      SwapButton(1, stringResource(R.string.swap_stretch), stringResource(R.string.swap_stretch_desc), Color(0xFF34D399)) { c.chooseSwap(SwapType.STRETCH) }
      SwapButton(2, stringResource(R.string.swap_vocab), stringResource(R.string.swap_vocab_desc), Color(0xFF60A5FA)) { c.chooseSwap(SwapType.VOCAB) }
      SwapButton(3, stringResource(R.string.swap_note), stringResource(R.string.swap_note_desc), Color(0xFFFBBF24)) { c.chooseSwap(SwapType.QUICK_NOTE) }
      SwapButton(4, stringResource(R.string.swap_emotion), stringResource(R.string.swap_emotion_desc), Color(0xFFF472B6)) { c.chooseSwap(SwapType.EMOTION) }
      Spacer(Modifier.height(10.dp))
      d.quotaLeftMinutes?.let { left ->
        if (left <= 0) Mono(stringResource(R.string.ic_quota_out), color = Red, size = 12)
        else Mono(stringResource(R.string.ic_quota_left, left), color = Amber, size = 12)
      }
      if (d.canBypass) Mono(stringResource(R.string.ic_cooldown_note, s.cooldownMinutes), color = Muted, size = 11)
    }
    Row(Modifier.fillMaxWidth().background(Color(0xFF080C0A)).padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TermButton(stringResource(R.string.ic_exit), Modifier.weight(1f)) { c.exitHome() }
      when {
        !d.canBypass -> TermButton(stringResource(R.string.ic_continue_locked, modeLabel(d.mode)), Modifier.weight(1f), enabled = false, locked = true) {}
        !done -> TermButton(stringResource(R.string.ic_continue_wait, remaining), Modifier.weight(1f), enabled = false) {}
        else -> TermButton(stringResource(R.string.ic_continue), Modifier.weight(1f)) { c.bypass() }
      }
    }
  }
}

@Composable
private fun SwapButton(n: Int, title: String, desc: String, accent: Color, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(6.dp)).background(TermPanel)
      .border(1.dp, Color(0xFF1F3A32), RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Mono("[$n]", color = Mint, size = 13, modifier = Modifier.width(30.dp))
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(accent))
    Spacer(Modifier.width(10.dp))
    Column { Mono(title, size = 13, weight = FontWeight.Bold); Mono(desc, color = Muted, size = 10) }
  }
}

@Composable
private fun TermButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, locked: Boolean = false, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = RoundedCornerShape(6.dp),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink, disabledContentColor = if (locked) Color(0xFFB47C7C) else Muted),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 10.dp),
  ) {
    Mono(text, color = if (!enabled) (if (locked) Color(0xFFB47C7C) else Muted) else Ink, size = 11, align = TextAlign.Center)
  }
}

@Composable
private fun SwapFrame(title: String, c: OverlayController, content: @Composable () -> Unit) {
  Column(Modifier.fillMaxSize().background(Color(0xFF0D1210))) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
      TextButton(onClick = { c.backToChoose() }) { Mono(stringResource(R.string.ic_back), color = Muted, size = 12) }
      Mono(title, color = Ink, size = 13, modifier = Modifier.padding(end = 12.dp))
    }
    Column(Modifier.weight(1f).fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
  }
}

private val STRETCH_STEPS = listOf(
  10 to R.string.stretch_breathe,
  10 to R.string.stretch_neck_left,
  10 to R.string.stretch_neck_right,
  10 to R.string.stretch_shoulders,
  10 to R.string.stretch_chest,
  10 to R.string.stretch_eyes,
)

@Composable
private fun StretchContent(c: OverlayController) {
  val start = remember { SystemClock.elapsedRealtime() }
  var elapsed by remember { mutableIntStateOf(0) }
  val total = STRETCH_STEPS.sumOf { it.first }
  LaunchedEffect(Unit) {
    while (elapsed < total) {
      elapsed = ((SystemClock.elapsedRealtime() - start) / 1000).toInt().coerceAtMost(total)
      delay(250)
    }
  }
  if (elapsed >= total) {
    Text("✓", color = Mint, fontSize = 56.sp)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.stretch_done), color = Ink, fontSize = 18.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(18.dp))
    Button(onClick = { c.completeSwap(SwapType.STRETCH) }, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF062A1F))) {
      Text(stringResource(R.string.stretch_finish), fontWeight = FontWeight.Bold)
    }
    return
  }
  var acc = 0
  var idx = 0
  var stepLeft = 0
  for ((i, step) in STRETCH_STEPS.withIndex()) {
    if (elapsed < acc + step.first) { idx = i; stepLeft = acc + step.first - elapsed; break }
    acc += step.first
  }
  Box(contentAlignment = Alignment.Center) {
    CircularProgressIndicator(progress = { elapsed.toFloat() / total }, modifier = Modifier.size(150.dp), color = Mint, trackColor = Color(0xFF1F2B28), strokeWidth = 10.dp)
    Mono(stepLeft.toString(), size = 40, weight = FontWeight.Bold)
  }
  Spacer(Modifier.height(18.dp))
  Text(stringResource(STRETCH_STEPS[idx].second), color = Ink, fontSize = 17.sp, textAlign = TextAlign.Center)
  Spacer(Modifier.height(6.dp))
  Mono("${idx + 1}/${STRETCH_STEPS.size}", color = Muted, size = 12)
  Spacer(Modifier.height(24.dp))
  TextButton(onClick = { c.backToChoose() }) { Text(stringResource(R.string.stretch_skip), color = Muted) }
}

@Composable
private fun VocabContent(screen: OverlayScreen.Vocab, c: OverlayController) {
  val v = screen.entity
  if (v == null) { CircularProgressIndicator(color = Mint); return }
  Box(
    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(14.dp))
      .background(if (screen.flipped) Color(0xFF1D3A5A) else Color(0xFF16302A))
      .border(1.dp, Color(0xFF1F2B28), RoundedCornerShape(14.dp))
      .clickable { c.flipVocab() }.padding(16.dp),
    contentAlignment = Alignment.Center,
  ) {
    if (!screen.flipped) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v.word, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.vocab_flip), color = Muted, fontSize = 13.sp)
      }
    } else {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v.meaning, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(v.example, color = Color(0xFFA7BDB4), fontSize = 13.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
      }
    }
  }
  Spacer(Modifier.height(18.dp))
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    OutlinedButton(onClick = { c.nextVocab() }) { Text(stringResource(R.string.vocab_next), color = Muted) }
    Button(onClick = { c.completeSwap(SwapType.VOCAB) }, enabled = screen.flipped, colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF062A1F))) {
      Text(stringResource(R.string.vocab_got), fontWeight = FontWeight.Bold)
    }
  }
}

@Composable
private fun NoteContent(c: OverlayController) {
  var text by remember { mutableStateOf("") }
  OutlinedTextField(
    value = text,
    onValueChange = { if (it.length <= 280) text = it },
    modifier = Modifier.fillMaxWidth(),
    placeholder = { Text(stringResource(R.string.note_placeholder), color = Muted) },
    minLines = 3,
    maxLines = 6,
  )
  Spacer(Modifier.height(6.dp))
  Text(stringResource(R.string.note_hint), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
  Spacer(Modifier.height(14.dp))
  Button(
    onClick = { c.completeSwap(SwapType.QUICK_NOTE, note = text) },
    enabled = text.isNotBlank(),
    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF062A1F)),
  ) { Text(stringResource(R.string.note_save), fontWeight = FontWeight.Bold) }
}

@Composable
private fun EmotionContent(c: OverlayController) {
  Text(stringResource(R.string.emotion_question), color = Ink, fontSize = 18.sp, textAlign = TextAlign.Center)
  Spacer(Modifier.height(16.dp))
  val items = listOf(
    Emotion.BORED to R.string.emotion_bored,
    Emotion.ANXIOUS to R.string.emotion_anxious,
    Emotion.LONELY to R.string.emotion_lonely,
    Emotion.HABIT to R.string.emotion_habit,
    Emotion.TIRED to R.string.emotion_tired,
  )
  items.chunked(2).forEach { row ->
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      row.forEach { (e, label) ->
        OutlinedButton(onClick = { c.completeSwap(SwapType.EMOTION, emotion = e) }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(12.dp)) {
          Text(stringResource(label), color = Ink)
        }
      }
      if (row.size == 1) Spacer(Modifier.weight(1f))
    }
  }
}

package com.habitbridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitbridge.ui.theme.Muted
import com.habitbridge.ui.theme.Panel

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (title != null) Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Muted, letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp))
      content()
    }
  }
}

@Composable
fun StatRow(label: String, value: String, highlight: Boolean = false) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
fun ScreenTitle(text: String) {
  Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
}

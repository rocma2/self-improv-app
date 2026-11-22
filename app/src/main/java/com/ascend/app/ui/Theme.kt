package com.ascend.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val bg = Color(0xFF0B0E1A)
    val surface = Color(0xFF151A2E)
    val surface2 = Color(0xFF1E2540)
    val violet = Color(0xFF7C5CFF)
    val cyan = Color(0xFF22D3EE)
    val gold = Color(0xFFF5B83D)
    val hp = Color(0xFFF43F5E)
    val green = Color(0xFF34D399)
    val text = Color(0xFFE6E8F2)
    val muted = Color(0xFF9AA3C0)
    val track = Color(0xFF2A3150)
}

private val scheme = darkColorScheme(
    primary = C.violet,
    onPrimary = Color.White,
    secondary = C.cyan,
    tertiary = C.gold,
    background = C.bg,
    onBackground = C.text,
    surface = C.surface,
    onSurface = C.text,
    surfaceVariant = C.surface2,
    onSurfaceVariant = C.muted,
    surfaceContainer = C.surface,
    surfaceContainerHigh = C.surface2,
    surfaceContainerLow = C.surface,
    error = C.hp,
    outline = C.track,
)

@Composable
fun AscendTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AppCard(modifier: Modifier = Modifier, border: Color? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = C.surface),
        border = border?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(top = 8.dp, bottom = 4.dp), color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 8) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50)).background(C.track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

@Composable
fun LabeledBar(label: String, value: String, fraction: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = C.muted)
            Text(value, fontSize = 12.sp, color = C.text)
        }
        Spacer(Modifier.height(3.dp))
        Bar(fraction, color)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** A big number with − and + buttons. */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = C.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onMinus, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.width(44.dp)) { Text("−", fontSize = 18.sp) }
            Text(value, modifier = Modifier.width(88.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            OutlinedButton(onClick = onPlus, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp), modifier = Modifier.width(44.dp)) { Text("+", fontSize = 18.sp) }
        }
    }
}

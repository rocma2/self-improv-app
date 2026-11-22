@file:OptIn(ExperimentalLayoutApi::class)

package com.ascend.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.data.Repository
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Leveling
import com.ascend.app.domain.Skill
import com.ascend.app.domain.SkillRadar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radar chart ("pentagon" for 5 values, octagon for 8). [values] are 0..1.
 * [labels] can have two lines separated by \n. Tapping picks the nearest axis.
 */
@Composable
fun RadarChart(values: List<Float>, labels: List<String>, color: Color, modifier: Modifier = Modifier, onTap: ((Int) -> Unit)? = null) {
    val n = values.size
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val textPx = with(LocalDensity.current) { 13.sp.toPx() }
    val tap = if (onTap == null) Modifier else Modifier.pointerInput(n) {
        detectTapGestures { p ->
            val deg = (Math.toDegrees(atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())) + 90 + 360) % 360
            onTap(((deg / (360.0 / n)) + 0.5).toInt() % n)
        }
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(1f).then(tap)) {
        if (n < 3) return@Canvas
        val c = center
        val r = size.minDimension / 2f * 0.66f
        fun pt(i: Int, f: Float): Offset {
            val a = Math.toRadians(-90.0 + 360.0 * i / n)
            return Offset(c.x + r * f * cos(a).toFloat(), c.y + r * f * sin(a).toFloat())
        }
        fun poly(f: (Int) -> Float) = Path().apply {
            for (i in 0 until n) { val p = pt(i, f(i)); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            close()
        }
        for (ring in 1..4) drawPath(poly { ring / 4f }, C.track, style = Stroke(width = if (ring == 4) 3f else 1.5f))
        for (i in 0 until n) drawLine(C.track, c, pt(i, 1f), strokeWidth = 1.5f)
        val shape = poly { values[it].coerceIn(0.03f, 1f) * grow.value }
        drawPath(shape, color.copy(alpha = 0.28f))
        drawPath(shape, color, style = Stroke(width = 5f))
        for (i in 0 until n) drawCircle(color, 7f, pt(i, values[i].coerceIn(0.03f, 1f) * grow.value))
        val paint = android.graphics.Paint().apply {
            this.color = C.text.toArgb(); textSize = textPx; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            isFakeBoldText = true
        }
        labels.forEachIndexed { i, label ->
            val lines = label.split("\n")
            val p = pt(i, 1.32f)
            val top = p.y - (lines.size - 1) * textPx * 0.6f + textPx / 3
            lines.forEachIndexed { li, line ->
                paint.color = if (li == 0) C.text.toArgb() else C.cyan.toArgb()
                drawContext.canvas.nativeCanvas.drawText(line, p.x, top + li * textPx * 1.15f, paint)
            }
        }
    }
}

/** Status-style octagon of all 8 skills, with tiles to open each one. */
@Composable
fun SkillsRadarCard(state: AppState, onOpen: (Skill) -> Unit) {
    val overview = SkillRadar.overview(state)
    AppCard(title = "Skills") {
        RadarChart(overview.map { it.third }, overview.map { (s, l, _) -> "${s.emoji}\nLv $l" }, C.cyan, onTap = { onOpen(overview[it].first) })
        Text("Tap a skill to see its stats and what to work on.", fontSize = 12.sp, color = C.muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), maxItemsInEachRow = 4) {
            overview.forEach { (s, l, _) ->
                val hp = state.skill(s).hp
                Column(
                    Modifier.weight(1f).border(1.dp, (if (hp < 40) C.hp else C.blue).copy(alpha = 0.6f), CutCornerShape(6.dp))
                        .background(C.surface, CutCornerShape(6.dp)).clickable { onOpen(s) }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.emoji, fontSize = 18.sp)
                    Text("Lv $l", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Bar(hp / 100f, C.hp, height = 3)
                }
            }
        }
    }
}

/** One skill: level, its own pentagon of 5 sub-stats, and the one to improve next. */
@Composable
fun SkillDialog(state: AppState, skill: Skill, onDismiss: () -> Unit) {
    val subs = SkillRadar.subStats(state, skill, Repository.today())
    val weak = SkillRadar.weakest(subs)
    val sk = state.skill(skill)
    val info = Leveling.skillInfo(sk.xp)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        AppCard(title = "${skill.emoji} ${skill.label}") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SysText("Level ${info.level}", size = 22.sp, modifier = Modifier.fillMaxWidth())
                LabeledBar("XP", "${info.intoLevel} / ${info.levelSize}", info.fraction, C.cyan)
                LabeledBar("HP", "${sk.hp} / 100", sk.hp / 100f, C.hp)
                RadarChart(subs.map { it.score / 100f }, subs.map { "${it.name}\n${it.score}" }, C.violet)
                subs.forEach { st ->
                    Column(Modifier.padding(vertical = 3.dp)) {
                        Row {
                            Text(st.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("${st.score}", fontWeight = FontWeight.Bold, color = if (st == weak) C.gold else C.text)
                        }
                        Text(st.detail, fontSize = 12.sp, color = C.muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                AppCard(border = C.gold, title = "Improve next") {
                    Text(weak.name, fontWeight = FontWeight.Bold, color = C.gold)
                    Text(weak.tip, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = CutCornerShape(8.dp)) { Text("CLOSE") }
            }
        }
    }
}

/** Seven boxes, one claim a day. The track waits for you if you skip a day. */
@Composable
fun CheckInCard(state: AppState, onClaim: () -> Unit) {
    val idx = com.ascend.app.domain.Rpg.checkInIndex(state)
    AppCard(title = "Daily check-in", border = C.gold, pulse = true) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 7) {
                val color = when { i < idx -> C.green; i == idx -> C.gold; else -> C.muted }
                Column(
                    Modifier.weight(1f).border(1.dp, color.copy(alpha = 0.7f), CutCornerShape(4.dp)).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (i < idx) "✔" else if (i == 6) "🛡️" else if (i == 2) "🎁" else if (i == 4) "⚗️" else "🪙", fontSize = 14.sp)
                    Text("${i + 1}", fontSize = 11.sp, color = color)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onClaim, modifier = Modifier.fillMaxWidth(), shape = CutCornerShape(8.dp)) {
            Text("CLAIM DAY ${idx + 1}: ${com.ascend.app.domain.Rpg.CHECK_IN_REWARDS[idx]}")
        }
    }
}

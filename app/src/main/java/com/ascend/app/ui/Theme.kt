package com.ascend.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.R
import kotlin.math.cos
import kotlin.math.sin

/** "System window" look: near-black navy, electric blue glow, angular panels. */
object C {
    val bg = Color(0xFF04060E)
    val surface = Color(0xFF0A1226)
    val surface2 = Color(0xFF0F1B38)
    val violet = Color(0xFF2F7BFF) // primary electric blue (name kept so all screens follow)
    val blue = Color(0xFF2F7BFF)
    val cyan = Color(0xFF4FD8FF)
    val gold = Color(0xFFFFC94D)
    val hp = Color(0xFFFF3B5C)
    val green = Color(0xFF3CF0A0)
    val text = Color(0xFFEAF2FF)
    val muted = Color(0xFF8FA3C8)
    val track = Color(0xFF1A2747)
    val panelTop = Color(0xFF0D1A3A)
    val panelBottom = Color(0xFF070C1C)
    val purple = Color(0xFF9D85FF)
    // Frosted glass panel fill, see-through over the background glow.
    val glassTop = Color(0x2678AAFF)
    val glassBottom = Color(0x1478AAFF)
}

val Body = FontFamily(
    Font(R.font.rajdhani_medium, FontWeight.Normal),
    Font(R.font.rajdhani_medium, FontWeight.Medium),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
    Font(R.font.rajdhani_bold, FontWeight.ExtraBold),
    Font(R.font.rajdhani_bold, FontWeight.Black),
)
val Display = FontFamily(Font(R.font.orbitron))

private val scheme = darkColorScheme(
    primary = C.blue,
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
    secondaryContainer = C.blue.copy(alpha = 0.35f),
    onSecondaryContainer = C.text,
    error = C.hp,
    outline = C.blue.copy(alpha = 0.5f),
)

private fun Typography.withFont(f: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f), displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f), headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f), titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f, fontSize = 17.sp), bodyMedium = bodyMedium.copy(fontFamily = f, fontSize = 15.sp), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold, letterSpacing = 1.sp), labelMedium = labelMedium.copy(fontFamily = f), labelSmall = labelSmall.copy(fontFamily = f),
)

@Composable
fun AscendTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography().withFont(Body), content = content)
}

val PanelShape = RoundedCornerShape(22.dp)

/** Holo System backdrop: a deep blue glow from the top left fading into near black. */
val AppBackground = Brush.radialGradient(
    0f to Color(0xFF13306B), 0.55f to Color(0xFF060B1C), 1f to Color(0xFF03050C),
    center = Offset(150f, 0f), radius = 2200f,
)

/** Glowing text used for headings. */
fun glow(color: Color = C.blue, radius: Float = 18f) = Shadow(color = color, offset = Offset.Zero, blurRadius = radius)

@Composable
fun SysText(text: String, size: TextUnit = 18.sp, color: Color = C.text, glowColor: Color = C.blue, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = TextStyle(fontFamily = Display, fontSize = size, color = color, letterSpacing = 1.5.sp, shadow = glow(glowColor), fontWeight = FontWeight.Bold),
        textAlign = align,
    )
}

/** The "◆ NOTIFICATION" style tab that sits on top of a panel. */
@Composable
fun PanelHeader(title: String, color: Color = C.cyan) {
    Row(
        Modifier.border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("◆", color = color, fontSize = 11.sp)
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = TextStyle(fontFamily = Display, fontSize = 12.sp, color = C.text, letterSpacing = 2.sp, shadow = glow(color, 10f)))
    }
}

/**
 * Every card in the app is a frosted "holo" panel: rounded, see-through, thin glowing border.
 * [solid] puts a dark base under the glass for dialogs and toasts.
 * [border] tints the glow (red for penalties, green for done). [title] adds a header tab.
 */
@Composable
fun AppCard(modifier: Modifier = Modifier, border: Color? = null, title: String? = null, pulse: Boolean = false, solid: Boolean = false, content: @Composable () -> Unit) {
    val tint = border ?: C.blue
    val alpha by if (pulse) rememberInfiniteTransition(label = "pulse").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a",
    ) else animateFloatAsState(if (border != null) 0.9f else 0.55f, label = "b")
    Box(
        modifier.fillMaxWidth()
            .then(if (solid) Modifier.background(C.panelBottom, PanelShape) else Modifier)
            .background(Brush.verticalGradient(listOf(C.glassTop, C.glassBottom)), PanelShape)
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = alpha * 0.75f), tint.copy(alpha = alpha * 0.25f)))), PanelShape),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PanelHeader(title, if (border != null) tint else C.cyan) }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        "▸ " + text.uppercase(), modifier = modifier.padding(top = 10.dp, bottom = 2.dp),
        style = TextStyle(fontFamily = Display, fontSize = 12.sp, color = C.cyan, letterSpacing = 2.sp, shadow = glow(C.cyan, 8f)),
    )
}

/** Animated glowing progress bar. */
@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 8) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(2.dp)).background(C.track)) {
        Box(
            Modifier.fillMaxWidth(f).height(height.dp)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)))
        )
    }
}

@Composable
fun LabeledBar(label: String, value: String, fraction: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, color = C.muted, fontWeight = FontWeight.SemiBold)
            Text(value, fontSize = 13.sp, color = C.text, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        Bar(fraction, color)
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text.uppercase(),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

/** Hexagon rank emblem with a glowing letter. */
@Composable
fun RankEmblem(rank: String, color: Color, size: Dp = 64.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val path = Path().apply {
                for (i in 0 until 6) {
                    val a = Math.toRadians((60.0 * i) - 90.0)
                    val p = Offset(c.x + r * 0.95f * cos(a).toFloat(), c.y + r * 0.95f * sin(a).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path, Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), c, r))
            drawPath(path, color, style = Stroke(width = 4f))
        }
        Text(rank, style = TextStyle(fontFamily = Display, fontSize = (size.value * 0.42f).sp, color = Color.White, shadow = glow(color, 24f), fontWeight = FontWeight.Black))
    }
}

/** A big number with − and + buttons. */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 13.sp, color = C.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(44.dp), shape = RoundedCornerShape(6.dp * 2)) { Text("−", fontSize = 18.sp) }
            Text(value, modifier = Modifier.width(88.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(44.dp), shape = RoundedCornerShape(6.dp * 2)) { Text("+", fontSize = 18.sp) }
        }
    }
}

/** Rank letter inside a glowing XP ring (Holo status panel). */
@Composable
fun XpRing(rank: String, fraction: Float, size: Dp = 76.dp, color: Color = C.cyan) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = this.size.minDimension * 0.085f
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color.copy(alpha = 0.35f), -90f, 360f * f, false, Offset(inset, inset), arcSize, style = Stroke(stroke * 2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawArc(color, -90f, 360f * f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Text(rank, style = TextStyle(fontFamily = Display, fontSize = (size.value * 0.36f).sp, color = Color.White, shadow = glow(color, 20f), fontWeight = FontWeight.Black))
    }
}

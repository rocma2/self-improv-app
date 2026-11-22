package com.ascend.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A one-shot burst of glowing shards from the middle of the screen. Draws nothing once it's over. */
@Composable
fun ShardBurst(trigger: Int, onDone: () -> Unit) {
    val t = remember(trigger) { Animatable(0f) }
    val shards = remember(trigger) {
        val colors = listOf(C.cyan, C.gold, C.purple, C.blue, C.green)
        List(70) {
            Triple(Random.nextDouble(0.0, Math.PI * 2), Random.nextDouble(0.35, 1.0).toFloat(), colors[it % colors.size])
        }
    }
    LaunchedEffect(trigger) {
        t.snapTo(0f)
        t.animateTo(1f, tween(1400, easing = LinearEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - t.value * t.value }) {
        val c = Offset(size.width / 2f, size.height * 0.42f)
        val reach = size.minDimension * 0.75f
        shards.forEach { (angle, speed, color) ->
            val d = reach * speed * (1 - (1 - t.value) * (1 - t.value))
            val fall = size.height * 0.25f * t.value * t.value
            val p = Offset(c.x + (cos(angle) * d).toFloat(), c.y + (sin(angle) * d).toFloat() + fall)
            drawCircle(color.copy(alpha = 0.35f), 12f * speed, p)
            drawCircle(Color.White, 3.5f * speed, p)
            drawCircle(color, 6f * speed, p)
        }
    }
}

private val manual = listOf(
    "Daily quests" to "Built from your schedule. Log a run, sleep or minutes and the quest finishes itself. Unfinished quests at midnight cost HP and, on harder modes, add a penalty quest.",
    "XP, levels and ranks" to "Every quest gives XP. Levels come fast; ranks (E, D, C, B, A, S) each need 3x the XP of the last. Rank up = +5 stat points and 500 gold.",
    "HP" to "Each skill has 100 HP. Misses take it away, finished quests heal +5. At 0 HP the skill drops a level. Shields block a miss.",
    "Penalty and app lock" to "On Obsessed and New life, a penalty quest locks TikTok and YouTube until you clear it. Rest days and recovery weeks never count as misses.",
    "Momentum" to "Good days add to your streak. A bad day never sends it to zero: a freeze saves it, otherwise you keep 75%.",
    "Training points" to "Runs, gym and bike earn TP toward this week's plan. Three chests at 50, 75 and 100%. Going over the plan earns nothing extra.",
    "Gates and bosses" to "3 gates a week (from level 2). Enter one and hit its target by Sunday. Bosses are your real goals: sub-19 5K, half, marathon, 72 kg, squat.",
    "Gold and shop" to "Gold comes from XP, gates and chests. The shop (from level 4) has shields, XP potions, skip passes and your own real-life rewards.",
    "Dungeon" to "A no-phone focus timer for study, reading or money work. Finish it for a bonus.",
    "Check-in and hidden quests" to "Open the app once a day for the 7-day check-in track. Some days hide a bonus for finishing every quest.",
    "Your data" to "Everything stays on your phone. If Android backup is on, it is saved with your Google account too. You can also export a file in Settings.",
)

/** The System manual: every rule on one screen. */
@Composable
fun ManualDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        AppCard(title = "System manual", solid = true) {
            LazyColumn(Modifier.height(520.dp)) {
                items(manual) { (title, body) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(title, fontWeight = FontWeight.Bold, color = C.cyan, fontSize = 16.sp)
                        Text(body, fontSize = 14.sp, color = C.text)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) { Text("GOT IT") }
        }
    }
}

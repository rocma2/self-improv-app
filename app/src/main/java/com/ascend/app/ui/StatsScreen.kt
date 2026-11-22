@file:OptIn(ExperimentalLayoutApi::class)

package com.ascend.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.data.Repository
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Badges
import com.ascend.app.domain.Exercises
import com.ascend.app.domain.Fmt
import com.ascend.app.domain.Leveling
import com.ascend.app.domain.Records
import com.ascend.app.domain.Seasons
import com.ascend.app.domain.Skill
import com.ascend.app.domain.Stats
import com.ascend.app.domain.TrainingPlan
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsScreen(state: AppState) {
    val today = Repository.today()
    var open by remember { mutableStateOf<Skill?>(null) }
    open?.let { sk -> SkillDialog(state, sk) { open = null } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SkillsRadarCard(state) { open = it } }
        item { FuelCard(state, today) }

        item { SectionTitle("PROGRESS THAT DOESN'T PLATEAU") }
        item {
            AppCard {
                val wonBack = state.screenDays.sumOf { (state.screen.baselineMin - it.usedMin).coerceAtLeast(0) }
                StatLine("Consistency (28 days)", Stats.consistency(state, today)?.let { "$it%" } ?: "—")
                StatLine("Total km", Fmt.km((Stats.totalKm(state) * 10).roundToInt() / 10.0))
                StatLine("Best momentum", "🔥 ${state.momentum.best}")
                StatLine("Quests done", "${state.questRecords.count { it.status == com.ascend.app.domain.QuestStatus.DONE }}")
                StatLine("Time won back", Fmt.hours(wonBack))
                StatLine("Weight", "${Fmt.kg(state.latestWeight)} (${"%+.1f".format(state.latestWeight - state.profile.startKg)} kg)")
                val cmp = Stats.vsThreeMonthsAgo(state, today)
                if (cmp.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("You vs 3 months ago", fontWeight = FontWeight.Bold)
                    cmp.forEach { c ->
                        StatLine(c.title, "${c.then} → ${c.now} " + when (c.better) { true -> "⬆"; false -> "·"; null -> "" })
                    }
                } else {
                    Text("In 3 months you'll see yourself vs today here.", fontSize = 12.sp, color = C.muted)
                }
            }
        }

        item { SeasonCard(state) }

        item { SectionTitle("CHARTS") }
        item {
            AppCard {
                Text("Weekly running km", fontWeight = FontWeight.SemiBold)
                Text("Violet = run, light blue = bike ÷ 3, gold line = this week's target", fontSize = 11.sp, color = C.muted)
                Spacer(Modifier.height(6.dp))
                val bikes = Stats.weeklyBikeKm(state, today).map { it.copy(value = it.value / 3) }
                BarChart(Stats.weeklyKm(state, today), C.violet, TrainingPlan.targetKm(state, TrainingPlan.weekIndex(state, today), today), bikes)
            }
        }
        item {
            AppCard {
                Text("Pace (lower is faster)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LineChart(Stats.paces(state), C.cyan, { Fmt.pace(it) })
            }
        }
        item {
            AppCard {
                Text("Body weight", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LineChart(Stats.weights(state), C.green, { "%.1f".format(it) }, target = state.profile.targetKg)
            }
        }
        item { LiftChart(state) }

        item { SectionTitle("PERSONAL RECORDS") }
        item {
            AppCard {
                val recs = Records.all(state)
                if (recs.isEmpty()) Text("Log runs and lifts. Records are found automatically.", color = C.muted, fontSize = 13.sp)
                recs.forEach { r -> StatLine(r.title, r.value) }
            }
        }

        item { SectionTitle("BADGES ${state.badges.size} / ${Badges.all.size}") }
        item {
            AppCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badges.all.forEach { b ->
                        val got = b.id in state.badges
                        Column(Modifier.width(96.dp).alpha(if (got) 1f else 0.35f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(b.emoji, fontSize = 28.sp)
                            Text(b.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Text(b.description, fontSize = 10.sp, color = C.muted, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        item { SectionTitle("MONTHLY RECAP") }
        item { RecapCard(state) }
    }
}

@Composable
fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = C.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SeasonCard(state: AppState) {
    val today = Repository.today()
    val season = Seasons.current(state, today) ?: return
    val left = ChronoUnit.DAYS.between(today, java.time.LocalDate.parse(season.end))
    AppCard(border = C.gold.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆 Season ${season.number}", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text("$left days left", color = C.muted, fontSize = 13.sp)
        }
        Text("Fresh goals every 6 weeks, built from where you are now. Each one is worth XP.", fontSize = 12.sp, color = C.muted)
        Spacer(Modifier.height(6.dp))
        season.goals.forEach { g ->
            val v = Seasons.value(state, season, g)
            val claimed = g.id in season.claimed
            val shown = if (g.target >= 10) "${v.roundToInt()} / ${g.target.roundToInt()}" else "%.1f / %.1f".format(v, g.target)
            LabeledBar((if (claimed) "✅ " else "") + g.title, shown, (v / g.target).toFloat(), if (claimed) C.green else C.gold)
        }
    }
}

@Composable
private fun LiftChart(state: AppState) {
    var ex by remember { mutableStateOf(Exercises.SQUAT) }
    AppCard {
        Text("Strength (estimated max)", fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Exercises.SQUAT, Exercises.DEADLIFT, Exercises.OHP, Exercises.BENCH, Exercises.PULLUP).forEach { e ->
                FilterChip(selected = ex == e, onClick = { ex = e }, label = { Text(e) })
            }
        }
        LineChart(Stats.lift(state, ex), C.gold, { if (ex in Exercises.loaded) Fmt.kg(it.roundToInt().toDouble()) else "${it.roundToInt()} reps" })
    }
}

@Composable
private fun RecapCard(state: AppState) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
            Text(month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " ${month.year}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextButton(onClick = { if (month < YearMonth.now()) month = month.plusMonths(1) }) { Text("›") }
        }
        RecapBody(state, month)
    }
}

@Composable
private fun RecapBody(state: AppState, month: YearMonth) {
    val r = Stats.recap(state, month)
    val prev = Stats.recap(state, month.minusMonths(1))
    fun delta(now: Double, before: Double) = if (before == 0.0) "" else " (${"%+.0f".format(now - before)})"
    Column {
        StatLine("XP earned", "${r.xp}" + delta(r.xp.toDouble(), prev.xp.toDouble()))
        StatLine("Runs · km", "${r.runs} · ${"%.1f".format(r.km)}" + delta(r.km, prev.km))
        StatLine("Strength sessions", "${r.gymSessions}")
        StatLine("Reading", Fmt.hours(r.readMin))
        StatLine("Studying", Fmt.hours(r.studyMin))
        StatLine("Money work", Fmt.hours(r.moneyMin))
        StatLine("Average sleep", r.avgSleep?.let { "%.1f h".format(it) } ?: "—")
        StatLine("Weight change", r.weightChange?.let { "%+.1f kg".format(it) } ?: "—")
        StatLine("Quests done / missed", "${r.questsDone} / ${r.questsMissed}")
        StatLine("Won back from social apps", Fmt.hours(r.wonBackMin))
        if (r.badges.isNotEmpty()) StatLine("Badges", r.badges.joinToString(" ") { it.emoji })
    }
}

@Composable
fun RecapDialog(state: AppState, month: YearMonth, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("On to this month") } },
        title = { Text("📜 ${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} recap") },
        text = { RecapBody(state, month) },
        containerColor = C.surface2,
    )
}

/** Daily food target for the weight goal, adjusted from real weigh-ins. */
@Composable
private fun FuelCard(state: AppState, today: java.time.LocalDate) {
    val f = com.ascend.app.domain.Nutrition.target(state, today)
    AppCard(title = "Fuel plan", border = C.green) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${f.kcal}", style = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontSize = 26.sp, color = C.text, shadow = glow(C.green, 14f)))
                Text("kcal a day", fontSize = 12.sp, color = C.muted)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${f.proteinG} g", style = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontSize = 26.sp, color = C.text, shadow = glow(C.green, 14f)))
                Text("protein", fontSize = 12.sp, color = C.muted)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(f.note, fontSize = 13.sp, color = C.green)
        Spacer(Modifier.height(4.dp))
        val day = today.dayOfYear
        listOf(day, day + 2).map { com.ascend.app.domain.Nutrition.IDEAS[it % com.ascend.app.domain.Nutrition.IDEAS.size] }
            .forEach { Text("• $it", fontSize = 13.sp, color = C.muted) }
        Text("A rough guide for gaining weight while you run, not medical advice.", fontSize = 11.sp, color = C.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

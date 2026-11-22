package com.ascend.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.data.Repository
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Fmt
import com.ascend.app.domain.Game
import com.ascend.app.domain.QuestEngine
import com.ascend.app.domain.QuestStatus
import com.ascend.app.domain.Records
import com.ascend.app.domain.Surface
import com.ascend.app.domain.TrainingPlan
import com.ascend.app.domain.weekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun TrainScreen(state: AppState, onLog: (LogTarget) -> Unit) {
    val today = Repository.today()
    val week = TrainingPlan.weekIndex(state, today)
    val ws = today.weekStart()
    val target = TrainingPlan.targetKm(state, week, today)
    val done = TrainingPlan.weekRunKm(state, ws)
    val bike = TrainingPlan.weekBikeKm(state, ws)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Week $week", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    if (TrainingPlan.isDeload(week)) Pill("deload", C.cyan)
                    if (TrainingPlan.isTestWeek(week)) Pill("5K test", C.gold)
                    if (TrainingPlan.isWinter(state, today)) { Spacer(Modifier.width(4.dp)); Pill("winter", C.cyan) }
                }
                Spacer(Modifier.height(8.dp))
                LabeledBar("Running this week", "${Fmt.km(round1(done))} / ${Fmt.km(target)}", (done / target).toFloat(), C.violet)
                LabeledBar("Bike commutes", Fmt.km(round1(bike)), (bike / 50).toFloat(), C.cyan)
                Text("Total load: ${Fmt.km(round1(done + bike / 3))} run-equivalent (bike ÷ 3). The plan never adds more than 10% a week, and every 4th week is lighter.",
                    fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item { SectionTitle("THIS WEEK") }
        items((0..6).map { ws.plusDays(it.toLong()) }) { d -> DayRow(state, d, today) }

        item { GoalsCard(state, today) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onLog(LogTarget.Run(TrainingPlan.plannedRun(state, today)?.km, TrainingPlan.plannedRun(state, today)?.type)) }, modifier = Modifier.weight(1f)) { Text("Log run") }
                Button(onClick = { onLog(LogTarget.Gym) }, modifier = Modifier.weight(1f)) { Text("Log gym") }
                Button(onClick = { onLog(LogTarget.Bike) }, modifier = Modifier.weight(1f)) { Text("Log bike") }
            }
        }

        val runs = state.runs.sortedByDescending { it.date }.take(15)
        if (runs.isNotEmpty()) {
            item { SectionTitle("RECENT RUNS") }
            items(runs, key = { "r${it.id}" }) { r ->
                LogRow(
                    "${r.date} · ${r.type.label}" + if (r.surface != Surface.OUTDOOR) " · ${r.surface.label}" else "",
                    "${Fmt.km(r.km)}" + (if (r.seconds > 0) " in ${Fmt.duration(r.seconds)}" else "") +
                        (if (r.seconds > 0 && r.surface != Surface.INDOOR_BIKE) " · ${Fmt.pace(r.seconds / r.km)}" else "") +
                        (r.tempC?.let { " · $it °C" } ?: ""),
                ) { Repository.edit { Game.deleteRun(it, r.id) } }
            }
        }
        val gyms = state.gym.sortedByDescending { it.date }.take(8)
        if (gyms.isNotEmpty()) {
            item { SectionTitle("RECENT STRENGTH") }
            items(gyms, key = { "g${it.id}" }) { g ->
                LogRow(g.date + if (g.homeWorkout) " · home" else "",
                    g.entries.joinToString("\n") { e -> "${e.exercise}: ${if (e.kg > 0) Fmt.kg(e.kg) + " × " else ""}${e.reps} × ${e.sets}" },
                ) { Repository.edit { Game.deleteGym(it, g.id) } }
            }
        }
        val bikes = state.bikes.sortedByDescending { it.date }.take(5)
        if (bikes.isNotEmpty()) {
            item { SectionTitle("RECENT BIKE") }
            items(bikes, key = { "b${it.id}" }) { b -> LogRow(b.date, Fmt.km(b.km)) { Repository.edit { Game.deleteBike(it, b.id) } } }
        }
    }
}

private fun round1(v: Double) = Math.round(v * 10) / 10.0

@Composable
private fun DayRow(state: AppState, d: LocalDate, today: LocalDate) {
    val run = TrainingPlan.plannedRun(state, d, today)
    val gym = TrainingPlan.gymSession(state, d)
    val quests = QuestEngine.questsFor(state, d, today).filter { it.skill == com.ascend.app.domain.Skill.RUNNING || it.skill == com.ascend.app.domain.Skill.GYM }
    val statuses = quests.map { q -> QuestEngine.record(state, q)?.status ?: if (QuestEngine.progress(state, q) >= 1) QuestStatus.DONE else null }
    val mark = when {
        quests.isEmpty() -> "·"
        statuses.all { it == QuestStatus.DONE } -> "✅"
        statuses.any { it == QuestStatus.MISSED } -> "❌"
        statuses.any { it == QuestStatus.REST } -> "😌"
        else -> "○"
    }
    AppCard(border = if (d == today) C.violet else null) {
        Row(verticalAlignment = Alignment.Top) {
            Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) {
                when {
                    run != null -> {
                        Text("${run.title} · ${Fmt.km(run.km)}", fontWeight = FontWeight.SemiBold)
                        Text(run.detail, fontSize = 12.sp, color = C.muted)
                    }
                    gym != null -> {
                        Text(gym.name + if (gym.deload) " (deload)" else "", fontWeight = FontWeight.SemiBold)
                        gym.lifts.forEach { l ->
                            Text("${l.exercise}: ${l.sets}×${l.reps}" + if (l.suggestedKg > 0) " @ ${Fmt.kg(l.suggestedKg)}" else "", fontSize = 12.sp, color = C.muted)
                        }
                    }
                    d.dayOfWeek == DayOfWeek.SUNDAY || d.dayOfWeek == DayOfWeek.WEDNESDAY -> Text("Rest. Recovery is training too.", color = C.muted, fontSize = 13.sp)
                    else -> Text("No training planned.", color = C.muted, fontSize = 13.sp)
                }
            }
            Text(mark, fontSize = 18.sp)
        }
    }
}

@Composable
private fun GoalsCard(state: AppState, today: LocalDate) {
    val best5k = Records.best5k(state)?.first
    val longest = state.runs.filter { it.surface != Surface.INDOOR_BIKE }.maxOfOrNull { it.km } ?: 0.0
    val week = TrainingPlan.weekIndex(state, today)
    val nextTest = generateSequence(week) { it + 1 }.first { TrainingPlan.isTestWeek(it) }
    val start = TrainingPlan.planStart(state)
    val marathonDate = start.plusYears(3)
    AppCard {
        Text("🎯 Big goals", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val sub19 = 19 * 60
        LabeledBar("5K under 19:00 (year 1)", best5k?.let { Fmt.duration(it) } ?: "no 5K yet",
            best5k?.let { (sub19.toFloat() / it).coerceIn(0f, 1f) } ?: 0f, C.gold)
        Text(if (nextTest == week) "5K time trial is this week." else "Next 5K time trial: week $nextTest (in ${nextTest - week} weeks).", fontSize = 12.sp, color = C.muted)
        LabeledBar("First half marathon (21.1 km)", "longest ${Fmt.km(longest)}", (longest / 21.1).toFloat(), C.violet)
        LabeledBar("Marathon under 3:00 (4:16/km)", "${ChronoUnit.DAYS.between(today, marathonDate).coerceAtLeast(0)} days left",
            (ChronoUnit.DAYS.between(start, today).toFloat() / ChronoUnit.DAYS.between(start, marathonDate)).coerceIn(0f, 1f), C.cyan)
    }
}

@Composable
fun LogRow(title: String, body: String, onDelete: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, color = C.muted)
                Text(body, fontSize = 14.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = C.muted) }
        }
    }
}

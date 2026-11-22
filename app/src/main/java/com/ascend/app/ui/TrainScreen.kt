package com.ascend.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ascend.app.domain.RunType
import com.ascend.app.domain.Skill
import com.ascend.app.domain.Training
import kotlin.math.roundToInt
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
    var selected by remember { mutableStateOf(today) }
    val streak = Training.weekStreak(state, today)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SysText("Training arc", size = 20.sp, modifier = Modifier.weight(1f))
                Pill("Week $week", C.cyan)
                if (streak > 0) Pill("🔥 $streak wk", C.gold)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (TrainingPlan.isDeload(week)) Pill("deload week", C.cyan)
                if (TrainingPlan.isTestWeek(week)) Pill("5K test week", C.gold)
                if (TrainingPlan.isWinter(state, today)) Pill("winter arc ×1.2", C.cyan)
            }
        }

        item { MissionCard(state, today, onLog) }
        item { TpTrack(state, today) }
        item { WeekPath(state, ws, today, selected) { selected = it } }
        item { DayDetail(state, selected, today) }
        item { BestsRow(state) }
        item { GoalsCard(state, today) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onLog(LogTarget.Run(TrainingPlan.plannedRun(state, today)?.km, TrainingPlan.plannedRun(state, today)?.type)) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("+ Run") }
                OutlinedButton(onClick = { onLog(LogTarget.Gym) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("+ Gym") }
                OutlinedButton(onClick = { onLog(LogTarget.Bike) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("+ Bike") }
            }
        }

        val feed = activityFeed(state)
        if (feed.isNotEmpty()) {
            item { SectionTitle("Activity log") }
            items(feed, key = { it.key }) { f -> FeedRow(f) }
        }
    }
}

private fun round1(v: Double) = Math.round(v * 10) / 10.0

private fun dayStatus(state: AppState, d: LocalDate, today: LocalDate): QuestStatus? {
    val quests = QuestEngine.questsFor(state, d, today).filter { it.skill == Skill.RUNNING || it.skill == Skill.GYM }
    if (quests.isEmpty()) return null
    val st = quests.map { q -> QuestEngine.record(state, q)?.status ?: if (QuestEngine.progress(state, q) >= 1) QuestStatus.DONE else null }
    return when {
        st.all { it == QuestStatus.DONE } -> QuestStatus.DONE
        st.any { it == QuestStatus.MISSED } -> QuestStatus.MISSED
        st.any { it == QuestStatus.REST } -> QuestStatus.REST
        else -> null
    }
}

/** Today's session as a mission: rank, rewards, one big button. */
@Composable
private fun MissionCard(state: AppState, today: LocalDate, onLog: (LogTarget) -> Unit) {
    val run = TrainingPlan.plannedRun(state, today)
    val gym = TrainingPlan.gymSession(state, today)
    val quest = QuestEngine.questsFor(state, today).firstOrNull { it.skill == Skill.RUNNING || it.skill == Skill.GYM }
    val status = dayStatus(state, today, today)
    val done = status == QuestStatus.DONE
    val rank = Training.missionRank(run, gym)
    val color = when { done -> C.green; rank == "S" -> C.gold; rank == "B" -> C.hp; else -> C.cyan }
    AppCard(title = if (done) "Mission cleared" else "Today's mission", border = color, pulse = !done && quest != null) {
        if (run == null && gym == null) {
            SysText("Rest day", size = 20.sp, glowColor = C.green)
            Text("Recovery is where you get stronger. Sleep well, eat well, walk a bit. A bike commute still earns TP.", color = C.muted, fontSize = 14.sp)
            return@AppCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankEmblem(rank, color, 58.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(run?.let { "${it.title} · ${Fmt.km(it.km)}" } ?: (gym!!.name + if (gym.deload) " (deload)" else ""), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Rank $rank session", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (run != null) Text(run.detail, color = C.muted, fontSize = 14.sp)
        gym?.lifts?.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(l.exercise, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${l.sets}×${l.reps}" + if (l.suggestedKg > 0) " @ ${Fmt.kg(l.suggestedKg)}" else "", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = C.cyan)
            }
        }
        Spacer(Modifier.height(10.dp))
        val xp = quest?.let { (it.baseXp * Game.xpMultiplier(state, today)).roundToInt() } ?: 0
        val tp = run?.let { (it.km * 10).roundToInt() + if (it.type == RunType.SPEED || it.type == RunType.TEST) 20 else 0 } ?: 60
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("+$xp XP", C.gold)
            Pill("+$tp TP", C.cyan)
        }
        Spacer(Modifier.height(10.dp))
        when {
            done -> SysText("✔ Cleared", size = 18.sp, glowColor = C.green, color = C.green)
            status == QuestStatus.REST -> Text("Turned into a rest day.", color = C.muted)
            else -> Button(
                onClick = { if (run != null) onLog(LogTarget.Run(run.km, run.type)) else onLog(LogTarget.Gym) },
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.9f)),
            ) { Text("START MISSION", color = C.bg, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

/** Weekly Training Points with three chests. Nothing is paid past 100% of the plan. */
@Composable
private fun TpTrack(state: AppState, today: LocalDate) {
    val ws = today.weekStart()
    val tp = Training.weekTp(state, ws)
    val target = Training.weekTarget(state, ws, today).coerceAtLeast(1)
    AppCard(title = "Training points") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$tp", style = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontSize = 34.sp, color = C.text, shadow = glow(C.cyan, 16f)))
            Text(" / $target TP this week", color = C.muted, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(6.dp))
        Bar(tp / target.toFloat(), C.cyan, height = 10)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Training.TIERS.forEachIndexed { i, t ->
                val claimed = Training.claimed(state, ws, i)
                val reached = Training.reached(state, ws, today, i)
                val c = when (i) { 0 -> Color(0xFFCD8B5A); 1 -> Color(0xFFC9D6EA); else -> C.gold }
                OutlinedButton(
                    onClick = { Repository.update { s -> Game.act(s, today) { st, ev -> Training.claim(st, today, i, ev) } } },
                    enabled = reached && !claimed, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.copy(alpha = if (reached) 0.9f else 0.3f)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (claimed) "✔" else "${(t.share * 100).roundToInt()}%", color = c, fontWeight = FontWeight.Bold)
                        Text(if (claimed) "claimed" else "${t.label} · ${t.gold} 🪙", fontSize = 11.sp, color = if (reached) C.text else C.muted)
                    }
                }
            }
        }
        Text("Runs earn 10 TP per km, speed work +20, gym 50+, bike 2 per km. The track stops at your plan: going over it never pays, so it never pushes you past +10% a week.",
            fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(top = 8.dp))
    }
}

/** The week as a path of 7 nodes. Tap a node to see that day. */
@Composable
private fun WeekPath(state: AppState, ws: LocalDate, today: LocalDate, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val days = (0..6).map { ws.plusDays(it.toLong()) }
    AppCard {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 21.dp).height(2.dp).background(C.track))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { d ->
                    val planned = TrainingPlan.plannedRun(state, d, today) != null || TrainingPlan.gymSession(state, d) != null
                    val st = dayStatus(state, d, today)
                    val c = when {
                        st == QuestStatus.DONE -> C.green
                        st == QuestStatus.MISSED -> C.hp
                        st == QuestStatus.REST -> C.muted
                        d == today -> C.gold
                        planned -> C.blue
                        else -> C.track
                    }
                    val isSel = d == selected
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(42.dp).clickable { onSelect(d) }) {
                        Box(
                            Modifier.size(if (isSel) 44.dp else 38.dp).clip(CircleShape)
                                .background(if (planned || st != null) c.copy(alpha = 0.25f) else C.bg)
                                .border(if (isSel) 3.dp else 2.dp, c, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                when (st) { QuestStatus.DONE -> "✔"; QuestStatus.MISSED -> "✕"; QuestStatus.REST -> "z"; else -> if (planned) "◆" else "·" },
                                color = c, fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2), fontSize = 12.sp,
                            color = if (d == today) C.gold else C.muted, fontWeight = FontWeight.Bold)
                        val dtp = Training.dayTp(state, d)
                        Text(if (dtp > 0) "+$dtp" else " ", fontSize = 11.sp, color = C.cyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetail(state: AppState, d: LocalDate, today: LocalDate) {
    val run = TrainingPlan.plannedRun(state, d, today)
    val gym = TrainingPlan.gymSession(state, d)
    val label = if (d == today) "Today" else d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    AppCard(border = if (d == today) C.gold else null) {
        Text(label, fontWeight = FontWeight.Bold, color = C.cyan)
        when {
            run != null -> {
                Text("${run.title} · ${Fmt.km(run.km)}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(run.detail, fontSize = 13.sp, color = C.muted)
            }
            gym != null -> {
                Text(gym.name + if (gym.deload) " (deload)" else "", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                gym.lifts.forEach { l ->
                    Text("${l.exercise}: ${l.sets}×${l.reps}" + if (l.suggestedKg > 0) " @ ${Fmt.kg(l.suggestedKg)}" else "", fontSize = 13.sp, color = C.muted)
                }
            }
            else -> Text("Rest. Recovery is training too.", color = C.muted, fontSize = 14.sp)
        }
    }
}

/** Personal bests as glowing chips. */
@Composable
private fun BestsRow(state: AppState) {
    val best5k = Records.best5k(state)?.first
    val longest = state.runs.filter { it.surface != Surface.INDOOR_BIKE }.maxOfOrNull { it.km } ?: 0.0
    val squat = Records.bestE1rm(state, com.ascend.app.domain.Exercises.SQUAT)
    val dead = Records.bestE1rm(state, com.ascend.app.domain.Exercises.DEADLIFT)
    Column {
        SectionTitle("Personal bests")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BestChip("5K", best5k?.let { Fmt.duration(it) } ?: "—")
            BestChip("Longest", if (longest > 0) Fmt.km(round1(longest)) else "—")
            BestChip("Squat", if (squat > 0) "~${squat.roundToInt()} kg" else "—")
            BestChip("Deadlift", if (dead > 0) "~${dead.roundToInt()} kg" else "—")
            BestChip("Total", Fmt.km(round1(state.runs.sumOf { TrainingPlan.runEquivalentKm(it) })))
        }
    }
}

@Composable
private fun BestChip(label: String, value: String) {
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(C.glassTop, C.glassBottom)))
            .border(1.dp, C.purple.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label.uppercase(), fontSize = 11.sp, color = C.muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, style = androidx.compose.ui.text.TextStyle(fontFamily = Display, fontSize = 17.sp, color = C.text, shadow = glow(C.purple, 12f)))
    }
}

private data class Feed(val key: String, val date: String, val icon: String, val title: String, val body: String, val tp: Int, val delete: (AppState) -> AppState)

private fun activityFeed(state: AppState): List<Feed> {
    val runs = state.runs.map { r ->
        Feed("r${r.id}", r.date, "🏃", "${r.type.label} run" + if (r.surface != Surface.OUTDOOR) " · ${r.surface.label}" else "",
            Fmt.km(r.km) + (if (r.seconds > 0) " in ${Fmt.duration(r.seconds)}" else "") +
                (if (r.seconds > 0 && r.surface != Surface.INDOOR_BIKE) " · ${Fmt.pace(r.seconds / r.km)}" else "") + (r.tempC?.let { " · $it °C" } ?: ""),
            Training.runTp(r)) { Game.deleteRun(it, r.id) }
    }
    val gyms = state.gym.map { g ->
        Feed("g${g.id}", g.date, "🏋️", "Strength" + if (g.homeWorkout) " · home" else "",
            g.entries.joinToString(" · ") { e -> "${e.exercise} ${if (e.kg > 0) Fmt.kg(e.kg) + "×" else ""}${e.reps}×${e.sets}" },
            Training.gymTp(g)) { Game.deleteGym(it, g.id) }
    }
    val bikes = state.bikes.map { b -> Feed("b${b.id}", b.date, "🚲", "Bike commute", Fmt.km(b.km), Training.bikeTp(b)) { Game.deleteBike(it, b.id) } }
    return (runs + gyms + bikes).sortedByDescending { it.date }.take(15)
}

@Composable
private fun FeedRow(f: Feed) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.blue.copy(alpha = 0.08f))
            .border(1.dp, C.blue.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(f.icon, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${f.title} · ${f.date.substring(5)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(f.body, fontSize = 13.sp, color = C.muted, maxLines = 2)
        }
        Pill("+${f.tp} TP", C.cyan)
        IconButton(onClick = { Repository.edit { f.delete(it) } }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = C.muted) }
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

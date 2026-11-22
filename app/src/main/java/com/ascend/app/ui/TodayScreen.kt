package com.ascend.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.data.Repository
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Game
import com.ascend.app.domain.Leveling
import com.ascend.app.domain.Quest
import com.ascend.app.domain.QuestEngine
import com.ascend.app.domain.QuestKind
import com.ascend.app.domain.QuestStatus
import com.ascend.app.domain.Skill
import com.ascend.app.domain.TrainingPlan
import com.ascend.app.domain.weekStart
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
fun TodayScreen(state: AppState, onLog: (LogTarget) -> Unit, onSettings: () -> Unit, onFocus: () -> Unit) {
    val today = Repository.today()
    val quests = QuestEngine.questsFor(state, today)
    val penalties = QuestEngine.pendingPenalties(state, today)
    val doneCount = quests.count { QuestEngine.record(state, it)?.status == QuestStatus.DONE }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { StatusWindow(state, onSettings) }

        if (!com.ascend.app.domain.Rpg.checkedInToday(state, today)) item {
            CheckInCard(state) { Repository.update { s -> Game.act(s, today) { st, ev -> com.ascend.app.domain.Rpg.checkIn(st, today, ev) } } }
        }

        if (TrainingPlan.isWinter(state, today)) item {
            AppCard(border = C.cyan, title = "Winter arc") {
                Text("All XP ×1.2 until March. Treadmill and indoor bike count for runs. Showing up is the whole game right now.", fontSize = 14.sp, color = C.muted)
            }
        }

        if (penalties.isNotEmpty()) {
            items(penalties, key = { "p${it.id}" }) { p ->
                AppCard(border = C.hp, title = "Penalty quest", pulse = true) {
                    SysText(p.title.removePrefix("Penalty: "), size = 18.sp, glowColor = C.hp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text(p.detail, fontSize = 14.sp, color = C.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (state.mode.appLock) Text("TikTok and YouTube are locked until this is cleared.", fontSize = 13.sp, color = C.hp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { Repository.update { Game.completePenalty(it, today, p.id) } }, shape = RoundedCornerShape(8.dp * 2),
                        colors = ButtonDefaults.buttonColors(containerColor = C.hp), modifier = Modifier.fillMaxWidth()) { Text("CLEARED") }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Daily quests", Modifier.weight(1f))
                Text("$doneCount / ${quests.size}", fontWeight = FontWeight.Bold, color = if (doneCount == quests.size) C.green else C.muted)
            }
        }
        items(quests, key = { it.id }) { q -> QuestRow(state, q, onLog, onFocus) }
        item {
            Column {
                if (today.toString() in state.restDays) {
                    Text("Recovery day. Nothing today counts as a miss.", color = C.green, fontSize = 13.sp)
                } else if (state.mode.hpLoss > 0 && doneCount < quests.size) {
                    Text(
                        "WARNING: quests not done by midnight cost ${state.mode.hpLoss} HP each" + if (state.mode.penaltyQuest) " and trigger a penalty quest." else ".",
                        color = C.hp, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Countdown()
            }
        }

        item { QuickLog(onLog, onFood = { Repository.update { Game.logFood(it, today, today) } }) }

        item { WeeklyChest(state) }

        item {
            if (today.toString() !in state.restDays) {
                if (Game.canTakeRestDay(state, today)) {
                    OutlinedButton(onClick = { Repository.edit { Game.takeRestDay(it, today) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp * 2)) {
                        Text("Sick or wrecked? Use this week's recovery day")
                    }
                } else Text("Recovery day already used this week.", color = C.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Countdown() {
    var left by remember { mutableStateOf(Duration.ZERO) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            left = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
            delay(1000)
        }
    }
    val s = left.seconds
    Text(
        "⏱ %02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        textAlign = TextAlign.Center,
        style = TextStyle(fontFamily = Display, fontSize = 16.sp, color = C.text, letterSpacing = 2.sp, shadow = glow(C.blue, 10f)),
    )
}

@Composable
private fun StatusWindow(state: AppState, onSettings: () -> Unit) {
    var allocating by remember { mutableStateOf(false) }
    if (allocating) AllocateDialog(state) { allocating = false }
    val info = Leveling.info(state.totalXp)
    val rank = Leveling.rank(state.totalXp)
    val hp = state.skills.values.map { it.hp }.average().takeIf { !it.isNaN() } ?: 100.0
    AppCard(title = "Status") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XpRing(rank, info.fraction)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                SysText("Level ${info.level}", size = 24.sp)
                Text(state.profile.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Title: ${Leveling.title(info.level)}  ·  Rank $rank", color = C.muted, fontSize = 13.sp)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = C.muted) }
        }
        Spacer(Modifier.height(12.dp))
        LabeledBar("XP", "${info.intoLevel} / ${info.levelSize} · ${(info.fraction * 100).roundToInt()}% to level ${info.level + 1}", info.fraction, C.cyan)
        LabeledBar("HP", "${hp.roundToInt()} / 100", (hp / 100).toFloat(), C.hp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            com.ascend.app.domain.Rpg.attributes(state).forEach { (name, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(name, fontSize = 12.sp, color = C.muted, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("$v", style = TextStyle(fontFamily = Display, fontSize = 20.sp, color = C.text, shadow = glow(C.blue, 12f)))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(state.mode.label, if (state.mode.appLock) C.hp else C.blue)
            Pill("🔥 ${state.momentum.streak}", C.gold)
            Pill("❄ ${state.momentum.freezes}", C.cyan)
            Pill("🪙 ${state.gold}", C.gold)
        }
        if (state.statPoints > 0) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { allocating = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp * 2),
                colors = ButtonDefaults.buttonColors(containerColor = C.gold.copy(alpha = 0.85f))) {
                Text("${state.statPoints} STAT POINT(S) TO SPEND", color = C.bg)
            }
        } else if (state.allocated.isNotEmpty()) {
            TextButton(onClick = { allocating = true }) { Text("View stats", color = C.muted, fontSize = 12.sp) }
        }
        Leveling.nextRank(state.totalXp)?.let { (r, xp, f) ->
            Spacer(Modifier.height(6.dp))
            LabeledBar("Rank $r", "%,d / %,d XP".format(state.totalXp, xp), f, C.violet)
        }
        com.ascend.app.domain.Rpg.nextUnlock(state)?.let { f ->
            Text("Next system: ${f.label} at level ${f.level}", fontSize = 12.sp, color = C.muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun QuickLog(onLog: (LogTarget) -> Unit, onFood: () -> Unit) {
    Column {
        SectionTitle("Quick log")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chip: @Composable (String, () -> Unit) -> Unit = { label, action ->
                AssistChip(onClick = action, label = { Text(label, fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(6.dp * 2),
                    colors = AssistChipDefaults.assistChipColors(containerColor = C.surface2, labelColor = C.text))
            }
            chip("🏃 Run") { onLog(LogTarget.Run()) }
            chip("🏋️ Gym") { onLog(LogTarget.Gym) }
            chip("🚲 Bike") { onLog(LogTarget.Bike) }
            chip("😴 Sleep") { onLog(LogTarget.Sleep) }
            chip("🍽️ Ate well") { onFood() }
            chip("⚖️ Weigh") { onLog(LogTarget.Weight) }
            chip("📖 Read") { onLog(LogTarget.Minutes(Skill.READING, 20)) }
            chip("🎓 Study") { onLog(LogTarget.Minutes(Skill.STUDYING, 45)) }
            chip("💰 Money") { onLog(LogTarget.Minutes(Skill.MONEY, 30)) }
        }
    }
}

private fun counter(q: Quest, progress: Double): String {
    val v = (progress * q.target).coerceAtMost(q.target)
    return when (q.kind) {
        QuestKind.RUN -> "[%.1f/%.1f km]".format(v, q.target)
        QuestKind.SLEEP -> "[%.1f/%.1f h]".format(v, q.target)
        QuestKind.READ, QuestKind.STUDY, QuestKind.MONEY -> "[${v.roundToInt()}/${q.target.roundToInt()} min]"
        QuestKind.SCREEN -> "[auto]"
        else -> "[${v.roundToInt()}/1]"
    }
}

@Composable
private fun QuestRow(state: AppState, q: Quest, onLog: (LogTarget) -> Unit, onFocus: () -> Unit) {
    val today = Repository.today()
    val done = QuestEngine.record(state, q)?.status == QuestStatus.DONE
    val skipped = QuestEngine.record(state, q)?.status == QuestStatus.REST
    val progress = if (done) 1.0 else QuestEngine.progress(state, q)
    val xp = (q.baseXp * Game.xpMultiplier(state, today)).roundToInt()
    val action: () -> Unit = {
        when (q.kind) {
            QuestKind.RUN -> onLog(LogTarget.Run(TrainingPlan.plannedRun(state, today)?.km, TrainingPlan.plannedRun(state, today)?.type))
            QuestKind.GYM -> onLog(LogTarget.Gym)
            QuestKind.SLEEP -> onLog(LogTarget.Sleep)
            QuestKind.FOOD -> Repository.update { Game.logFood(it, today, today) }
            QuestKind.WEIGH_IN -> onLog(LogTarget.Weight)
            QuestKind.READ -> onLog(LogTarget.Minutes(Skill.READING, q.target.toInt()))
            QuestKind.STUDY -> onLog(LogTarget.Minutes(Skill.STUDYING, q.target.toInt()))
            QuestKind.MONEY -> onLog(LogTarget.Minutes(Skill.MONEY, q.target.toInt()))
            QuestKind.SCREEN -> onFocus()
        }
    }
    val tint = if (done) C.green else C.blue
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = if (done) 0.08f else 0.10f))
            .border(1.dp, tint.copy(alpha = if (done) 0.4f else 0.3f), RoundedCornerShape(16.dp))
            .clickable(enabled = !done && !skipped) { action() }.padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(30.dp)) { Text(if (done) "✔" else q.skill.emoji, fontSize = 18.sp, color = C.green) }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (done) C.muted else C.text, modifier = Modifier.weight(1f, fill = false))
                    if (q.optional) { Spacer(Modifier.width(6.dp)); Pill("bonus", C.cyan) }
                    if (skipped) { Spacer(Modifier.width(6.dp)); Pill("rest", C.muted) }
                }
                Text(q.detail, fontSize = 12.sp, color = C.muted, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(counter(q, progress), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (done) C.green else C.text)
                Text("+$xp XP", fontSize = 12.sp, color = C.gold, fontWeight = FontWeight.Bold)
            }
        }
        if (!done && progress > 0 && q.kind != QuestKind.SCREEN) {
            Spacer(Modifier.height(5.dp))
            Bar(progress.toFloat(), C.blue, height = 4)
        }
    }
}

@Composable
private fun WeeklyChest(state: AppState) {
    val today = Repository.today()
    val ws = today.weekStart()
    val misses = state.questRecords.count { it.status == QuestStatus.MISSED && it.date >= ws.toString() }
    val limit = state.mode.weeklyMissLimit
    AppCard(title = "Weekly reward", border = if (limit < 99 && misses >= limit) C.hp else C.gold) {
        Text("+${(Game.WEEKLY_CHEST_XP * Game.xpMultiplier(state, today)).roundToInt()} XP chest on Sunday night", fontWeight = FontWeight.Bold, color = C.gold)
        Text(
            if (limit >= 99) "Soft mode: the chest is always yours."
            else if (misses >= limit) "Lost this week ($misses misses). New chest on Monday."
            else "Misses this week: $misses. You lose it at $limit.",
            fontSize = 14.sp, color = if (limit < 99 && misses >= limit) C.hp else C.muted,
        )
    }
}

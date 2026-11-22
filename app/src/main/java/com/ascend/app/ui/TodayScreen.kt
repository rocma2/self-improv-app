package com.ascend.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.roundToInt

@Composable
fun TodayScreen(state: AppState, onLog: (LogTarget) -> Unit, onSettings: () -> Unit, onFocus: () -> Unit) {
    val today = Repository.today()
    val quests = QuestEngine.questsFor(state, today)
    val penalties = QuestEngine.pendingPenalties(state, today)
    val doneCount = quests.count { QuestEngine.record(state, it)?.status == QuestStatus.DONE }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { HeaderCard(state, onSettings) }

        if (TrainingPlan.isWinter(state, today)) item {
            AppCard(border = C.cyan.copy(alpha = 0.5f)) {
                Text("❄️ Winter arc is on", fontWeight = FontWeight.Bold, color = C.cyan)
                Text("All XP x1.2 until March. Treadmill and indoor bike count for runs. Showing up is the whole game right now.", fontSize = 13.sp, color = C.muted)
            }
        }

        if (penalties.isNotEmpty()) {
            item { SectionTitle("PENALTY QUESTS") }
            items(penalties, key = { "p${it.id}" }) { p ->
                AppCard(border = C.hp) {
                    Text("⚠️ ${p.title}", fontWeight = FontWeight.Bold, color = C.hp)
                    Text(p.detail, fontSize = 13.sp, color = C.muted)
                    if (state.mode.appLock) Text("🔒 TikTok and YouTube stay locked until this is done.", fontSize = 12.sp, color = C.hp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { Repository.update { Game.completePenalty(it, today, p.id) } },
                        colors = ButtonDefaults.buttonColors(containerColor = C.hp)) { Text("I did it") }
                }
            }
        }

        item { QuickLog(onLog, onFood = { Repository.update { Game.logFood(it, today, today) } }) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("TODAY'S QUESTS")
                Spacer(Modifier.weight(1f))
                Text("$doneCount / ${quests.size}", color = C.muted, fontSize = 13.sp)
            }
        }
        items(quests, key = { it.id }) { q -> QuestCard(state, q, onLog, onFocus) }

        item { WeeklyChest(state) }

        item {
            val rest = today.toString() in state.restDays
            when {
                rest -> Text("😌 Recovery day. Nothing today counts as a miss. Do what you can.", color = C.green, fontSize = 13.sp)
                Game.canTakeRestDay(state, today) -> OutlinedButton(onClick = { Repository.edit { Game.takeRestDay(it, today) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Sick or wrecked? Take this week's recovery day")
                }
                else -> Text("Recovery day already used this week.", color = C.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HeaderCard(state: AppState, onSettings: () -> Unit) {
    val info = Leveling.info(state.totalXp)
    val hp = state.skills.values.map { it.hp }.average().takeIf { !it.isNaN() } ?: 100.0
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(C.violet), contentAlignment = Alignment.Center) {
                Text("${info.level}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = androidx.compose.ui.graphics.Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.profile.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Level ${info.level} · ${Leveling.title(info.level)}", color = C.muted, fontSize = 13.sp)
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings", tint = C.muted) }
        }
        Spacer(Modifier.height(10.dp))
        LabeledBar("XP", "${info.intoLevel} / ${info.levelSize}", info.fraction, C.gold)
        LabeledBar("HP", "${hp.roundToInt()} / 100", (hp / 100).toFloat(), C.hp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(state.mode.label, if (state.mode.appLock) C.hp else C.violet)
            Pill("🔥 ${state.momentum.streak}", C.gold)
            Pill("❄️ ${state.momentum.freezes} freeze" + if (state.momentum.freezes == 1) "" else "s", C.cyan)
        }
    }
}

@Composable
private fun QuickLog(onLog: (LogTarget) -> Unit, onFood: () -> Unit) {
    Column {
        SectionTitle("QUICK LOG")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chip: @Composable (String, () -> Unit) -> Unit = { label, action ->
                AssistChip(onClick = action, label = { Text(label) }, colors = AssistChipDefaults.assistChipColors(containerColor = C.surface2, labelColor = C.text))
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

@Composable
private fun QuestCard(state: AppState, q: Quest, onLog: (LogTarget) -> Unit, onFocus: () -> Unit) {
    val today = Repository.today()
    val rec = QuestEngine.record(state, q)
    val done = rec?.status == QuestStatus.DONE
    val progress = QuestEngine.progress(state, q)
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
    AppCard(modifier = Modifier.clickable(enabled = !done) { action() }, border = if (done) C.green.copy(alpha = 0.6f) else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q.skill.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                    if (q.optional) { Spacer(Modifier.width(6.dp)); Pill("bonus", C.cyan) }
                }
                Text(q.detail, fontSize = 12.sp, color = C.muted, maxLines = 3)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (done) "✓" else "+$xp", color = if (done) C.green else C.gold, fontWeight = FontWeight.Bold)
        }
        if (!done && q.kind != QuestKind.SCREEN && progress > 0) {
            Spacer(Modifier.height(8.dp))
            Bar(progress.toFloat(), C.violet, height = 5)
        }
    }
}

@Composable
private fun WeeklyChest(state: AppState) {
    val today = Repository.today()
    val ws = today.weekStart()
    val misses = state.questRecords.count { it.status == QuestStatus.MISSED && it.date >= ws.toString() }
    val limit = state.mode.weeklyMissLimit
    AppCard {
        Text("🧰 Weekly chest: +${(Game.WEEKLY_CHEST_XP * Game.xpMultiplier(state, today)).roundToInt()} XP on Sunday night", fontWeight = FontWeight.SemiBold)
        Text(
            if (limit >= 99) "Soft mode: the chest is always yours."
            else if (misses >= limit) "Lost this week ($misses misses). New chest on Monday."
            else "Misses this week: $misses. You lose it at $limit.",
            fontSize = 13.sp, color = if (misses >= limit) C.hp else C.muted,
        )
    }
}

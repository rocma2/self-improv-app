package com.ascend.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.data.Repository
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Boss
import com.ascend.app.domain.Game
import com.ascend.app.domain.GameEvent
import com.ascend.app.domain.Gate
import com.ascend.app.domain.GateStatus
import com.ascend.app.domain.Item
import com.ascend.app.domain.JobClass
import com.ascend.app.domain.Rpg
import com.ascend.app.domain.Skill
import com.ascend.app.domain.weekStart
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Small helper so every RPG button goes through the same rules and shows its system messages. */
private fun act(change: (AppState, MutableList<GameEvent>) -> AppState) {
    val today = Repository.today()
    Repository.update { Game.act(it, today, change) }
}

@Composable
fun GatesScreen(state: AppState) {
    val today = Repository.today()
    val gates = state.gates.filter { it.start == today.weekStart().toString() }
    val bosses = Rpg.bosses(state)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SysText("Hunter's base", size = 20.sp, modifier = Modifier.weight(1f))
                Pill("🪙 ${state.gold}", C.gold)
            }
        }

        item { DungeonPanel() }

        // New systems open one at a time so the app never feels like homework.
        if (Rpg.unlocked(state, Rpg.Feature.GATES)) {
            item { SectionTitle("This week's gates") }
            if (gates.isEmpty()) item { Text("New gates open on Monday.", color = C.muted) }
            items(gates, key = { it.id }) { GateCard(state, it) }
        }

        item { SectionTitle("Bosses: your big goals") }
        items(bosses, key = { it.id }) { BossCard(it, it.id in state.defeatedBosses) }

        if (Rpg.unlocked(state, Rpg.Feature.SHOP)) item { ShopPanel(state) }
        if (Rpg.unlocked(state, Rpg.Feature.JOB)) item { JobPanel(state) }

        val locked = Rpg.Feature.entries.filter { !Rpg.unlocked(state, it) }
        if (locked.isNotEmpty()) item {
            AppCard(title = "Sealed") {
                locked.forEach { f -> Text("🔒 ${f.label} opens at level ${f.level}", color = C.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                Text("Your gold keeps adding up until then.", color = C.muted, fontSize = 12.sp)
            }
        }
    }
}

// ---------- Dungeon: focus timer ----------

@Composable
private fun DungeonPanel() {
    var skill by rememberSaveable { mutableStateOf(Skill.STUDYING) }
    var length by rememberSaveable { mutableStateOf(45) }
    // Wall-clock start time, so the timer keeps the right time if you switch apps.
    var startedAt by rememberSaveable { mutableStateOf(0L) }
    val running = startedAt > 0
    val now by produceState(System.currentTimeMillis(), running) {
        while (running) { value = System.currentTimeMillis(); delay(1000) }
    }
    val elapsedSec = if (running) ((now - startedAt) / 1000).toInt().coerceAtLeast(0) else 0
    val totalSec = length * 60
    val done = running && elapsedSec >= totalSec

    AppCard(title = "Dungeon", border = if (running) C.violet else null, pulse = running) {
        if (!running) {
            Text("Enter a dungeon to study, read or work on money with no phone. Finish it for a clear bonus on top of the normal XP.",
                color = C.muted, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Rpg.DUNGEON_SKILLS.forEach { s ->
                    FilterChip(selected = skill == s, onClick = { skill = s }, label = { Text("${s.emoji} ${s.label}") }, shape = RoundedCornerShape(6.dp * 2))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(25, 45, 60, 90).forEach { m ->
                    FilterChip(selected = length == m, onClick = { length = m }, label = { Text("$m min") }, shape = RoundedCornerShape(6.dp * 2))
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { startedAt = System.currentTimeMillis() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp * 2)) {
                Text("ENTER DUNGEON")
            }
        } else {
            val left = (totalSec - elapsedSec).coerceAtLeast(0)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${skill.emoji} ${skill.label}", color = C.muted)
                SysText(if (done) "Cleared" else "%d:%02d".format(left / 60, left % 60), size = 40.sp, glowColor = if (done) C.green else C.violet)
                Spacer(Modifier.height(6.dp))
                Bar(elapsedSec / totalSec.toFloat(), if (done) C.green else C.violet, height = 10)
                Spacer(Modifier.height(10.dp))
                if (done) {
                    Button(onClick = {
                        val s = skill; val m = length
                        startedAt = 0L
                        act { st, ev -> Rpg.dungeon(st, Repository.today(), s, m, cleared = true, events = ev) }
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp * 2),
                        colors = ButtonDefaults.buttonColors(containerColor = C.green.copy(alpha = 0.8f))) { Text("CLAIM REWARD") }
                } else {
                    Text("Stay in. Leaving early still logs your minutes, but no bonus.", color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    TextButton(onClick = {
                        val s = skill; val m = elapsedSec / 60
                        startedAt = 0L
                        if (m > 0) act { st, ev -> Rpg.dungeon(st, Repository.today(), s, m, cleared = false, events = ev) }
                    }) { Text("Leave dungeon", color = C.hp) }
                }
            }
        }
    }
}

// ---------- Gates ----------

@Composable
private fun GateCard(state: AppState, g: Gate) {
    val value = Rpg.gateValue(state, g)
    val color = when (g.status) {
        GateStatus.CLEARED -> C.green
        GateStatus.FAILED -> C.muted
        else -> if (g.red) C.hp else C.blue
    }
    AppCard(border = color, title = if (g.red) "Red gate · Rank ${g.rank}" else "Gate · Rank ${g.rank}", pulse = g.red && g.status == GateStatus.ENTERED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.skill.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(g.detail, color = C.muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        LabeledBar("Progress", "${fmt(value)} / ${fmt(g.target)} ${g.metric.unit}", (value / g.target).toFloat(), color)
        Text("Reward: +${g.xp} XP · +${g.gold} 🪙 · 🎁", fontSize = 13.sp, color = C.gold)
        Spacer(Modifier.height(6.dp))
        when (g.status) {
            GateStatus.OPEN -> Button(onClick = { act { s, _ -> Rpg.enterGate(s, g.id) } }, shape = RoundedCornerShape(8.dp * 2),
                colors = ButtonDefaults.buttonColors(containerColor = if (g.red) C.hp.copy(alpha = 0.85f) else C.blue)) { Text("ENTER GATE") }
            GateStatus.ENTERED -> Pill("Inside the gate", color)
            GateStatus.CLEARED -> Pill("Cleared", C.green)
            GateStatus.FAILED -> Pill("Collapsed", C.muted)
        }
    }
}

private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.roundToInt().toString() else "%.1f".format(v)

// ---------- Bosses ----------

@Composable
private fun BossCard(b: Boss, rewarded: Boolean) {
    val color = when {
        b.locked -> C.muted
        b.defeated -> C.green
        else -> C.hp
    }
    AppCard(border = color, title = if (b.locked) "Sealed" else if (b.defeated) "Defeated" else "Boss") {
        SysText(b.name, size = 18.sp, glowColor = color, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        Text(b.goal, color = C.muted, fontSize = 13.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        if (b.locked) {
            Text("Defeat Warden of the Half to break the seal.", color = C.muted, fontSize = 13.sp)
        } else {
            LabeledBar("Boss HP", b.hpLabel, b.fraction, color)
            if (!b.defeated) Text("Every PR you set takes HP off this boss. Reward: +${Rpg.BOSS_XP} XP, 300 🪙, 3 🎁", fontSize = 12.sp, color = C.gold)
            else if (rewarded) Text("Reward claimed.", fontSize = 12.sp, color = C.green)
        }
    }
}

// ---------- Job change ----------

@Composable
private fun JobPanel(state: AppState) {
    val today = Repository.today()
    AppCard(title = "Job change", border = if (Rpg.canChooseJob(state)) C.gold else null, pulse = Rpg.canChooseJob(state)) {
        when {
            state.job != null -> {
                SysText(state.job.label, size = 20.sp, glowColor = C.gold)
                Text(state.job.description, color = C.muted, fontSize = 14.sp)
            }
            !Rpg.jobUnlocked(state) -> Text("Locked. Reach level ${Rpg.JOB_LEVEL} to take the job change trial and pick a class.", color = C.muted, fontSize = 14.sp)
            state.jobTrialStart == null -> {
                Text("The trial: ${Rpg.JOB_TRIAL_DAYS} good days in a row (all required quests done). A bad day doesn't erase your progress, it just pauses it.",
                    color = C.muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { act { s, ev -> ev += GameEvent.Message("[SYSTEM] Job change trial started."); Rpg.startJobTrial(s, today) } },
                    shape = RoundedCornerShape(8.dp * 2)) { Text("START THE TRIAL") }
            }
            !Rpg.canChooseJob(state) -> {
                val p = Rpg.jobTrialProgress(state)
                LabeledBar("Trial", "$p / ${Rpg.JOB_TRIAL_DAYS} days", p / Rpg.JOB_TRIAL_DAYS.toFloat(), C.gold)
            }
            else -> {
                Text("Trial passed. Choose your class. This is permanent.", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                JobClass.entries.forEach { j ->
                    OutlinedButton(onClick = { act { s, ev -> ev += GameEvent.Message("[SYSTEM] Job change complete: ${j.label}."); Rpg.chooseJob(s, j) } },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(8.dp * 2)) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(j.label, fontWeight = FontWeight.Bold, color = C.gold)
                            Text(j.description, fontSize = 12.sp, color = C.muted)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Shop: items, skip pass, bag, real-life rewards ----------

private enum class ShopTab(val label: String) { ITEMS("Items"), BAG("Bag"), REWARDS("Real life") }

@Composable
private fun ShopPanel(state: AppState) {
    var tab by rememberSaveable { mutableStateOf(ShopTab.ITEMS) }
    AppCard(title = "Shop · 🪙 ${state.gold}") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShopTab.entries.forEach { t ->
                val n = if (t == ShopTab.BAG) state.inventory.values.sum() else 0
                FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(if (n > 0) "${t.label} ($n)" else t.label) }, shape = RoundedCornerShape(6.dp * 2))
            }
        }
        Spacer(Modifier.height(6.dp))
        when (tab) {
            ShopTab.ITEMS -> {
                SkipPassRow(state)
                Item.entries.filter { it.price > 0 }.forEach { item ->
                    ItemRow(item.emoji, item.label, item.description) {
                        Button(onClick = { act { s, ev -> ev += GameEvent.Message("[SYSTEM] Bought ${item.label}."); Rpg.buy(s, item) } },
                            enabled = state.gold >= item.price, shape = RoundedCornerShape(6.dp * 2)) { Text("${item.price} 🪙") }
                    }
                }
            }
            ShopTab.BAG -> {
                val owned = Item.entries.filter { (state.inventory[it] ?: 0) > 0 }
                if (owned.isEmpty()) Text("Empty. Clear gates and daily quests to find boxes.", color = C.muted, fontSize = 14.sp)
                owned.forEach { item ->
                    ItemRow(item.emoji, "${item.label} ×${state.inventory[item]}", item.description) {
                        if (item == Item.SHIELD) Pill("Auto", C.cyan)
                        else OutlinedButton(onClick = { act { s, ev -> Rpg.useItem(s, item, Repository.today(), ev) } }, shape = RoundedCornerShape(6.dp * 2)) {
                            Text(if (item == Item.MYSTERY_BOX) "OPEN" else "USE")
                        }
                    }
                }
            }
            ShopTab.REWARDS -> RewardsTab(state)
        }
    }
}

@Composable
private fun ItemRow(emoji: String, title: String, detail: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, fontSize = 12.sp, color = C.muted)
        }
        Spacer(Modifier.width(6.dp))
        action()
    }
}

@Composable
private fun SkipPassRow(state: AppState) {
    val today = Repository.today()
    val price = Rpg.skipPrice(state, today)
    val workout = Rpg.skippableWorkout(state, today)
    val why = when {
        Rpg.skipUsedThisWeek(state, today) -> "Already used this week."
        workout == null -> "Only works on a day with an open run or gym session."
        else -> "Turns today's ${workout.title} into a rest day: no HP loss, no penalty, no XP. Price doubles each time (resets after 4 weeks). 1 per week."
    }
    ItemRow("🎟️", "Skip pass", why) {
        Button(onClick = { act { s, ev -> Rpg.skipWorkout(s, today, ev) } }, enabled = Rpg.canSkip(state, today), shape = RoundedCornerShape(6.dp * 2),
            colors = ButtonDefaults.buttonColors(containerColor = C.hp.copy(alpha = 0.8f))) { Text("$price 🪙") }
    }
}

@Composable
private fun RewardsTab(state: AppState) {
    var name by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("300") }
    Text("Your own rewards, bought with gold: a movie night, a new game, Xbox on a weekday…", color = C.muted, fontSize = 13.sp)
    state.customRewards.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎖️ ${r.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { act { s, ev -> ev += GameEvent.Message("[SYSTEM] Reward unlocked: ${r.name}. Enjoy it, you earned it."); Rpg.buyReward(s, r.id) } },
                enabled = state.gold >= r.cost, shape = RoundedCornerShape(6.dp * 2)) { Text("${r.cost} 🪙") }
            TextButton(onClick = { Repository.edit { Rpg.removeReward(it, r.id) } }) { Text("✕", color = C.muted) }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("Reward") }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(cost, { cost = it.filter(Char::isDigit).take(6) }, label = { Text("🪙") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(90.dp))
    }
    TextButton(onClick = {
        val c = cost.toIntOrNull() ?: 0
        if (name.isNotBlank() && c > 0) { Repository.edit { Rpg.addReward(it, name, c) }; name = "" }
    }) { Text("+ ADD REWARD") }
}

/** Spend unspent stat points, one tap per point. */
@Composable
fun AllocateDialog(state: AppState, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        AppCard(title = "Stat points", pulse = state.statPoints > 0, solid = true) {
            Text("${state.statPoints} point(s) to spend", fontWeight = FontWeight.Bold, color = C.gold)
            Text("STR gym · AGI running · VIT sleep + weight · INT reading + studying · WIL focus + money", fontSize = 12.sp, color = C.muted)
            Spacer(Modifier.height(8.dp))
            Rpg.attributes(state).forEach { (attr, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(attr, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                    Text("$v", style = TextStyle(fontFamily = Display, fontSize = 20.sp, color = C.text, shadow = glow(C.blue, 12f)), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { Repository.edit { Rpg.allocate(it, attr) } }, enabled = state.statPoints > 0, shape = RoundedCornerShape(6.dp * 2)) { Text("+") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp * 2)) { Text("CLOSE") }
        }
    }
}

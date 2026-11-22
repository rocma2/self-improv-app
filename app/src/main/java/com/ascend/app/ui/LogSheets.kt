@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ascend.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.data.Repository
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Exercises
import com.ascend.app.domain.Fmt
import com.ascend.app.domain.Game
import com.ascend.app.domain.LiftEntry
import com.ascend.app.domain.Records
import com.ascend.app.domain.RunType
import com.ascend.app.domain.Skill
import com.ascend.app.domain.Surface
import com.ascend.app.domain.TrainingPlan
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun LogSheet(state: AppState, target: LogTarget, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = C.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (target) {
                is LogTarget.Run -> RunForm(state, target, onDismiss)
                LogTarget.Gym -> GymForm(state, onDismiss)
                LogTarget.Weight -> WeightForm(state, onDismiss)
                LogTarget.Sleep -> SleepForm(state, onDismiss)
                is LogTarget.Minutes -> MinutesForm(target, onDismiss)
                LogTarget.Bike -> BikeForm(onDismiss)
            }
        }
    }
}

@Composable
private fun DayPicker(date: LocalDate, onPick: (LocalDate) -> Unit) {
    val today = Repository.today()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = date == today, onClick = { onPick(today) }, label = { Text("Today") })
        FilterChip(selected = date == today.minusDays(1), onClick = { onPick(today.minusDays(1)) }, label = { Text("Yesterday") })
    }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold)

private fun String.toDoubleLoose(): Double? = replace(',', '.').trim().toDoubleOrNull()

@Composable
private fun RunForm(state: AppState, t: LogTarget.Run, done: () -> Unit) {
    val today = Repository.today()
    var date by remember { mutableStateOf(today) }
    var km by remember { mutableDoubleStateOf(t.km ?: 5.0) }
    val defaultPace = Records.best5k(state)?.let { it.first / 5.0 + 90 } ?: 360.0
    var minutes by remember { mutableStateOf(((t.km ?: 5.0) * defaultPace / 60).roundToInt().toString()) }
    var seconds by remember { mutableStateOf("0") }
    var type by remember { mutableStateOf(t.type ?: RunType.EASY) }
    var surface by remember { mutableStateOf(if (TrainingPlan.isWinter(state, today)) Surface.OUTDOOR else Surface.OUTDOOR) }
    var temp by remember { mutableStateOf("") }

    Title("🏃 Log a run")
    DayPicker(date) { date = it }
    Stepper(if (surface == Surface.INDOOR_BIKE) "Bike distance (km)" else "Distance (km)", Fmt.km(km).removeSuffix(" km"),
        onMinus = { km = max(0.5, km - 0.5) }, onPlus = { km += 0.5 })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minutes") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        OutlinedTextField(seconds, { seconds = it.filter(Char::isDigit).take(2) }, label = { Text("Seconds") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
    }
    val totalSec = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
    if (totalSec > 0 && surface != Surface.INDOOR_BIKE) Text("Pace ${Fmt.pace(totalSec / km)}", color = C.muted, fontSize = 13.sp)
    Text("Type", color = C.muted, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunType.entries.forEach { r -> FilterChip(selected = type == r, onClick = { type = r }, label = { Text(r.label) }) }
    }
    Text("Where", color = C.muted, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface.entries.forEach { s -> FilterChip(selected = surface == s, onClick = { surface = s }, label = { Text(s.label) }) }
    }
    if (surface == Surface.OUTDOOR) {
        OutlinedTextField(temp, { v -> temp = v.filter { it.isDigit() || it == '-' } }, label = { Text("Temperature °C (optional, for cold badges)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth())
    }
    Button(onClick = {
        Repository.update { Game.logRun(it, today, date, km, totalSec, type, surface, temp.toIntOrNull()) }
        done()
    }, enabled = km > 0, modifier = Modifier.fillMaxWidth()) { Text("Save run") }
}

private class LiftRow(name: String, kg: Double, reps: Int, sets: Int) {
    val name = name
    var kg by mutableDoubleStateOf(kg)
    var reps by mutableIntStateOf(reps)
    var sets by mutableIntStateOf(sets)
}

@Composable
private fun GymForm(state: AppState, done: () -> Unit) {
    val today = Repository.today()
    var date by remember { mutableStateOf(today) }
    // Today's planned session, or the next one in the week.
    val planned = remember {
        (0..6).firstNotNullOfOrNull { TrainingPlan.gymSession(state, today.plusDays(it.toLong())) }
    }
    var home by remember { mutableStateOf(false) }
    val rows = remember {
        mutableStateListOf<LiftRow>().apply {
            planned?.lifts?.forEach { add(LiftRow(it.exercise, it.suggestedKg, it.reps, it.sets)) }
        }
    }
    Title("🏋️ Log strength" + (planned?.let { ": ${it.name}" } ?: ""))
    DayPicker(date) { date = it }
    Text("Weights are pre-filled with today's suggestion. Adjust only what changed.", color = C.muted, fontSize = 12.sp)
    rows.forEach { r ->
        Column(Modifier.fillMaxWidth()) {
            Text(r.name, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.name in Exercises.loaded || r.name == Exercises.PULLUP) {
                    Stepper("kg", Fmt.kg(r.kg).removeSuffix(" kg"), { r.kg = max(0.0, r.kg - 2.5) }, { r.kg += 2.5 }, Modifier.weight(1.2f))
                }
                Stepper(if (r.name == Exercises.PLANK) "sec" else "reps", "${r.reps}", { r.reps = max(0, r.reps - 1) }, { r.reps += 1 }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sets: ", color = C.muted, fontSize = 12.sp)
                (1..5).forEach { n -> FilterChip(selected = r.sets == n, onClick = { r.sets = n }, label = { Text("$n") }, modifier = Modifier.padding(end = 4.dp)) }
            }
        }
    }
    var extra by remember { mutableStateOf<String?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Exercises.all.filter { e -> rows.none { it.name == e } }.forEach { e ->
            FilterChip(selected = false, onClick = { rows.add(LiftRow(e, TrainingPlan.suggestedKg(state, e, 5, false), 5, 3)); extra = e }, label = { Text("+ $e") })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = home, onCheckedChange = { home = it })
        Spacer(Modifier.width(8.dp))
        Text("Home workout (Plan B)", fontSize = 14.sp)
    }
    Button(onClick = {
        val entries = rows.filter { it.reps > 0 }.map { LiftEntry(it.name, it.kg, it.reps, it.sets) }
        Repository.update { Game.logGym(it, today, date, entries, home) }
        done()
    }, enabled = rows.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Save session") }
}

@Composable
private fun WeightForm(state: AppState, done: () -> Unit) {
    val today = Repository.today()
    var date by remember { mutableStateOf(today) }
    var kg by remember { mutableDoubleStateOf(state.latestWeight) }
    Title("⚖️ Weigh-in")
    DayPicker(date) { date = it }
    Stepper("Body weight (kg)", "%.1f".format(kg), { kg = ((kg - 0.1) * 10).roundToInt() / 10.0 }, { kg = ((kg + 0.1) * 10).roundToInt() / 10.0 })
    val diff = kg - state.profile.startKg
    Text("%+.1f kg since you started · %.1f kg to go".format(diff, (state.profile.targetKg - kg).coerceAtLeast(0.0)), color = C.muted, fontSize = 13.sp)
    Button(onClick = { Repository.update { Game.logWeight(it, today, date, kg) }; done() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
}

@Composable
private fun SleepForm(state: AppState, done: () -> Unit) {
    val today = Repository.today()
    var hours by remember { mutableDoubleStateOf(state.schedule.sleepHours) }
    Title("😴 Last night's sleep")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(5.0, 6.0, 6.5, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5).forEach { h ->
            FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text("$h h") })
        }
    }
    Stepper("Hours", "$hours", { hours = max(0.0, hours - 0.25) }, { hours += 0.25 })
    Button(onClick = { Repository.update { Game.logSleep(it, today, today, hours) }; done() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
}

@Composable
private fun MinutesForm(t: LogTarget.Minutes, done: () -> Unit) {
    val today = Repository.today()
    var date by remember { mutableStateOf(today) }
    var mins by remember { mutableIntStateOf(t.suggested) }
    val title = when (t.skill) {
        Skill.READING -> "📖 Reading"
        Skill.STUDYING -> "🎓 Studying"
        Skill.MONEY -> "💰 Money work"
        else -> "${t.skill.emoji} ${t.skill.label}"
    }
    Title(title)
    DayPicker(date) { date = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10, 15, 20, 30, 45, 60, 90, 120).forEach { m -> FilterChip(selected = mins == m, onClick = { mins = m }, label = { Text("$m min") }) }
    }
    Stepper("Minutes", "$mins", { mins = max(5, mins - 5) }, { mins += 5 })
    Button(onClick = { Repository.update { Game.logMinutes(it, today, date, t.skill, mins) }; done() }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
}

@Composable
private fun BikeForm(done: () -> Unit) {
    val today = Repository.today()
    var date by remember { mutableStateOf(today) }
    var km by remember { mutableStateOf("10") }
    Title("🚲 Bike commute")
    Text("Counts in your weekly training load (not in running km).", color = C.muted, fontSize = 13.sp)
    DayPicker(date) { date = it }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("5", "10", "15", "20").forEach { v -> FilterChip(selected = km == v, onClick = { km = v }, label = { Text("$v km") }) }
    }
    OutlinedTextField(km, { km = it }, label = { Text("km") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    Spacer(Modifier.height(4.dp))
    Button(onClick = {
        km.toDoubleLoose()?.let { v -> Repository.update { Game.logBike(it, today, date, v) } }
        done()
    }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
}

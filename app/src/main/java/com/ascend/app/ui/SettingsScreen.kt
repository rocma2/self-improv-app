@file:OptIn(ExperimentalLayoutApi::class)

package com.ascend.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.data.Repository
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Difficulty
import com.ascend.app.domain.QuestEngine
import com.ascend.app.domain.Schedule
import com.ascend.app.domain.WinterMode
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(state: AppState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Repository.exportJson().toByteArray()) }
            Toast.makeText(ctx, "Exported", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            val ok = text != null && Repository.importJson(text)
            Toast.makeText(ctx, if (ok) "Imported" else "That file isn't an Ascend backup", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        item { ProfileCard(state) }
        item { ModeCard(state) }
        item { ScheduleCard(state) }
        item {
            AppCard {
                Text("Winter mode", fontWeight = FontWeight.SemiBold)
                Text("Auto = December to February. XP x1.2, indoor Plan B counts.", fontSize = 12.sp, color = C.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WinterMode.entries.forEach { w ->
                        FilterChip(selected = state.profile.winterMode == w, onClick = { Repository.edit { it.copy(profile = it.profile.copy(winterMode = w)) } },
                            label = { Text(w.name.lowercase().replaceFirstChar { c -> c.uppercase() }) })
                    }
                }
            }
        }
        item {
            AppCard {
                Text("Your data", fontWeight = FontWeight.SemiBold)
                Text("Everything stays on this phone. Export a backup now and then (e.g. to Google Drive), and import it on a new phone.", fontSize = 12.sp, color = C.muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exporter.launch("ascend-backup-${Repository.today()}.json") }) { Text("Export") }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "*/*")) }) { Text("Import") }
                }
            }
        }
        item { Text("Ascend v1 · made for you, not for the app store.", color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
    }
}

private fun String.num(): Double? = replace(',', '.').trim().toDoubleOrNull()

@Composable
private fun ProfileCard(state: AppState) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(state.profile.name) }
    var start by remember { mutableStateOf(state.profile.startKg.toString()) }
    var target by remember { mutableStateOf(state.profile.targetKg.toString()) }
    var km by remember { mutableStateOf(state.profile.baseWeeklyKm.toString()) }
    AppCard {
        Text("Profile", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(start, { start = it }, label = { Text("Start kg") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(target, { target = it }, label = { Text("Target kg") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        OutlinedTextField(km, { km = it }, label = { Text("Starting km per week") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            Repository.edit {
                it.copy(profile = it.profile.copy(
                    name = name.ifBlank { "Player" },
                    startKg = start.num() ?: it.profile.startKg,
                    targetKg = target.num() ?: it.profile.targetKg,
                    baseWeeklyKm = km.num()?.coerceIn(3.0, 75.0) ?: it.profile.baseWeeklyKm,
                ))
            }
            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show()
        }) { Text("Save profile") }
    }
}

@Composable
private fun ModeCard(state: AppState) {
    val ctx = LocalContext.current
    val owes = QuestEngine.pendingPenalties(state, Repository.today()).isNotEmpty()
    AppCard {
        Text("Difficulty", fontWeight = FontWeight.SemiBold)
        if (owes) Text("You owe a penalty quest. Finish it before switching to an easier mode.", fontSize = 12.sp, color = C.hp)
        Difficulty.entries.forEach { d ->
            val easier = d.ordinal < state.mode.ordinal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    if (owes && easier) Toast.makeText(ctx, "Finish your penalty first", Toast.LENGTH_SHORT).show()
                    else Repository.edit { it.copy(profile = it.profile.copy(difficulty = d)) }
                },
            ) {
                RadioButton(selected = state.mode == d, onClick = null)
                Column(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
                    Text(d.label, fontWeight = FontWeight.SemiBold)
                    Text(d.tagline, fontSize = 12.sp, color = C.muted)
                }
            }
        }
    }
}

@Composable
private fun DayChips(label: String, selected: List<Int>, max: Int, onChange: (List<Int>) -> Unit) {
    Column {
        Text(label, fontSize = 12.sp, color = C.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { d ->
                val on = d.value in selected
                FilterChip(selected = on, onClick = {
                    val next = if (on) selected - d.value else (selected + d.value).takeLast(max)
                    if (next.isNotEmpty()) onChange(next.sorted())
                }, label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)) })
            }
        }
    }
}

@Composable
private fun ScheduleCard(state: AppState) {
    val s = state.schedule
    fun set(change: (Schedule) -> Schedule) = Repository.edit { it.copy(schedule = change(it.schedule)) }
    AppCard {
        Text("Weekly schedule", fontWeight = FontWeight.SemiBold)
        Text("Changes apply from today. Run 1 = speed, run 2 = recovery, run 3 = long.", fontSize = 12.sp, color = C.muted)
        DayChips("Run days (3)", s.runDays, 4) { v -> set { it.copy(runDays = v) } }
        DayChips("Strength days (2)", s.gymDays, 3) { v -> set { it.copy(gymDays = v) } }
        DayChips("Study days", s.studyDays, 7) { v -> set { it.copy(studyDays = v) } }
        DayChips("Money days", s.moneyDays, 7) { v -> set { it.copy(moneyDays = v) } }
        DayChips("Weigh-in day", listOf(s.weighInDay), 1) { v -> set { it.copy(weighInDay = v.first()) } }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper("Read min", "${s.readMinutes}", { set { it.copy(readMinutes = (it.readMinutes - 5).coerceAtLeast(5)) } }, { set { it.copy(readMinutes = it.readMinutes + 5) } }, Modifier.weight(1f))
            Stepper("Study min", "${s.studyMinutes}", { set { it.copy(studyMinutes = (it.studyMinutes - 5).coerceAtLeast(10)) } }, { set { it.copy(studyMinutes = it.studyMinutes + 5) } }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper("Money min", "${s.moneyMinutes}", { set { it.copy(moneyMinutes = (it.moneyMinutes - 5).coerceAtLeast(10)) } }, { set { it.copy(moneyMinutes = it.moneyMinutes + 5) } }, Modifier.weight(1f))
            Stepper("Sleep h", "${s.sleepHours}", { set { it.copy(sleepHours = (it.sleepHours - 0.5).coerceAtLeast(6.0)) } }, { set { it.copy(sleepHours = (it.sleepHours + 0.5).coerceAtMost(10.0)) } }, Modifier.weight(1f))
        }
    }
}

package com.ascend.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.data.Repository
import com.ascend.app.domain.Difficulty
import com.ascend.app.domain.Game
import com.ascend.app.domain.Profile

@Composable
fun OnboardingScreen() {
    var name by remember { mutableStateOf("") }
    var kg by remember { mutableStateOf("66") }
    var target by remember { mutableStateOf("72") }
    var km by remember { mutableStateOf("8") }
    var mode by remember { mutableStateOf(Difficulty.NEW_LIFE) }
    fun String.num() = replace(',', '.').trim().toDoubleOrNull()

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("ASCEND", fontSize = 34.sp, fontWeight = FontWeight.Black, color = C.violet)
        Text("Your life is the game. Real runs, real lifts, real hours won back from your phone, all turned into XP.", color = C.muted)
        AppCard {
            Text("How it works", fontWeight = FontWeight.Bold)
            Text(
                "• Daily quests for running, gym, sleep, eating, reading, studying and money.\n" +
                    "• Log anything in under 30 seconds. Quests complete themselves.\n" +
                    "• Each skill has its own level and HP. Miss quests and HP drops.\n" +
                    "• Your momentum never resets to zero, and rest days never count as misses.\n" +
                    "• A new season of goals every 6 weeks.",
                fontSize = 13.sp, color = C.muted,
            )
        }
        OutlinedTextField(name, { name = it }, label = { Text("Your name or player name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(kg, { kg = it }, label = { Text("Weight now (kg)") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(target, { target = it }, label = { Text("Target (kg)") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        OutlinedTextField(km, { km = it }, label = { Text("Running km per week right now") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

        Text("Pick your difficulty", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("You can change it later. Food and sleep are never used as punishment.", fontSize = 12.sp, color = C.muted)
        Difficulty.entries.forEach { d ->
            AppCard(modifier = Modifier.clickable { mode = d }, border = if (mode == d) C.violet else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == d, onClick = { mode = d })
                    Column {
                        Text(d.label, fontWeight = FontWeight.Bold, color = if (d.appLock) C.hp else C.text)
                        Text(d.tagline, fontSize = 12.sp, color = C.muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                val profile = Profile(
                    name = name.ifBlank { "Player" },
                    startKg = kg.num() ?: 66.0,
                    targetKg = target.num() ?: 72.0,
                    baseWeeklyKm = (km.num() ?: 8.0).coerceIn(3.0, 75.0),
                    difficulty = mode,
                )
                Repository.edit { Game.completeOnboarding(it, profile, Repository.today()) }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Start week 1", fontSize = 16.sp) }
    }
}

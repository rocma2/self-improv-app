package com.ascend.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.data.Repository
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Fmt
import com.ascend.app.domain.Game
import com.ascend.app.domain.Point
import com.ascend.app.domain.QuestEngine
import com.ascend.app.focus.ScreenTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun FocusScreen(state: AppState) {
    val ctx = LocalContext.current
    val today = Repository.today()
    // Re-check permissions every few seconds so the screen updates when you come back from Settings.
    val access by produceState(ScreenTime.hasUsageAccess(ctx)) { while (true) { value = ScreenTime.hasUsageAccess(ctx); delay(2000) } }
    val lockOn by produceState(ScreenTime.lockServiceEnabled(ctx)) { while (true) { value = ScreenTime.lockServiceEnabled(ctx); delay(2000) } }
    val usedToday by produceState<Int?>(null, access, state.screen.packages) {
        while (true) {
            value = withContext(Dispatchers.IO) { ScreenTime.minutesOn(ctx, state.screen.packages, today) }
            delay(60_000)
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("📵 Take your time back", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Your TikTok + YouTube budget drops by about 1 hour every week. Every hour under your old average is XP.", color = C.muted, fontSize = 13.sp)
        }

        if (!access) item {
            AppCard(border = C.gold) {
                Text("Step 1: allow usage access", fontWeight = FontWeight.Bold)
                Text("This lets Ascend see how many minutes TikTok and YouTube were open. Nothing leaves your phone. In the list, tap Ascend and turn it on.", fontSize = 13.sp, color = C.muted)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open usage access") }
            }
        } else if (!state.screen.enabled) item { StartBudget() }
        else {
            item { TodayBudget(state, usedToday) }
            item { History(state) }
        }

        if (access && state.screen.enabled) item { LockCard(state, lockOn) }
        if (state.screen.enabled) item { BudgetSettings(state) }
    }
}

@Composable
private fun StartBudget() {
    val ctx = LocalContext.current
    val avg by produceState<Int?>(null) { value = withContext(Dispatchers.IO) { ScreenTime.averageDaily(ctx, com.ascend.app.domain.ScreenSettings.DEFAULT_BLOCKED) } }
    AppCard(border = C.violet) {
        Text("Step 2: set your starting point", fontWeight = FontWeight.Bold)
        val a = avg
        if (a == null) Text("Measuring your last 7 days…", color = C.muted, fontSize = 13.sp)
        else {
            Text("Your last 7 days: about ${Fmt.hours(a)} a day on TikTok + YouTube.", fontSize = 14.sp)
            val start = (((a.coerceAtLeast(60) + 29) / 30) * 30)
            Text("Week 1 budget: ${Fmt.hours(start)} a day. Then ${Fmt.hours((start - 60).coerceAtLeast(60))}, then ${Fmt.hours((start - 120).coerceAtLeast(60))}… down to 1 h.", color = C.muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { Repository.edit { Game.enableScreen(it, Repository.today(), start) } }) { Text("Start my budget") }
        }
    }
}

@Composable
private fun TodayBudget(state: AppState, used: Int?) {
    val today = Repository.today()
    val budget = QuestEngine.screenBudget(state, today)
    val next = QuestEngine.screenBudget(state, today.plusWeeks(1))
    AppCard {
        Text("Today", color = C.muted, fontSize = 13.sp)
        Text("${used?.let { Fmt.hours(it) } ?: "…"} of ${Fmt.hours(budget)}", fontSize = 30.sp, fontWeight = FontWeight.Black,
            color = if ((used ?: 0) >= budget) C.hp else C.text)
        Bar(((used ?: 0).toFloat() / budget).coerceIn(0f, 1f), if ((used ?: 0) >= budget) C.hp else C.cyan, height = 12)
        Spacer(Modifier.height(6.dp))
        val left = budget - (used ?: 0)
        Text(if (left > 0) "${Fmt.hours(left)} left today." else "Budget used up for today.", fontSize = 13.sp, color = C.muted)
        Text("Next week's budget: ${Fmt.hours(next)} a day. Your old average: ${Fmt.hours(state.screen.baselineMin)}.", fontSize = 13.sp, color = C.muted)
    }
}

@Composable
private fun History(state: AppState) {
    val days = state.screenDays.sortedBy { it.date }.takeLast(14)
    val total = state.screenDays.sumOf { (state.screen.baselineMin - it.usedMin).coerceAtLeast(0) }
    AppCard {
        Text("Last 14 days (hours)", fontWeight = FontWeight.SemiBold)
        Text("Total won back: ${Fmt.hours(total)} · that's ${(total / 60) / 24} full days of your life.", fontSize = 12.sp, color = C.muted)
        Spacer(Modifier.height(6.dp))
        BarChart(days.map { Point(java.time.LocalDate.parse(it.date).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH), it.usedMin / 60.0) },
            C.cyan, target = days.lastOrNull()?.budgetMin?.div(60.0))
    }
}

@Composable
private fun LockCard(state: AppState, lockOn: Boolean) {
    val ctx = LocalContext.current
    AppCard(border = if (state.mode.appLock && !lockOn) C.hp else null) {
        Text("🔒 App lock", fontWeight = FontWeight.Bold)
        if (!state.mode.appLock) {
            Text("Your mode (${state.mode.label}) doesn't lock apps. Obsessed and New life do.", fontSize = 13.sp, color = C.muted)
            return@AppCard
        }
        Text(
            if (lockOn) "On. TikTok and YouTube close when you owe a penalty quest or today's budget is used up."
            else "Off. Turn on \"Ascend app lock\" so TikTok and YouTube lock when you owe a penalty or pass your budget.",
            fontSize = 13.sp, color = if (lockOn) C.green else C.muted,
        )
        if (!lockOn) {
            Spacer(Modifier.height(8.dp))
            Text("1. Tap the button below, find Ascend (maybe under \"Downloaded apps\"), turn it on.", fontSize = 13.sp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text("2. If Android says \"Restricted setting\": open App info below, tap ⋮ (top right) → \"Allow restricted settings\", then try step 1 again.", fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Turn on lock") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("App info") }
            }
        }
        val pending = QuestEngine.pendingPenalties(state, Repository.today())
        if (pending.isNotEmpty()) Text("Locked right now: ${pending.first().title}", color = C.hp, fontSize = 13.sp)
    }
}

@Composable
private fun BudgetSettings(state: AppState) {
    var drop by remember(state.screen.weeklyDropMin) { mutableStateOf(state.screen.weeklyDropMin) }
    var floor by remember(state.screen.floorMin) { mutableStateOf(state.screen.floorMin) }
    LaunchedEffect(drop, floor) {
        if (drop != state.screen.weeklyDropMin || floor != state.screen.floorMin) {
            Repository.edit { it.copy(screen = it.screen.copy(weeklyDropMin = drop, floorMin = floor)) }
        }
    }
    AppCard {
        Text("Budget settings", fontWeight = FontWeight.SemiBold)
        Text("Drop per week", fontSize = 12.sp, color = C.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 60, 90).forEach { v -> FilterChip(selected = drop == v, onClick = { drop = v }, label = { Text(Fmt.hours(v)) }) }
        }
        Text("Lowest daily budget", fontSize = 12.sp, color = C.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 60, 90, 120).forEach { v -> FilterChip(selected = floor == v, onClick = { floor = v }, label = { Text(Fmt.hours(v)) }) }
        }
        Spacer(Modifier.height(4.dp))
        Text("Tracked apps: TikTok, YouTube.", fontSize = 12.sp, color = C.muted, modifier = Modifier.fillMaxWidth())
    }
}

package com.ascend.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import com.ascend.app.focus.Reminders
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascend.app.data.Repository
import com.ascend.app.domain.GameEvent
import com.ascend.app.domain.Leveling
import com.ascend.app.domain.Skill
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repository.init(this)
        Reminders.createChannel(this)
        Reminders.scheduleAll(this)
        if (!Reminders.canNotify(this) && Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { AscendTheme { AscendApp() } }
    }

    override fun onResume() {
        super.onResume()
        Repository.refresh()
    }
}

enum class Tab(val label: String, val emoji: String) { TODAY("Today", "⚔️"), TRAIN("Train", "🏃"), STATS("Stats", "📊"), FOCUS("Focus", "📵") }

/** Which quick-log sheet is open. */
sealed class LogTarget {
    data class Run(val km: Double? = null, val type: com.ascend.app.domain.RunType? = null) : LogTarget()
    data object Gym : LogTarget()
    data object Weight : LogTarget()
    data object Sleep : LogTarget()
    data class Minutes(val skill: Skill, val suggested: Int) : LogTarget()
    data object Bike : LogTarget()
}

@Composable
fun AscendApp() {
    val state by Repository.state.collectAsStateWithLifecycle()
    if (!state.onboarded) {
        OnboardingScreen()
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<LogTarget?>(null) }
    var levelUp by remember { mutableStateOf<GameEvent.LevelUp?>(null) }
    var showRecap by remember { mutableStateOf<YearMonth?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        Repository.events.collect { e ->
            when (e) {
                is GameEvent.LevelUp -> levelUp = e
                is GameEvent.SkillLevelUp -> snackbar.showSnackbar("${e.skill.emoji} ${e.skill.label} reached level ${e.level}!", duration = SnackbarDuration.Short)
                is GameEvent.SkillLevelDown -> snackbar.showSnackbar("💔 ${e.skill.label} ran out of HP and dropped to level ${e.level}.", duration = SnackbarDuration.Long)
                is GameEvent.QuestDone -> snackbar.showSnackbar("✅ ${e.title}  +${e.xp} XP", duration = SnackbarDuration.Short)
                is GameEvent.NewRecord -> snackbar.showSnackbar("🏅 New record! ${e.title}", duration = SnackbarDuration.Long)
                is GameEvent.BadgeEarned -> snackbar.showSnackbar("${e.badge.emoji} Badge unlocked: ${e.badge.title}", duration = SnackbarDuration.Long)
                is GameEvent.Message -> snackbar.showSnackbar(e.text, duration = SnackbarDuration.Short)
            }
        }
    }

    // First open of a new month: show last month's recap once.
    LaunchedEffect(state.lastRecapMonth) {
        val now = YearMonth.now()
        val last = now.minusMonths(1)
        val started = runCatching { YearMonth.from(java.time.LocalDate.parse(state.profile.planStart)) }.getOrNull()
        if (state.lastRecapMonth != now.toString() && started != null && started <= last) showRecap = last
    }

    BackHandler(enabled = settings) { settings = false }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!settings) NavigationBar(containerColor = C.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.emoji, fontSize = 20.sp) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = C.violet.copy(alpha = 0.3f), selectedTextColor = C.text, unselectedTextColor = C.muted),
                    )
                }
            }
        },
        containerColor = C.bg,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (settings) SettingsScreen(state, onBack = { settings = false })
            else when (tab) {
                Tab.TODAY -> TodayScreen(state, onLog = { sheet = it }, onSettings = { settings = true }, onFocus = { tab = Tab.FOCUS })
                Tab.TRAIN -> TrainScreen(state, onLog = { sheet = it })
                Tab.STATS -> StatsScreen(state)
                Tab.FOCUS -> FocusScreen(state)
            }
        }
    }

    sheet?.let { LogSheet(state, it, onDismiss = { sheet = null }) }

    levelUp?.let { lv ->
        AlertDialog(
            onDismissRequest = { levelUp = null },
            confirmButton = { TextButton(onClick = { levelUp = null }) { Text("Keep going") } },
            title = { Text("⬆ LEVEL UP", color = C.gold, fontWeight = FontWeight.Black, fontSize = 28.sp) },
            text = {
                val next = Leveling.nextTitle(lv.level)
                Text(
                    "You are now level ${lv.level}: ${lv.title}." + (next?.let { "\nNext title at level ${it.first}: ${it.second}." } ?: ""),
                    textAlign = TextAlign.Center,
                )
            },
            containerColor = C.surface2,
        )
    }

    showRecap?.let { month ->
        RecapDialog(state, month, onDismiss = {
            showRecap = null
            Repository.edit { it.copy(lastRecapMonth = YearMonth.now().toString()) }
        })
    }
}

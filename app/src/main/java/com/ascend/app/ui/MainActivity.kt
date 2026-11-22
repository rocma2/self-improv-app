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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
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

enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TODAY("Today", androidx.compose.material.icons.Icons.Filled.Home),
    TRAIN("Train", androidx.compose.material.icons.Icons.Filled.DateRange),
    GATES("Gates", androidx.compose.material.icons.Icons.Filled.Star),
    STATS("Stats", androidx.compose.material.icons.Icons.Filled.Person),
    FOCUS("Focus", androidx.compose.material.icons.Icons.Filled.Lock),
}

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
    var rankUp by remember { mutableStateOf<String?>(null) }
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
                is GameEvent.RankUp -> rankUp = e.rank
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
        snackbarHost = { SnackbarHost(snackbar) { data -> SystemSnackbar(data.visuals.message) } },
        bottomBar = { if (!settings) HoloNavBar(tab) { tab = it } },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.background(AppBackground),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (settings) SettingsScreen(state, onBack = { settings = false })
            else when (tab) {
                Tab.TODAY -> TodayScreen(state, onLog = { sheet = it }, onSettings = { settings = true }, onFocus = { tab = Tab.FOCUS })
                Tab.TRAIN -> TrainScreen(state, onLog = { sheet = it })
                Tab.GATES -> GatesScreen(state)
                Tab.STATS -> StatsScreen(state)
                Tab.FOCUS -> FocusScreen(state)
            }
        }
    }

    sheet?.let { LogSheet(state, it, onDismiss = { sheet = null }) }

    rankUp?.let { r -> RankUpDialog(r) { rankUp = null } }
    if (rankUp == null) levelUp?.let { lv -> LevelUpDialog(lv) { levelUp = null } }

    showRecap?.let { month ->
        RecapDialog(state, month, onDismiss = {
            showRecap = null
            Repository.edit { it.copy(lastRecapMonth = YearMonth.now().toString()) }
        })
    }
}

@Composable
private fun LevelUpDialog(lv: GameEvent.LevelUp, onDismiss: () -> Unit) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 300f)) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            AppCard(title = "Notification", pulse = true, solid = true) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SysText("Level up!", size = 30.sp, color = C.text, glowColor = C.cyan)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
                    RankEmblem(Leveling.rank(Repository.state.value.totalXp), C.blue, 84.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
                    Text("You reached level ${lv.level}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Title: ${lv.title}", color = C.muted)
                    Leveling.nextTitle(lv.level)?.let { Text("Next title at level ${it.first}: ${it.second}", color = C.muted, fontSize = 13.sp) }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                    androidx.compose.material3.Button(onClick = onDismiss, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp * 2)) { Text("ACCEPT") }
                }
            }
        }
    }
}

/** System-window style toast for every in-game message. */
@Composable
private fun SystemSnackbar(message: String) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AppCard(solid = true, border = if (message.contains("💔") || message.contains("collapsed")) C.hp else C.cyan) {
            Text(message.removePrefix("[SYSTEM] "), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.text)
        }
    }
}

@Composable
private fun RankUpDialog(rank: String, onDismiss: () -> Unit) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.3f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 200f)) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
            AppCard(title = "Rank up", border = C.gold, pulse = true, solid = true) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    SysText("You are now", size = 16.sp, glowColor = C.gold)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
                    RankEmblem(rank, C.gold, 110.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
                    SysText("$rank-rank hunter", size = 24.sp, glowColor = C.gold)
                    Text("+5 stat points · +500 gold", color = C.gold, fontWeight = FontWeight.Bold)
                    Leveling.nextRank(Repository.state.value.totalXp)?.let { (r, xp, _) ->
                        Text("Rank $r needs %,d total XP. Every rank takes 3x the last.".format(xp), color = C.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                    androidx.compose.material3.Button(onClick = onDismiss, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp * 2)) { Text("ACCEPT") }
                }
            }
        }
    }
}

/** Floating pill-shaped nav bar over the glass screens. */
@Composable
private fun HoloNavBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().height(64.dp)
                .background(androidx.compose.ui.graphics.Color(0xD90A142D), RoundedCornerShape(32.dp))
                .border(1.dp, C.blue.copy(alpha = 0.45f), RoundedCornerShape(32.dp)),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    androidx.compose.material3.Icon(t.icon, contentDescription = t.label, tint = if (on) C.cyan else C.muted, modifier = Modifier.size(22.dp))
                    Text(t.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) C.cyan else C.muted)
                }
            }
        }
    }
}

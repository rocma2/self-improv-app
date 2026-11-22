package com.ascend.app.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class Skill(val label: String, val emoji: String) {
    RUNNING("Running", "🏃"),
    GYM("Gym", "🏋️"),
    SLEEP("Sleep", "😴"),
    WEIGHT("Weight gain", "🍽️"),
    READING("Reading", "📖"),
    STUDYING("Studying", "🎓"),
    MONEY("Money", "💰"),
    FOCUS("Focus", "📵"),
}

/**
 * Difficulty decides how hard misses hit. XP rises with difficulty so harder modes level faster.
 * weeklyMissLimit: number of misses in a week at which the weekly chest is lost.
 */
@Serializable
enum class Difficulty(
    val label: String,
    val tagline: String,
    val xpMultiplier: Double,
    val hpLoss: Int,
    val penaltyQuest: Boolean,
    val appLock: Boolean,
    val weeklyMissLimit: Int,
) {
    SOFT("Soft", "No punishments. XP x0.8. Good for a rough week.", 0.8, 0, false, false, 99),
    EASY("Easy", "Misses cost a little HP. XP x1.0.", 1.0, 5, false, false, 6),
    HARDCORE("Hardcore", "Misses cost HP and add a penalty quest. XP x1.2.", 1.2, 10, true, false, 4),
    OBSESSED("Obsessed", "Penalty quests lock TikTok and YouTube. XP x1.35.", 1.35, 15, true, true, 4),
    NEW_LIFE("New life", "Max stakes: big HP loss, penalty quests, app lock, 3 misses = no weekly chest. XP x1.5.", 1.5, 20, true, true, 3),
}

@Serializable
enum class WinterMode { AUTO, ON, OFF }

@Serializable
data class Profile(
    val name: String = "Player",
    val heightCm: Int = 178,
    val startKg: Double = 66.0,
    val targetKg: Double = 72.0,
    val baseWeeklyKm: Double = 8.0,
    val difficulty: Difficulty = Difficulty.NEW_LIFE,
    val planStart: String = LocalDate.now().toString(),
    val winterMode: WinterMode = WinterMode.AUTO,
)

@Serializable
data class SkillState(val xp: Long = 0, val hp: Int = 100)

@Serializable
data class Momentum(
    val streak: Int = 0,
    val best: Int = 0,
    val freezes: Int = 1,
    val towardFreeze: Int = 0,
)

/** Days use java.time.DayOfWeek values: 1 = Monday ... 7 = Sunday. */
@Serializable
data class Schedule(
    val runDays: List<Int> = listOf(2, 4, 6),
    val gymDays: List<Int> = listOf(1, 5),
    val studyDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val moneyDays: List<Int> = listOf(1, 3, 6),
    val weighInDay: Int = 7,
    val readMinutes: Int = 20,
    val studyMinutes: Int = 45,
    val moneyMinutes: Int = 30,
    val sleepHours: Double = 7.5,
)

@Serializable
enum class RunType(val label: String) {
    EASY("Easy"), SPEED("Speed"), RECOVERY("Recovery"), LONG("Long"), TEST("5K test"), OTHER("Other")
}

@Serializable
enum class Surface(val label: String) { OUTDOOR("Outdoor"), TREADMILL("Treadmill"), INDOOR_BIKE("Indoor bike") }

@Serializable
data class RunLog(
    val id: Long,
    val date: String,
    val km: Double,
    val seconds: Int,
    val type: RunType = RunType.EASY,
    val surface: Surface = Surface.OUTDOOR,
    val tempC: Int? = null,
)

@Serializable
data class LiftEntry(val exercise: String, val kg: Double, val reps: Int, val sets: Int)

@Serializable
data class GymLog(val id: Long, val date: String, val entries: List<LiftEntry>, val homeWorkout: Boolean = false)

@Serializable
data class WeighIn(val date: String, val kg: Double)

/** Sleep for the night that ended on [date] (the morning you woke up). */
@Serializable
data class SleepLog(val date: String, val hours: Double)

@Serializable
data class MinutesLog(val id: Long, val date: String, val skill: Skill, val minutes: Int)

@Serializable
data class BikeLog(val id: Long, val date: String, val km: Double)

@Serializable
enum class QuestStatus { DONE, MISSED, REST }

@Serializable
data class QuestRecord(val questId: String, val date: String, val skill: Skill, val status: QuestStatus, val xp: Int = 0)

@Serializable
data class Penalty(
    val id: Long,
    val missedDate: String,
    val due: String,
    val skill: Skill,
    val title: String,
    val detail: String,
    val minutes: Int = 0,
    val done: Boolean = false,
    val doneDate: String? = null,
)

@Serializable
data class WeeklyResult(val weekStart: String, val misses: Int, val rewarded: Boolean, val xp: Int)

@Serializable
enum class GoalMetric { RUN_KM, RUN_COUNT, GYM_COUNT, READ_MIN, STUDY_MIN, WEIGHT_GAIN, SQUAT_GAIN, SLEEP_NIGHTS, FOCUS_HOURS }

@Serializable
data class SeasonGoal(val id: String, val title: String, val metric: GoalMetric, val target: Double, val baseline: Double = 0.0)

@Serializable
data class Season(
    val number: Int,
    val start: String,
    val end: String,
    val goals: List<SeasonGoal>,
    val claimed: List<String> = emptyList(),
)

@Serializable
data class ScreenSettings(
    val enabled: Boolean = false,
    val packages: List<String> = DEFAULT_BLOCKED,
    val baselineMin: Int = 600,
    val startDate: String = "",
    val weeklyDropMin: Int = 60,
    val floorMin: Int = 60,
) {
    companion object {
        val DEFAULT_BLOCKED = listOf(
            "com.zhiliaoapp.musically", // TikTok
            "com.ss.android.ugc.trill", // TikTok (other regions)
            "com.google.android.youtube", // YouTube
        )
    }
}

@Serializable
data class ScreenDay(val date: String, val usedMin: Int, val budgetMin: Int, val xp: Int)

@Serializable
data class AppState(
    val version: Int = 1,
    val onboarded: Boolean = false,
    val profile: Profile = Profile(),
    val skills: Map<Skill, SkillState> = Skill.entries.associateWith { SkillState() },
    val totalXp: Long = 0,
    val dailyXp: Map<String, Int> = emptyMap(),
    val momentum: Momentum = Momentum(),
    val schedule: Schedule = Schedule(),
    val runs: List<RunLog> = emptyList(),
    val gym: List<GymLog> = emptyList(),
    val weighIns: List<WeighIn> = emptyList(),
    val sleeps: List<SleepLog> = emptyList(),
    val minutes: List<MinutesLog> = emptyList(),
    val bikes: List<BikeLog> = emptyList(),
    val foodDays: List<String> = emptyList(),
    val questRecords: List<QuestRecord> = emptyList(),
    val penalties: List<Penalty> = emptyList(),
    val restDays: List<String> = emptyList(),
    val badges: Map<String, String> = emptyMap(),
    val weeklyResults: List<WeeklyResult> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val screen: ScreenSettings = ScreenSettings(),
    val screenDays: List<ScreenDay> = emptyList(),
    val lastEvaluated: String? = null,
    val nextId: Long = 1,
    val lastRecapMonth: String? = null,
    // RPG layer
    val gold: Int = 0,
    val statPoints: Int = 0,
    val allocated: Map<String, Int> = emptyMap(),
    val inventory: Map<Item, Int> = mapOf(Item.MYSTERY_BOX to 1),
    val gates: List<Gate> = emptyList(),
    val job: JobClass? = null,
    val jobTrialStart: String? = null,
    val jobTrialBase: Int = 0,
    val dailyRewardDays: List<String> = emptyList(),
    val customRewards: List<CustomReward> = emptyList(),
    val defeatedBosses: List<String> = emptyList(),
    val skipDays: List<String> = emptyList(),
    val xpBoostDay: String? = null,
) {
    fun skill(s: Skill): SkillState = skills[s] ?: SkillState()
    val mode: Difficulty get() = profile.difficulty
    val latestWeight: Double get() = weighIns.maxByOrNull { it.date }?.kg ?: profile.startKg
}

/** Things that happened that the UI should celebrate or warn about. */
sealed class GameEvent {
    data class LevelUp(val level: Int, val title: String) : GameEvent()
    data class SkillLevelUp(val skill: Skill, val level: Int) : GameEvent()
    data class SkillLevelDown(val skill: Skill, val level: Int) : GameEvent()
    data class QuestDone(val title: String, val xp: Int) : GameEvent()
    data class NewRecord(val title: String) : GameEvent()
    data class BadgeEarned(val badge: Badge) : GameEvent()
    data class Message(val text: String) : GameEvent()
    data class RankUp(val rank: String) : GameEvent()
}

fun String.toDate(): LocalDate = LocalDate.parse(this)
fun LocalDate.iso(): String = toString()
fun LocalDate.weekStart(): LocalDate = minusDays((dayOfWeek.value - 1).toLong())

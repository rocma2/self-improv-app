package com.ascend.app.domain

import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

data class PlannedRun(val type: RunType, val km: Double, val title: String, val detail: String)

data class PlannedLift(val exercise: String, val sets: Int, val reps: Int, val suggestedKg: Double, val note: String = "")

data class GymSession(val name: String, val lifts: List<PlannedLift>, val deload: Boolean)

object Exercises {
    const val SQUAT = "Back squat"
    const val DEADLIFT = "Deadlift"
    const val OHP = "Overhead press"
    const val BENCH = "Bench press"
    const val PULLUP = "Pull-ups"
    const val SPLIT_SQUAT = "Bulgarian split squat"
    const val SL_RDL = "Single-leg RDL"
    const val PLANK = "Plank (sec)"
    const val KNEE_RAISE = "Hanging knee raise"

    val all = listOf(SQUAT, DEADLIFT, OHP, BENCH, PULLUP, SPLIT_SQUAT, SL_RDL, PLANK, KNEE_RAISE)

    /** Exercises where kg is the load you progress. Others track reps or seconds. */
    val loaded = setOf(SQUAT, DEADLIFT, OHP, BENCH, SPLIT_SQUAT, SL_RDL)

    val startKg = mapOf(SQUAT to 40.0, DEADLIFT to 50.0, OHP to 25.0, BENCH to 40.0, SPLIT_SQUAT to 10.0, SL_RDL to 12.0)
}

object TrainingPlan {
    const val MAX_WEEKLY_KM = 75.0

    fun isWinter(state: AppState, date: LocalDate): Boolean = when (state.profile.winterMode) {
        WinterMode.ON -> true
        WinterMode.OFF -> false
        WinterMode.AUTO -> date.month in setOf(Month.DECEMBER, Month.JANUARY, Month.FEBRUARY)
    }

    fun planStart(state: AppState): LocalDate = runCatching { state.profile.planStart.toDate() }.getOrDefault(LocalDate.now()).weekStart()

    /** Week 1 is the week the plan started. */
    fun weekIndex(state: AppState, date: LocalDate): Int =
        (ChronoUnit.WEEKS.between(planStart(state), date.weekStart()).toInt() + 1).coerceAtLeast(1)

    fun weekStartOf(state: AppState, week: Int): LocalDate = planStart(state).plusWeeks((week - 1).toLong())

    /** Every 4th week is lighter so the body absorbs the work. */
    fun isDeload(week: Int): Boolean = week % 4 == 0

    fun isTestWeek(week: Int): Boolean = week % 12 == 10

    fun runEquivalentKm(run: RunLog): Double = if (run.surface == Surface.INDOOR_BIKE) run.km / 3.0 else run.km

    fun runKmBetween(state: AppState, from: LocalDate, toInclusive: LocalDate): Double =
        state.runs.filter { val d = it.date.toDate(); !d.isBefore(from) && !d.isAfter(toInclusive) }.sumOf { runEquivalentKm(it) }

    fun weekRunKm(state: AppState, weekStart: LocalDate): Double = runKmBetween(state, weekStart, weekStart.plusDays(6))

    fun weekBikeKm(state: AppState, weekStart: LocalDate): Double =
        state.bikes.filter { val d = it.date.toDate(); !d.isBefore(weekStart) && !d.isAfter(weekStart.plusDays(6)) }.sumOf { it.km }

    /**
     * Weekly running target. Grows at most 10% per week, only when the last week was actually done,
     * holds when it was mostly done, steps back when it was missed. Deload weeks drop to 70%.
     */
    fun targetKm(state: AppState, week: Int, today: LocalDate): Double {
        val currentWeek = weekIndex(state, today)
        var lastNormal = state.profile.baseWeeklyKm.coerceIn(3.0, MAX_WEEKLY_KM)
        var target = lastNormal
        for (w in 2..week) {
            if (isDeload(w)) {
                target = lastNormal * 0.7
                continue
            }
            val prev = w - 1
            val next = when {
                isDeload(prev) -> lastNormal * 1.05
                prev >= currentWeek -> lastNormal * 1.10 // future: assume it goes to plan
                else -> {
                    val actual = weekRunKm(state, weekStartOf(state, prev))
                    when {
                        actual >= lastNormal * 0.9 -> lastNormal * 1.10
                        actual >= lastNormal * 0.6 -> lastNormal
                        else -> max(lastNormal * 0.9, 5.0)
                    }
                }
            }
            lastNormal = next.coerceAtMost(MAX_WEEKLY_KM)
            target = lastNormal
        }
        return roundHalf(target)
    }

    private fun roundHalf(v: Double) = (v * 2).roundToInt() / 2.0

    private val speedWorkouts = listOf(
        "6 × 400 m fast, 90 s easy jog between",
        "5 × 800 m at 5K effort, 2 min jog between",
        "20 min tempo: comfortably hard, you can say 3 words",
        "8 × 30 s uphill hard, walk down to recover",
        "4 × 1 km at goal pace 3:48/km, 2 min jog between",
        "10 × 1 min hard / 1 min easy",
        "3 × 1.6 km at threshold, 2 min jog between",
        "2 × 2 km at 4:00/km, 3 min jog between",
    )

    fun speedWorkout(week: Int): String = speedWorkouts[(week - 1).mod(speedWorkouts.size)]

    /** The planned run for [date], or null when it is not a run day. */
    fun plannedRun(state: AppState, date: LocalDate, today: LocalDate = date): PlannedRun? {
        val days = state.schedule.runDays.sorted()
        val idx = days.indexOf(date.dayOfWeek.value)
        if (idx < 0) return null
        val week = weekIndex(state, date)
        val total = targetKm(state, week, today)
        val types = days.indices.map { i ->
            when (i) {
                0 -> RunType.SPEED
                1 -> RunType.RECOVERY
                2 -> RunType.LONG
                else -> RunType.EASY
            }
        }
        val share = mapOf(RunType.SPEED to 0.30, RunType.RECOVERY to 0.25, RunType.LONG to 0.45, RunType.EASY to 0.25)
        val shareSum = types.sumOf { share.getValue(it) }
        var type = types[idx]
        val km = roundHalf(max(2.0, total * share.getValue(type) / shareSum))
        val deload = isDeload(week)
        val winter = isWinter(state, date)
        val planB = if (winter) " Winter Plan B: treadmill counts the same, indoor bike counts 1 km per 3 km biked." else ""
        val pace = Records.easyPaceHint(state)
        return when {
            type == RunType.SPEED && isTestWeek(week) -> {
                type = RunType.TEST
                PlannedRun(type, max(km, 7.0), "5K time trial",
                    "Warm up 15 min easy, then run 5 km as fast as you can hold. Goal this year: under 19:00. Cool down 10 min.$planB")
            }
            type == RunType.SPEED && deload ->
                PlannedRun(type, km, "Easy run + strides", "Deload week: easy $km km, then 6 × 20 s relaxed fast strides.$planB")
            type == RunType.SPEED ->
                PlannedRun(type, km, "Speed session", "Warm up 10-15 min easy, then ${speedWorkout(week)}. Cool down easy. Total about $km km.$planB")
            type == RunType.RECOVERY ->
                PlannedRun(type, km, "Recovery run", "Very easy $km km. Slower than you think.$pace$planB")
            type == RunType.LONG ->
                PlannedRun(type, km, "Long easy run", "Easy $km km at talking pace. This builds your half-marathon engine.$pace$planB")
            else ->
                PlannedRun(type, km, "Easy run", "Easy $km km at talking pace.$pace$planB")
        }
    }

    /** Planned strength session for [date], alternating A and B. */
    fun gymSession(state: AppState, date: LocalDate): GymSession? {
        val days = state.schedule.gymDays.sorted()
        val idx = days.indexOf(date.dayOfWeek.value)
        if (idx < 0) return null
        val deload = isDeload(weekIndex(state, date))
        val sets = if (deload) 2 else 3
        val a = idx % 2 == 0
        fun lift(name: String, reps: Int, note: String = "") =
            PlannedLift(name, sets, reps, suggestedKg(state, name, reps, deload), note)
        val lifts = if (a) listOf(
            lift(Exercises.SQUAT, 5),
            lift(Exercises.OHP, 5),
            lift(Exercises.PULLUP, 6, "kg = added weight, 0 = bodyweight"),
            lift(Exercises.SPLIT_SQUAT, 8, "per leg, kg per dumbbell"),
            lift(Exercises.PLANK, 45, "reps = seconds"),
        ) else listOf(
            lift(Exercises.DEADLIFT, 5),
            lift(Exercises.BENCH, 5),
            lift(Exercises.PULLUP, 6, "kg = added weight, 0 = bodyweight"),
            lift(Exercises.SL_RDL, 8, "per leg"),
            lift(Exercises.KNEE_RAISE, 10),
        )
        return GymSession(if (a) "Strength A" else "Strength B", lifts, deload)
    }

    fun lastEntry(state: AppState, exercise: String): LiftEntry? =
        state.gym.sortedBy { it.date }.lastOrNull { g -> g.entries.any { it.exercise == exercise } }
            ?.entries?.last { it.exercise == exercise }

    /** Simple linear progression: hit all reps last time, add 2.5 kg. Deload uses 85%. */
    fun suggestedKg(state: AppState, exercise: String, targetReps: Int, deload: Boolean): Double {
        if (exercise !in Exercises.loaded) return lastEntry(state, exercise)?.kg ?: 0.0
        val last = lastEntry(state, exercise)
        val base = when {
            last == null -> Exercises.startKg[exercise] ?: 20.0
            last.reps >= targetReps -> last.kg + 2.5
            else -> last.kg
        }
        val kg = if (deload) base * 0.85 else base
        return (kg / 2.5).roundToInt() * 2.5
    }
}

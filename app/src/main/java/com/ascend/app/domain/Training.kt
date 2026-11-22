package com.ascend.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** A weekly reward tier on the Training Points track. */
data class TpTier(val label: String, val share: Double, val gold: Int)

/**
 * Training Points (TP): every run, gym session and bike commute earns points, and the week has a
 * target built from the plan. The track stops at 100% on purpose: going past the plan earns nothing
 * extra, so the points never push you over the +10% rule.
 */
object Training {
    val TIERS = listOf(TpTier("Bronze", 0.5, 40), TpTier("Silver", 0.75, 80), TpTier("Gold", 1.0, 150))

    fun runTp(r: RunLog): Int =
        (TrainingPlan.runEquivalentKm(r) * 10).roundToInt() + if (r.type == RunType.SPEED || r.type == RunType.TEST) 20 else 0

    fun gymTp(g: GymLog): Int = 50 + 2 * g.entries.sumOf { it.sets }.coerceAtMost(25)

    fun bikeTp(b: BikeLog): Int = (b.km * 2).roundToInt()

    private fun inWeek(date: String, ws: LocalDate) = date.toDate().let { !it.isBefore(ws) && !it.isAfter(ws.plusDays(6)) }

    fun weekTp(state: AppState, ws: LocalDate): Int =
        state.runs.filter { inWeek(it.date, ws) }.sumOf { runTp(it) } +
            state.gym.filter { inWeek(it.date, ws) }.sumOf { gymTp(it) } +
            state.bikes.filter { inWeek(it.date, ws) }.sumOf { bikeTp(it) }

    /** What the plan is worth this week: planned km, one speed bonus, the strength sessions. */
    fun weekTarget(state: AppState, ws: LocalDate, today: LocalDate): Int {
        val week = TrainingPlan.weekIndex(state, ws)
        val km = TrainingPlan.targetKm(state, week, today)
        return (km * 10).roundToInt() + 20 + state.schedule.gymDays.size * 60
    }

    fun dayTp(state: AppState, d: LocalDate): Int {
        val iso = d.iso()
        return state.runs.filter { it.date == iso }.sumOf { runTp(it) } +
            state.gym.filter { it.date == iso }.sumOf { gymTp(it) } +
            state.bikes.filter { it.date == iso }.sumOf { bikeTp(it) }
    }

    private fun key(ws: LocalDate, i: Int) = "${ws.iso()}:$i"

    fun claimed(state: AppState, ws: LocalDate, i: Int) = key(ws, i) in state.tpClaims

    fun reached(state: AppState, ws: LocalDate, today: LocalDate, i: Int) =
        weekTp(state, ws) >= (weekTarget(state, ws, today) * TIERS[i].share).roundToInt()

    fun claim(state: AppState, today: LocalDate, i: Int, events: MutableList<GameEvent>): AppState {
        val ws = today.weekStart()
        if (i !in TIERS.indices || claimed(state, ws, i) || !reached(state, ws, today, i)) return state
        val t = TIERS[i]
        events += GameEvent.Message("[SYSTEM] ${t.label} training chest: +${t.gold} gold.")
        var s = state.copy(gold = state.gold + t.gold, tpClaims = (state.tpClaims + key(ws, i)).takeLast(60))
        if (i == TIERS.lastIndex) s = Rpg.addItem(s, Item.MYSTERY_BOX)
        return s
    }

    /** Full weeks in a row (before this one) where you hit 100% of the plan. */
    fun weekStreak(state: AppState, today: LocalDate): Int {
        var ws = today.weekStart().minusWeeks(1)
        val start = TrainingPlan.planStart(state)
        var n = 0
        while (!ws.isBefore(start) && weekTp(state, ws) >= weekTarget(state, ws, today)) { n++; ws = ws.minusWeeks(1) }
        return n
    }

    /** A difficulty letter for the day's session, just for flavour. */
    fun missionRank(run: PlannedRun?, gym: GymSession?): String = when {
        run?.type == RunType.TEST -> "S"
        run?.type == RunType.SPEED -> "B"
        run?.type == RunType.LONG -> "C"
        run != null -> "E"
        gym != null && gym.deload -> "D"
        gym != null -> "C"
        else -> "-"
    }
}

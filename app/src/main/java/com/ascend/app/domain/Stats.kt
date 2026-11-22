package com.ascend.app.domain

import java.time.LocalDate
import java.time.YearMonth

data class Point(val label: String, val value: Double)

data class Compare(val title: String, val now: String, val then: String, val better: Boolean?)

data class Recap(
    val month: YearMonth,
    val runs: Int,
    val km: Double,
    val gymSessions: Int,
    val readMin: Int,
    val studyMin: Int,
    val moneyMin: Int,
    val avgSleep: Double?,
    val weightChange: Double?,
    val xp: Int,
    val questsDone: Int,
    val questsMissed: Int,
    val wonBackMin: Int,
    val badges: List<Badge>,
)

/** Numbers that keep moving even when race times plateau. */
object Stats {
    private fun inRange(d: String, from: LocalDate, to: LocalDate) = d.toDate().let { !it.isBefore(from) && !it.isAfter(to) }

    fun consistency(state: AppState, today: LocalDate, days: Long = 28): Int? {
        val from = today.minusDays(days)
        val recs = state.questRecords.filter { inRange(it.date, from, today) && it.status != QuestStatus.REST }
        if (recs.isEmpty()) return null
        return (100.0 * recs.count { it.status == QuestStatus.DONE } / recs.size).toInt()
    }

    fun totalKm(state: AppState) = state.runs.sumOf { TrainingPlan.runEquivalentKm(it) }

    fun weeklyKm(state: AppState, today: LocalDate, weeks: Int = 12): List<Point> {
        val thisWeek = today.weekStart()
        return (weeks - 1 downTo 0).map { i ->
            val ws = thisWeek.minusWeeks(i.toLong())
            Point("${ws.monthValue}/${ws.dayOfMonth}", TrainingPlan.weekRunKm(state, ws))
        }
    }

    fun weeklyBikeKm(state: AppState, today: LocalDate, weeks: Int = 12): List<Point> {
        val thisWeek = today.weekStart()
        return (weeks - 1 downTo 0).map { i ->
            val ws = thisWeek.minusWeeks(i.toLong())
            Point("${ws.monthValue}/${ws.dayOfMonth}", TrainingPlan.weekBikeKm(state, ws))
        }
    }

    /** Pace in sec/km for foot runs of 2 km+, oldest first. */
    fun paces(state: AppState, limit: Int = 30): List<Point> =
        state.runs.filter { it.surface != Surface.INDOOR_BIKE && it.km >= 2 && it.seconds > 0 }
            .sortedBy { it.date }.takeLast(limit)
            .map { val d = it.date.toDate(); Point("${d.monthValue}/${d.dayOfMonth}", it.seconds / it.km) }

    fun weights(state: AppState): List<Point> =
        state.weighIns.sortedBy { it.date }.takeLast(30).map { val d = it.date.toDate(); Point("${d.monthValue}/${d.dayOfMonth}", it.kg) }

    /** Best estimated 1-rep max per session for an exercise. */
    fun lift(state: AppState, exercise: String): List<Point> =
        state.gym.sortedBy { it.date }.mapNotNull { g ->
            val best = g.entries.filter { it.exercise == exercise }.maxOfOrNull {
                if (exercise in Exercises.loaded) Records.e1rm(it.kg, it.reps) else it.reps.toDouble()
            } ?: return@mapNotNull null
            val d = g.date.toDate()
            Point("${d.monthValue}/${d.dayOfMonth}", best)
        }.takeLast(30)

    /** Last 4 weeks vs the 4 weeks that ended 3 months ago. */
    fun vsThreeMonthsAgo(state: AppState, today: LocalDate): List<Compare> {
        val nowFrom = today.minusDays(27)
        val thenTo = today.minusDays(90)
        val thenFrom = thenTo.minusDays(27)
        val out = mutableListOf<Compare>()
        fun km(f: LocalDate, t: LocalDate) = TrainingPlan.runKmBetween(state, f, t) / 4
        val kmThen = km(thenFrom, thenTo)
        val kmNow = km(nowFrom, today)
        if (state.runs.any { it.date.toDate() <= thenTo }) {
            out += Compare("Weekly km", "%.1f".format(kmNow), "%.1f".format(kmThen), kmNow > kmThen)
        }
        fun pace(f: LocalDate, t: LocalDate): Double? = state.runs
            .filter { inRange(it.date, f, t) && it.surface != Surface.INDOOR_BIKE && it.km >= 2 && it.seconds > 0 }
            .takeIf { it.isNotEmpty() }?.let { r -> r.sumOf { it.seconds }.toDouble() / r.sumOf { it.km } }
        val pThen = pace(thenFrom, thenTo)
        val pNow = pace(nowFrom, today)
        if (pThen != null && pNow != null) out += Compare("Average pace", Fmt.pace(pNow), Fmt.pace(pThen), pNow < pThen)
        val wThen = state.weighIns.filter { it.date.toDate() <= thenTo }.maxByOrNull { it.date }
        if (wThen != null) out += Compare("Body weight", Fmt.kg(state.latestWeight), Fmt.kg(wThen.kg), state.latestWeight > wThen.kg)
        val sqThen = state.gym.filter { it.date.toDate() <= thenTo }.flatMap { it.entries }
            .filter { it.exercise == Exercises.SQUAT }.maxOfOrNull { Records.e1rm(it.kg, it.reps) }
        if (sqThen != null) {
            val sqNow = Records.bestE1rm(state, Exercises.SQUAT)
            out += Compare("Squat (est. max)", Fmt.kg(sqNow.toInt().toDouble()), Fmt.kg(sqThen.toInt().toDouble()), sqNow > sqThen)
        }
        val cNow = consistency(state, today)
        val cThen = consistency(state, thenTo)
        if (cNow != null && cThen != null) out += Compare("Consistency", "$cNow%", "$cThen%", cNow > cThen)
        return out
    }

    fun recap(state: AppState, month: YearMonth): Recap {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        fun r(d: String) = inRange(d, from, to)
        val runs = state.runs.filter { r(it.date) }
        val sleeps = state.sleeps.filter { r(it.date) }
        val weights = state.weighIns.filter { r(it.date) }.sortedBy { it.date }
        val recs = state.questRecords.filter { r(it.date) }
        return Recap(
            month = month,
            runs = runs.size,
            km = runs.sumOf { TrainingPlan.runEquivalentKm(it) },
            gymSessions = state.gym.count { r(it.date) },
            readMin = state.minutes.filter { it.skill == Skill.READING && r(it.date) }.sumOf { it.minutes },
            studyMin = state.minutes.filter { it.skill == Skill.STUDYING && r(it.date) }.sumOf { it.minutes },
            moneyMin = state.minutes.filter { it.skill == Skill.MONEY && r(it.date) }.sumOf { it.minutes },
            avgSleep = sleeps.takeIf { it.isNotEmpty() }?.map { it.hours }?.average(),
            weightChange = if (weights.size >= 2) weights.last().kg - weights.first().kg else null,
            xp = state.dailyXp.filterKeys { r(it) }.values.sum(),
            questsDone = recs.count { it.status == QuestStatus.DONE },
            questsMissed = recs.count { it.status == QuestStatus.MISSED },
            wonBackMin = state.screenDays.filter { r(it.date) }.sumOf { (state.screen.baselineMin - it.usedMin).coerceAtLeast(0) },
            badges = state.badges.filterValues { r(it) }.keys.mapNotNull { Badges.byId(it) },
        )
    }
}

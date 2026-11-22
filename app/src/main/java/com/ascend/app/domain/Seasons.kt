package com.ascend.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** A season is a 6-week block of fresh mini-goals built from where you are now. */
object Seasons {
    const val LENGTH_DAYS = 42L

    fun current(state: AppState, today: LocalDate): Season? =
        state.seasons.lastOrNull { !today.isBefore(it.start.toDate()) && !today.isAfter(it.end.toDate()) }

    fun ensure(state: AppState, today: LocalDate): AppState {
        if (!state.onboarded) return state
        if (current(state, today) != null) return state
        val number = (state.seasons.maxOfOrNull { it.number } ?: 0) + 1
        val end = today.plusDays(LENGTH_DAYS - 1)
        return state.copy(seasons = state.seasons + Season(number, today.iso(), end.iso(), makeGoals(state, today)))
    }

    private fun makeGoals(state: AppState, today: LocalDate): List<SeasonGoal> {
        val sch = state.schedule
        val week = TrainingPlan.weekIndex(state, today)
        val weeklyKm = TrainingPlan.targetKm(state, week, today)
        val goals = mutableListOf(
            SeasonGoal("km", "Run ${(weeklyKm * 6 * 0.85).roundToInt()} km", GoalMetric.RUN_KM, (weeklyKm * 6 * 0.85).roundToInt().toDouble()),
            SeasonGoal("runs", "Do ${sch.runDays.size * 6 - 3} runs", GoalMetric.RUN_COUNT, (sch.runDays.size * 6 - 3).toDouble()),
            SeasonGoal("gym", "Do ${sch.gymDays.size * 6 - 2} strength sessions", GoalMetric.GYM_COUNT, (sch.gymDays.size * 6 - 2).toDouble()),
            SeasonGoal("read", "Read ${(sch.readMinutes * 42 * 0.8 / 60).roundToInt()} hours", GoalMetric.READ_MIN, ((sch.readMinutes * 42 * 0.8 / 60).roundToInt() * 60).toDouble()),
            SeasonGoal("study", "Study ${(sch.studyMinutes * sch.studyDays.size * 6 * 0.8 / 60).roundToInt()} hours", GoalMetric.STUDY_MIN,
                ((sch.studyMinutes * sch.studyDays.size * 6 * 0.8 / 60).roundToInt() * 60).toDouble()),
            SeasonGoal("sleep", "30 nights at ${sch.sleepHours} h+", GoalMetric.SLEEP_NIGHTS, 30.0),
        )
        if (state.latestWeight < state.profile.targetKg) {
            goals += SeasonGoal("weight", "Gain 1 kg (to ${Fmt.kg(state.latestWeight + 1)})", GoalMetric.WEIGHT_GAIN, 1.0, state.latestWeight)
        }
        val squat = Records.bestE1rm(state, Exercises.SQUAT)
        if (squat > 0) goals += SeasonGoal("squat", "Squat +7.5 kg (estimated max)", GoalMetric.SQUAT_GAIN, 7.5, squat)
        if (state.screen.enabled) goals += SeasonGoal("focus", "Win back 40 h from social apps", GoalMetric.FOCUS_HOURS, 40.0)
        return goals
    }

    fun value(state: AppState, season: Season, goal: SeasonGoal): Double {
        val from = season.start.toDate()
        val to = season.end.toDate()
        fun inSeason(d: String) = d.toDate().let { !it.isBefore(from) && !it.isAfter(to) }
        return when (goal.metric) {
            GoalMetric.RUN_KM -> state.runs.filter { inSeason(it.date) }.sumOf { TrainingPlan.runEquivalentKm(it) }
            GoalMetric.RUN_COUNT -> state.runs.count { inSeason(it.date) }.toDouble()
            GoalMetric.GYM_COUNT -> state.gym.count { inSeason(it.date) }.toDouble()
            GoalMetric.READ_MIN -> state.minutes.filter { it.skill == Skill.READING && inSeason(it.date) }.sumOf { it.minutes }.toDouble()
            GoalMetric.STUDY_MIN -> state.minutes.filter { it.skill == Skill.STUDYING && inSeason(it.date) }.sumOf { it.minutes }.toDouble()
            GoalMetric.SLEEP_NIGHTS -> state.sleeps.count { inSeason(it.date) && it.hours >= state.schedule.sleepHours }.toDouble()
            GoalMetric.WEIGHT_GAIN -> (state.weighIns.filter { inSeason(it.date) }.maxByOrNull { it.date }?.kg ?: goal.baseline) - goal.baseline
            GoalMetric.SQUAT_GAIN -> Records.bestE1rm(state, Exercises.SQUAT) - goal.baseline
            GoalMetric.FOCUS_HOURS -> state.screenDays.filter { inSeason(it.date) }
                .sumOf { (state.screen.baselineMin - it.usedMin).coerceAtLeast(0) } / 60.0
        }
    }

    fun claim(state: AppState, today: LocalDate, events: MutableList<GameEvent>): AppState {
        var s = state
        val season = current(s, today) ?: return s
        val newly = season.goals.filter { it.id !in season.claimed && value(s, season, it) >= it.target }
        if (newly.isEmpty()) return s
        val updated = season.copy(claimed = season.claimed + newly.map { it.id })
        s = s.copy(seasons = s.seasons.map { if (it.number == season.number) updated else it })
        for (g in newly) {
            events += GameEvent.Message("Season goal cleared: ${g.title}")
            s = Game.grantXp(s, Skill.FOCUS, Game.SEASON_GOAL_XP, today, events)
        }
        return s
    }
}

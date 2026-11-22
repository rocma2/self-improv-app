package com.ascend.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** One side of a skill's pentagon: a 0-100 score from your real logs, and what to do to raise it. */
data class SubStat(val name: String, val score: Int, val detail: String, val tip: String)

/** Builds the 5 sub-stats of each skill's pentagon and the octagon of all skills. */
object SkillRadar {

    /** Main radar: every skill's level, scaled so the best skill reaches the edge (min scale level 5). */
    fun overview(state: AppState): List<Triple<Skill, Int, Float>> {
        val levels = Skill.entries.map { it to Leveling.skillLevel(state.skill(it).xp) }
        val top = maxOf(5, levels.maxOf { it.second })
        return levels.map { (s, l) -> Triple(s, l, (l.toFloat() / top).coerceIn(0.05f, 1f)) }
    }

    private fun pct(v: Double) = (v * 100).roundToInt().coerceIn(0, 100)

    private fun inLast(date: String, today: LocalDate, days: Long) =
        date.toDate().let { !it.isAfter(today) && it.isAfter(today.minusDays(days)) }

    /** Share of this skill's quests done over the last 28 days (rest days don't count). */
    private fun planRate(state: AppState, skill: Skill, today: LocalDate): SubStat {
        val recs = state.questRecords.filter { it.skill == skill && inLast(it.date, today, 28) && it.status != QuestStatus.REST }
        val done = recs.count { it.status == QuestStatus.DONE }
        val score = if (recs.isEmpty()) 0 else pct(done.toDouble() / recs.size)
        return SubStat("Discipline", score, if (recs.isEmpty()) "No quests judged yet" else "$done / ${recs.size} quests done (28 days)",
            "Do the quest even on bad days, a short version still counts more than nothing.")
    }

    private fun health(state: AppState, skill: Skill): SubStat {
        val hp = state.skill(skill).hp
        return SubStat("Health", hp.coerceIn(0, 100), "$hp / 100 HP", "Every finished quest heals +5 HP. Misses cost HP.")
    }

    private fun minutesStats(state: AppState, skill: Skill, today: LocalDate, weeklyTarget: Int): List<SubStat> {
        val logs = state.minutes.filter { it.skill == skill }
        val week = logs.filter { inLast(it.date, today, 7) }.sumOf { it.minutes }
        val days = logs.filter { inLast(it.date, today, 14) }.map { it.date }.distinct().size
        val deep = logs.count { inLast(it.date, today, 28) && it.minutes >= 45 }
        return listOf(
            SubStat("Volume", pct(week.toDouble() / weeklyTarget.coerceAtLeast(1)), "$week / $weeklyTarget min this week",
                "Add one more session this week, or 10 extra minutes to the ones you do."),
            SubStat("Frequency", pct(days / 10.0), "$days of the last 14 days", "Small and often beats one big session. Even 10 minutes keeps the habit alive."),
            SubStat("Deep work", pct(deep / 8.0), "$deep sessions of 45+ min (28 days)", "Enter a 45 or 60 min Dungeon with your phone in another room."),
        )
    }

    fun subStats(state: AppState, skill: Skill, today: LocalDate): List<SubStat> {
        val sch = state.schedule
        val bw = state.latestWeight
        val specific: List<SubStat> = when (skill) {
            Skill.RUNNING -> {
                val foot = state.runs.filter { it.surface != Surface.INDOOR_BIKE }
                val longest = foot.maxOfOrNull { it.km } ?: 0.0
                val best5k = Records.best5k(state)?.first
                val speed = best5k?.let { pct((30 * 60.0 - it) / (11 * 60.0)) } ?: 0
                val weeks = (0..3).map { TrainingPlan.weekRunKm(state, today.weekStart().minusWeeks(it.toLong())) }
                val target = TrainingPlan.targetKm(state, TrainingPlan.weekIndex(state, today), today)
                listOf(
                    SubStat("Endurance", pct(longest / 21.1), "Longest run ${Fmt.km(longest)} / 21.1", "Your long run grows by about 1 km a week. Keep it slow and conversational."),
                    SubStat("Speed", speed, best5k?.let { "Best 5K ${Fmt.duration(it)} (goal 19:00)" } ?: "No 5K logged yet",
                        "Hit the speed session hard, keep the other runs easy. Test day is in weeks 9-12."),
                    SubStat("Volume", pct(weeks.average() / target.coerceAtLeast(1.0)), "%.1f km/week avg vs %.1f planned".format(weeks.average(), target),
                        "Run all 3 planned runs. Bike commutes help, but runs build the engine."),
                )
            }
            Skill.GYM -> {
                fun lift(ex: String, ratio: Double, label: String) = Records.bestE1rm(state, ex).let { e ->
                    SubStat(label, pct(e / (bw * ratio)), if (e > 0) "$ex ~${e.roundToInt()} kg (goal ${(bw * ratio).roundToInt()})" else "No $ex logged yet",
                        "Add 2.5 kg when you hit all reps with good form. Rest 2-3 min between heavy sets.")
                }
                listOf(lift(Exercises.SQUAT, 1.5, "Legs"), lift(Exercises.DEADLIFT, 2.0, "Pull power"), lift(Exercises.BENCH, 1.0, "Push"))
            }
            Skill.SLEEP -> {
                val week = state.sleeps.filter { inLast(it.date, today, 7) }
                val avg = week.map { it.hours }.average().takeIf { !it.isNaN() } ?: 0.0
                val logged = state.sleeps.count { inLast(it.date, today, 14) }
                val good = week.count { it.hours >= sch.sleepHours }
                listOf(
                    SubStat("Duration", pct(avg / sch.sleepHours), "%.1f h average (goal %.1f h)".format(avg, sch.sleepHours), "Phone out of the bedroom 30 min before lights out."),
                    SubStat("Tracking", pct(logged / 14.0), "$logged of the last 14 nights logged", "Log your sleep right when you wake up, it takes 5 seconds."),
                    SubStat("Recovery", pct(good / 7.0), "$good / 7 nights at goal", "Same bedtime every night, even on weekends, makes the hours easy."),
                )
            }
            Skill.WEIGHT -> {
                val gain = state.latestWeight - state.profile.startKg
                val goal = (state.profile.targetKg - state.profile.startKg).coerceAtLeast(0.5)
                val surplus = state.foodDays.count { inLast(it, today, 14) }
                val weighs = state.weighIns.count { inLast(it.date, today, 28) }
                listOf(
                    SubStat("Mass", pct(gain / goal), "%+.1f kg of %+.1f kg".format(gain, goal), "Aim for +0.25 to 0.5 kg a week. Faster is mostly fat."),
                    SubStat("Fuel", pct(surplus / 14.0), "$surplus of 14 days in a surplus", "Add a 500 kcal snack: milk, oats, peanut butter, bananas."),
                    SubStat("Tracking", pct(weighs / 4.0), "$weighs weigh-ins (4 weeks)", "Weigh once a week, same morning, after the bathroom."),
                )
            }
            Skill.READING -> minutesStats(state, skill, today, sch.readMinutes * 7)
            Skill.STUDYING -> minutesStats(state, skill, today, sch.studyMinutes * sch.studyDays.size)
            Skill.MONEY -> minutesStats(state, skill, today, sch.moneyMinutes * sch.moneyDays.size)
            Skill.FOCUS -> {
                val days = state.screenDays.filter { inLast(it.date, today, 7) }
                val under = days.count { it.usedMin <= it.budgetMin }
                val avg = days.map { it.usedMin }.average().takeIf { !it.isNaN() }
                val base = state.screen.baselineMin.coerceAtLeast(1)
                listOf(
                    SubStat("Control", pct(under / 7.0), "$under / 7 days under budget", "Move TikTok and YouTube off your home screen."),
                    SubStat("Time won", avg?.let { pct((base - it) / base) } ?: 0, avg?.let { "${Fmt.hours(it.roundToInt())} a day vs ${Fmt.hours(base)} before" } ?: "Turn on the budget in Focus",
                        "Replace the first scroll of the day with a Dungeon or a run."),
                    SubStat("Momentum", pct(state.momentum.streak / 30.0), "🔥 ${state.momentum.streak} good days", "Good days add up. A freeze saves you on a bad one."),
                )
            }
        }
        return specific + planRate(state, skill, today) + health(state, skill)
    }

    /** The sub-stat to work on next: the lowest one. */
    fun weakest(stats: List<SubStat>): SubStat = stats.minBy { it.score }
}

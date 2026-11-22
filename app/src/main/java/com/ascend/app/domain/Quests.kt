package com.ascend.app.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

enum class QuestKind { RUN, GYM, SLEEP, FOOD, WEIGH_IN, READ, STUDY, MONEY, SCREEN }

data class Quest(
    val id: String,
    val date: String,
    val skill: Skill,
    val kind: QuestKind,
    val title: String,
    val detail: String,
    val baseXp: Int,
    val target: Double,
    val optional: Boolean = false,
)

object QuestEngine {
    fun isManualRest(state: AppState, date: LocalDate) = date.iso() in state.restDays

    fun screenBudget(state: AppState, date: LocalDate): Int {
        val start = runCatching { state.screen.startDate.toDate() }.getOrNull() ?: return state.screen.baselineMin
        val weeks = ChronoUnit.WEEKS.between(start, date).coerceAtLeast(0)
        return (state.screen.baselineMin - weeks * state.screen.weeklyDropMin).toInt().coerceAtLeast(state.screen.floorMin)
    }

    fun screenActive(state: AppState, date: LocalDate): Boolean {
        if (!state.screen.enabled) return false
        val start = runCatching { state.screen.startDate.toDate() }.getOrNull() ?: return false
        return !date.isBefore(start)
    }

    fun questsFor(state: AppState, date: LocalDate, today: LocalDate = date): List<Quest> {
        val d = date.iso()
        val dow = date.dayOfWeek.value
        val sch = state.schedule
        val rest = isManualRest(state, date)
        val week = TrainingPlan.weekIndex(state, date)
        val deload = TrainingPlan.isDeload(week)
        val out = mutableListOf<Quest>()

        TrainingPlan.plannedRun(state, date, today)?.let { r ->
            out += Quest("$d:RUN", d, Skill.RUNNING, QuestKind.RUN, r.title, r.detail,
                40 + (r.km * 4).roundToInt(), r.km * 0.8, optional = rest || deload)
        }
        TrainingPlan.gymSession(state, date)?.let { g ->
            val winter = if (TrainingPlan.isWinter(state, date)) " No gym access? A home workout counts (Plan B)." else ""
            out += Quest("$d:GYM", d, Skill.GYM, QuestKind.GYM, g.name + if (g.deload) " (deload)" else "",
                g.lifts.joinToString(" · ") { "${it.exercise} ${it.sets}×${it.reps}" } + winter,
                50, 1.0, optional = rest || deload)
        }
        out += Quest("$d:SLEEP", d, Skill.SLEEP, QuestKind.SLEEP, "Sleep ${trim(sch.sleepHours)} h",
            "Log last night's sleep. Screens off 30 min before bed helps.", 20, sch.sleepHours, optional = rest)
        out += Quest("$d:FOOD", d, Skill.WEIGHT, QuestKind.FOOD, "Eat in a surplus",
            Nutrition.target(state, date).let { f -> "About ${f.kcal} kcal and ${f.proteinG} g protein: 3 real meals + 2 snacks. You're building to ${Fmt.kg(state.profile.targetKg)}." }, 15, 1.0, optional = rest)
        if (dow == sch.weighInDay) {
            out += Quest("$d:WEIGH", d, Skill.WEIGHT, QuestKind.WEIGH_IN, "Weekly weigh-in",
                "Morning, after the bathroom, before eating.", 25, 1.0, optional = rest)
        }
        out += Quest("$d:READ", d, Skill.READING, QuestKind.READ, "Read ${sch.readMinutes} min",
            "Any book. Paper or e-reader, not a phone feed.", 20, sch.readMinutes.toDouble(), optional = rest)
        if (dow in sch.studyDays) {
            out += Quest("$d:STUDY", d, Skill.STUDYING, QuestKind.STUDY, "Study ${sch.studyMinutes} min",
                "Phone in another room. Try 25 min on, 5 min off.", 30, sch.studyMinutes.toDouble(), optional = rest)
        }
        if (dow in sch.moneyDays) {
            out += Quest("$d:MONEY", d, Skill.MONEY, QuestKind.MONEY, "Money move: ${sch.moneyMinutes} min",
                "Work a shift, apply to a job, learn a paid skill, sell something, build a side project.", 30, sch.moneyMinutes.toDouble(), optional = rest)
        }
        if (screenActive(state, date)) {
            val b = screenBudget(state, date)
            out += Quest("$d:SCREEN", d, Skill.FOCUS, QuestKind.SCREEN, "TikTok + YouTube under ${Fmt.hours(b)}",
                "Checked automatically at the end of the day. Every hour under your old ${Fmt.hours(state.screen.baselineMin)} earns XP.",
                30, b.toDouble(), optional = rest)
        }
        return out
    }

    private fun trim(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    /** 0.0 to 1.0. Screen quests can only be judged at the end of the day, so they read from screenDays. */
    fun progress(state: AppState, q: Quest): Double {
        val d = q.date
        val p = when (q.kind) {
            QuestKind.RUN -> state.runs.filter { it.date == d }.sumOf { TrainingPlan.runEquivalentKm(it) } / q.target
            QuestKind.GYM -> if (state.gym.any { it.date == d }) 1.0 else 0.0
            QuestKind.SLEEP -> (state.sleeps.filter { it.date == d }.maxOfOrNull { it.hours } ?: 0.0) / q.target
            QuestKind.FOOD -> if (d in state.foodDays) 1.0 else 0.0
            QuestKind.WEIGH_IN -> if (state.weighIns.any { it.date == d }) 1.0 else 0.0
            QuestKind.READ -> minutesOn(state, d, Skill.READING) / q.target
            QuestKind.STUDY -> minutesOn(state, d, Skill.STUDYING) / q.target
            QuestKind.MONEY -> minutesOn(state, d, Skill.MONEY) / q.target
            QuestKind.SCREEN -> state.screenDays.firstOrNull { it.date == d }?.let { if (it.usedMin <= it.budgetMin) 1.0 else 0.0 } ?: 0.0
        }
        return p.coerceIn(0.0, 1.0)
    }

    private fun minutesOn(state: AppState, d: String, skill: Skill) =
        state.minutes.filter { it.date == d && it.skill == skill }.sumOf { it.minutes }.toDouble()

    fun record(state: AppState, q: Quest): QuestRecord? = state.questRecords.firstOrNull { it.questId == q.id }

    fun pendingPenalties(state: AppState, today: LocalDate): List<Penalty> =
        state.penalties.filter { !it.done && !it.due.toDate().isAfter(today) }

    /** Penalties are small, useful, and never take away food or sleep. */
    fun penaltyFor(skill: Skill): Triple<String, String, Int> = when (skill) {
        Skill.RUNNING -> Triple("Penalty: +1 km", "Add 1 extra easy km to any run, or walk 20 min.", 0)
        Skill.GYM -> Triple("Penalty: 60 push-ups", "Any number of sets. Or a 4 min plank in total.", 0)
        Skill.SLEEP -> Triple("Penalty: phone out of the bedroom", "Tonight the phone charges outside your room. Lights out on time.", 0)
        Skill.WEIGHT -> Triple("Penalty: extra 500 kcal", "Add a big snack: milk + oats + peanut butter or similar.", 0)
        Skill.READING -> Triple("Penalty: read 20 min", "Extra reading on top of today's quest.", 20)
        Skill.STUDYING -> Triple("Penalty: study 25 min", "One focused block, phone in another room.", 25)
        Skill.MONEY -> Triple("Penalty: 30 min money task", "Applications, a shift, a skill course, a side project.", 30)
        Skill.FOCUS -> Triple("Penalty: 30 min outside, no phone", "Walk, bike or sit outside without your phone.", 0)
    }
}

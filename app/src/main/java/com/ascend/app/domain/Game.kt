package com.ascend.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * All game rules live here as pure functions: state in, new state + events out.
 * The Android layer only stores the state and shows the events.
 */
object Game {
    const val WEEKLY_CHEST_XP = 250
    const val RECORD_XP = 50
    const val BADGE_XP = 100
    const val SEASON_GOAL_XP = 150
    const val PENALTY_DONE_XP = 15
    const val GOOD_DAY_RATIO = 0.7
    const val MAX_CATCH_UP_DAYS = 7L

    class Result(val state: AppState, val events: List<GameEvent>)

    fun xpMultiplier(state: AppState, date: LocalDate): Double =
        state.mode.xpMultiplier * if (TrainingPlan.isWinter(state, date)) 1.2 else 1.0

    // ---------- XP and HP ----------

    fun grantXp(state: AppState, skill: Skill, baseXp: Int, date: LocalDate, events: MutableList<GameEvent>, multiply: Boolean = true): AppState {
        val xp = if (multiply) (baseXp * xpMultiplier(state, date)).roundToInt() else baseXp
        if (xp <= 0) return state
        val sk = state.skill(skill)
        val oldSkillLevel = Leveling.skillLevel(sk.xp)
        val newSk = sk.copy(xp = sk.xp + xp, hp = (sk.hp + 5).coerceAtMost(100))
        val newSkillLevel = Leveling.skillLevel(newSk.xp)
        if (newSkillLevel > oldSkillLevel) events += GameEvent.SkillLevelUp(skill, newSkillLevel)
        val oldLevel = Leveling.level(state.totalXp)
        val total = state.totalXp + xp
        val newLevel = Leveling.level(total)
        if (newLevel > oldLevel) events += GameEvent.LevelUp(newLevel, Leveling.title(newLevel))
        val key = date.iso()
        return state.copy(
            skills = state.skills + (skill to newSk),
            totalXp = total,
            dailyXp = state.dailyXp + (key to ((state.dailyXp[key] ?: 0) + xp)),
        )
    }

    /** HP loss. At 0 HP the skill drops one level and HP refills to 60. */
    fun damage(state: AppState, skill: Skill, amount: Int, events: MutableList<GameEvent>): AppState {
        if (amount <= 0) return state
        val sk = state.skill(skill)
        var hp = sk.hp - amount
        var xp = sk.xp
        if (hp <= 0) {
            val lvl = Leveling.skillLevel(xp)
            if (lvl > 1) {
                xp = Leveling.skillXpForLevel(lvl - 1)
                events += GameEvent.SkillLevelDown(skill, lvl - 1)
            }
            hp = 60
        }
        return state.copy(skills = state.skills + (skill to SkillState(xp, hp)))
    }

    // ---------- Daily flow ----------

    /** Mark today's finished quests as done and give their XP right away. */
    fun syncToday(state: AppState, today: LocalDate, events: MutableList<GameEvent>): AppState {
        var s = state
        for (q in QuestEngine.questsFor(s, today, today)) {
            if (q.kind == QuestKind.SCREEN) continue
            if (QuestEngine.record(s, q) != null) continue
            if (QuestEngine.progress(s, q) >= 1.0) {
                val xp = (q.baseXp * xpMultiplier(s, today)).roundToInt()
                s = s.copy(questRecords = s.questRecords + QuestRecord(q.id, q.date, q.skill, QuestStatus.DONE, xp))
                s = grantXp(s, q.skill, q.baseXp, today, events)
                events += GameEvent.QuestDone(q.title, xp)
            }
        }
        return s
    }

    /**
     * Judge every past day that wasn't judged yet (at most a week back).
     * [usageFor] returns minutes on blocked apps for a day, or null when unknown.
     */
    fun evaluate(state: AppState, today: LocalDate, usageFor: (LocalDate) -> Int?): Result {
        val events = mutableListOf<GameEvent>()
        var s = state
        if (!s.onboarded) return Result(s, events)
        val last = s.lastEvaluated?.let { runCatching { it.toDate() }.getOrNull() } ?: today.minusDays(1)
        var d = maxOf(last.plusDays(1), today.minusDays(MAX_CATCH_UP_DAYS))
        while (d.isBefore(today)) {
            s = evaluateDay(s, d, today, usageFor, events)
            d = d.plusDays(1)
        }
        if (last.isBefore(today.minusDays(1))) s = s.copy(lastEvaluated = today.minusDays(1).iso())
        s = finish(state, s, today, events)
        return Result(s, events)
    }

    private fun evaluateDay(state: AppState, d: LocalDate, today: LocalDate, usageFor: (LocalDate) -> Int?, events: MutableList<GameEvent>): AppState {
        var s = state
        val quests = QuestEngine.questsFor(s, d, today)
        var screenKnown = true

        if (quests.any { it.kind == QuestKind.SCREEN } && s.screenDays.none { it.date == d.iso() }) {
            val used = usageFor(d)
            if (used == null) screenKnown = false else {
                val budget = QuestEngine.screenBudget(s, d)
                val wonBackMin = (s.screen.baselineMin - used).coerceAtLeast(0)
                val xp = (wonBackMin / 60.0 * 20).roundToInt()
                s = s.copy(screenDays = s.screenDays + ScreenDay(d.iso(), used, budget, xp))
                if (xp > 0) {
                    s = grantXp(s, Skill.FOCUS, xp, d, events)
                    events += GameEvent.Message("You won back ${Fmt.hours(wonBackMin)} from TikTok/YouTube on ${d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}: +$xp XP")
                }
            }
        }

        var done = 0
        val missed = mutableListOf<Quest>()
        for (q in quests) {
            val rec = QuestEngine.record(s, q)
            if (rec != null) { if (rec.status == QuestStatus.DONE) done++; continue }
            if (q.kind == QuestKind.SCREEN && !screenKnown) continue
            when {
                QuestEngine.progress(s, q) >= 1.0 -> {
                    val xp = (q.baseXp * xpMultiplier(s, d)).roundToInt()
                    s = s.copy(questRecords = s.questRecords + QuestRecord(q.id, q.date, q.skill, QuestStatus.DONE, xp))
                    s = grantXp(s, q.skill, q.baseXp, d, events)
                    done++
                }
                q.optional -> s = s.copy(questRecords = s.questRecords + QuestRecord(q.id, q.date, q.skill, QuestStatus.REST))
                else -> {
                    s = s.copy(questRecords = s.questRecords + QuestRecord(q.id, q.date, q.skill, QuestStatus.MISSED))
                    s = damage(s, q.skill, s.mode.hpLoss, events)
                    missed += q
                }
            }
        }

        // One penalty quest per bad day, for the biggest quest you missed.
        if (missed.isNotEmpty() && s.mode.penaltyQuest) {
            val worst = missed.maxBy { it.baseXp }
            val (title, detail, mins) = QuestEngine.penaltyFor(worst.skill)
            s = s.copy(
                penalties = s.penalties + Penalty(s.nextId, d.iso(), d.plusDays(1).iso(), worst.skill, title, detail, mins),
                nextId = s.nextId + 1,
            )
        }
        if (missed.isNotEmpty()) {
            events += GameEvent.Message("${d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}: ${missed.size} quest(s) missed" +
                if (s.mode.hpLoss > 0) ", -${s.mode.hpLoss} HP each." else ".")
        }

        // Momentum never resets to zero: a freeze saves you, otherwise you keep 75%.
        val judged = done + missed.size
        if (judged > 0) {
            val m = s.momentum
            s = s.copy(momentum = if (done.toDouble() / judged >= GOOD_DAY_RATIO) {
                val streak = m.streak + 1
                val toward = m.towardFreeze + 1
                val earned = toward >= 7
                m.copy(streak = streak, best = maxOf(m.best, streak),
                    towardFreeze = if (earned) 0 else toward,
                    freezes = if (earned) (m.freezes + 1).coerceAtMost(3) else m.freezes)
            } else if (m.freezes > 0) {
                events += GameEvent.Message("A momentum freeze saved your streak.")
                m.copy(freezes = m.freezes - 1)
            } else {
                m.copy(streak = (m.streak * 0.75).toInt(), towardFreeze = 0)
            })
        }

        // Weekly chest on Sunday.
        if (d.dayOfWeek == DayOfWeek.SUNDAY) {
            val ws = d.weekStart()
            if (s.weeklyResults.none { it.weekStart == ws.iso() }) {
                val misses = s.questRecords.count { r ->
                    r.status == QuestStatus.MISSED && !r.date.toDate().isBefore(ws) && !r.date.toDate().isAfter(d)
                }
                val rewarded = misses < s.mode.weeklyMissLimit
                val xp = if (rewarded) (WEEKLY_CHEST_XP * xpMultiplier(s, d)).roundToInt() else 0
                s = s.copy(weeklyResults = s.weeklyResults + WeeklyResult(ws.iso(), misses, rewarded, xp))
                if (rewarded) {
                    s = grantXp(s, Skill.FOCUS, WEEKLY_CHEST_XP, d, events)
                    events += GameEvent.Message("Weekly chest opened: +$xp XP ($misses misses).")
                } else {
                    events += GameEvent.Message("Weekly chest lost: $misses misses (limit ${s.mode.weeklyMissLimit}).")
                }
            }
        }
        return s.copy(lastEvaluated = d.iso())
    }

    /** Runs after any change: new records, badges, season goals, today's quests. */
    fun finish(before: AppState, after: AppState, today: LocalDate, events: MutableList<GameEvent>): AppState {
        var s = after
        for (r in Records.newRecords(before, s)) {
            events += GameEvent.NewRecord("${r.title}: ${r.value}")
            val skill = if (r.key.startsWith("lift:")) Skill.GYM else if (r.key == "weight") Skill.WEIGHT else Skill.RUNNING
            s = grantXp(s, skill, RECORD_XP, today, events)
        }
        s = syncToday(s, today, events)
        s = Seasons.ensure(s, today)
        s = Seasons.claim(s, today, events)
        for (b in Badges.all) {
            if (b.id !in s.badges && b.check(s)) {
                s = s.copy(badges = s.badges + (b.id to today.iso()))
                events += GameEvent.BadgeEarned(b)
                s = grantXp(s, Skill.FOCUS, BADGE_XP, today, events, multiply = false)
            }
        }
        return s
    }

    private fun apply(state: AppState, today: LocalDate, change: (AppState) -> AppState): Result {
        val events = mutableListOf<GameEvent>()
        val s = finish(state, change(state), today, events)
        return Result(s, events)
    }

    // ---------- Actions ----------

    fun completeOnboarding(state: AppState, profile: Profile, today: LocalDate): AppState =
        state.copy(
            onboarded = true,
            profile = profile.copy(planStart = today.iso()),
            lastEvaluated = today.minusDays(1).iso(),
            weighIns = if (state.weighIns.isEmpty()) listOf(WeighIn(today.iso(), profile.startKg)) else state.weighIns,
        ).let { Seasons.ensure(it, today) }

    fun logRun(state: AppState, today: LocalDate, date: LocalDate, km: Double, seconds: Int, type: RunType, surface: Surface, tempC: Int?) =
        apply(state, today) { it.copy(runs = it.runs + RunLog(it.nextId, date.iso(), km, seconds, type, surface, tempC), nextId = it.nextId + 1) }

    fun logGym(state: AppState, today: LocalDate, date: LocalDate, entries: List<LiftEntry>, home: Boolean) =
        apply(state, today) { it.copy(gym = it.gym + GymLog(it.nextId, date.iso(), entries, home), nextId = it.nextId + 1) }

    fun logWeight(state: AppState, today: LocalDate, date: LocalDate, kg: Double) =
        apply(state, today) { s -> s.copy(weighIns = s.weighIns.filter { it.date != date.iso() } + WeighIn(date.iso(), kg)) }

    fun logSleep(state: AppState, today: LocalDate, date: LocalDate, hours: Double) =
        apply(state, today) { s -> s.copy(sleeps = s.sleeps.filter { it.date != date.iso() } + SleepLog(date.iso(), hours)) }

    fun logMinutes(state: AppState, today: LocalDate, date: LocalDate, skill: Skill, minutes: Int) =
        apply(state, today) { it.copy(minutes = it.minutes + MinutesLog(it.nextId, date.iso(), skill, minutes), nextId = it.nextId + 1) }

    fun logBike(state: AppState, today: LocalDate, date: LocalDate, km: Double) =
        apply(state, today) { it.copy(bikes = it.bikes + BikeLog(it.nextId, date.iso(), km), nextId = it.nextId + 1) }

    fun logFood(state: AppState, today: LocalDate, date: LocalDate) =
        apply(state, today) { s -> if (date.iso() in s.foodDays) s else s.copy(foodDays = s.foodDays + date.iso()) }

    fun completePenalty(state: AppState, today: LocalDate, id: Long): Result {
        val events = mutableListOf<GameEvent>()
        val p = state.penalties.firstOrNull { it.id == id && !it.done } ?: return Result(state, events)
        var s = state.copy(penalties = state.penalties.map { if (it.id == id) it.copy(done = true, doneDate = today.iso()) else it })
        if (p.minutes > 0) s = s.copy(minutes = s.minutes + MinutesLog(s.nextId, today.iso(), p.skill, p.minutes), nextId = s.nextId + 1)
        val sk = s.skill(p.skill)
        s = s.copy(skills = s.skills + (p.skill to sk.copy(hp = (sk.hp + 10).coerceAtMost(100))))
        s = grantXp(s, p.skill, PENALTY_DONE_XP, today, events)
        events += GameEvent.Message("Penalty cleared. +10 HP to ${p.skill.label}.")
        return Result(finish(state, s, today, events), events)
    }

    /** One recovery day per week: all of today's quests become optional and nothing counts as a miss. */
    fun canTakeRestDay(state: AppState, today: LocalDate): Boolean {
        val ws = today.weekStart()
        return today.iso() !in state.restDays && state.restDays.none { val d = it.toDate(); !d.isBefore(ws) && !d.isAfter(ws.plusDays(6)) }
    }

    fun takeRestDay(state: AppState, today: LocalDate): AppState =
        if (canTakeRestDay(state, today)) state.copy(restDays = state.restDays + today.iso()) else state

    fun deleteRun(state: AppState, id: Long) = state.copy(runs = state.runs.filter { it.id != id })
    fun deleteGym(state: AppState, id: Long) = state.copy(gym = state.gym.filter { it.id != id })
    fun deleteMinutes(state: AppState, id: Long) = state.copy(minutes = state.minutes.filter { it.id != id })
    fun deleteBike(state: AppState, id: Long) = state.copy(bikes = state.bikes.filter { it.id != id })
    fun deleteWeighIn(state: AppState, date: String) = state.copy(weighIns = state.weighIns.filter { it.date != date })

    fun enableScreen(state: AppState, today: LocalDate, baselineMin: Int): AppState =
        state.copy(screen = state.screen.copy(enabled = true, baselineMin = baselineMin.coerceIn(60, 1080), startDate = today.iso()))

    /** Lock TikTok/YouTube when a penalty is due (modes with app lock). */
    fun penaltyLockActive(state: AppState, today: LocalDate): Boolean =
        state.mode.appLock && QuestEngine.pendingPenalties(state, today).isNotEmpty()

    /** Lock when today's budget is used up (modes with app lock). */
    fun budgetLockEnabled(state: AppState): Boolean = state.mode.appLock && state.screen.enabled
}

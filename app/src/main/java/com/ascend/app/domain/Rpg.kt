package com.ascend.app.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random

@Serializable
enum class Item(val label: String, val emoji: String, val description: String, val price: Int) {
    FREEZE_CRYSTAL("Freeze crystal", "❄️", "+1 momentum freeze (max 3).", 150),
    ELIXIR("Elixir", "🧪", "+40 HP to your weakest skill.", 100),
    REST_SCROLL("Rest scroll", "📜", "An extra recovery day today, even if you used this week's.", 250),
    MYSTERY_BOX("Mystery box", "🎁", "Open it for gold or a random item.", 0),
}

@Serializable
enum class JobClass(val label: String, val description: String, val skills: List<Skill>) {
    STRIKER("Striker", "Speed and endurance. +15% XP on running and sleep.", listOf(Skill.RUNNING, Skill.SLEEP)),
    VANGUARD("Vanguard", "Raw strength. +15% XP on gym and weight gain.", listOf(Skill.GYM, Skill.WEIGHT)),
    SAGE("Sage", "Mind over everything. +15% XP on reading and studying.", listOf(Skill.READING, Skill.STUDYING)),
    WARDEN("Warden", "Iron discipline. +15% XP on focus and money.", listOf(Skill.FOCUS, Skill.MONEY)),
}

@Serializable
enum class GateMetric(val unit: String) { RUN_KM("km"), RUNS("runs"), GYM("sessions"), READ_MIN("min"), STUDY_MIN("min"), MONEY_MIN("min"), SLEEP_NIGHTS("nights"), FOOD_DAYS("days"), SCREEN_DAYS("days") }

@Serializable
enum class GateStatus { OPEN, ENTERED, CLEARED, FAILED }

@Serializable
data class Gate(
    val id: String,
    val rank: String,
    val red: Boolean,
    val title: String,
    val detail: String,
    val skill: Skill,
    val metric: GateMetric,
    val target: Double,
    val start: String,
    val end: String,
    val xp: Int,
    val gold: Int,
    val status: GateStatus = GateStatus.OPEN,
)

@Serializable
data class CustomReward(val id: Long, val name: String, val cost: Int)

data class Boss(
    val id: String,
    val name: String,
    val goal: String,
    val maxHp: Double,
    val hp: Double,
    val hpLabel: String,
    val locked: Boolean = false,
) {
    val fraction: Float get() = if (maxHp <= 0) 0f else (hp / maxHp).toFloat().coerceIn(0f, 1f)
    val defeated: Boolean get() = !locked && hp <= 0
}

/** The RPG layer: stat points, gates, bosses, job change, gold and items. */
object Rpg {
    const val POINTS_PER_LEVEL = 3
    const val BOSS_XP = 500
    const val JOB_LEVEL = 10
    const val JOB_TRIAL_DAYS = 7
    val ATTRS = listOf("STR", "AGI", "VIT", "INT", "WIL")

    fun attributes(state: AppState): List<Pair<String, Int>> =
        Leveling.attributes(state).map { (n, v) -> n to v + (state.allocated[n] ?: 0) }

    fun allocate(state: AppState, attr: String): AppState {
        if (state.statPoints <= 0 || attr !in ATTRS) return state
        return state.copy(statPoints = state.statPoints - 1, allocated = state.allocated + (attr to (state.allocated[attr] ?: 0) + 1))
    }

    fun jobMultiplier(state: AppState, skill: Skill): Double = if (state.job?.skills?.contains(skill) == true) 1.15 else 1.0

    // ---------- Items and gold ----------

    fun addItem(state: AppState, item: Item, n: Int = 1): AppState =
        state.copy(inventory = state.inventory + (item to (state.inventory[item] ?: 0) + n))

    fun buy(state: AppState, item: Item): AppState {
        if (item.price <= 0 || state.gold < item.price) return state
        return addItem(state.copy(gold = state.gold - item.price), item)
    }

    fun buyReward(state: AppState, id: Long): AppState {
        val r = state.customRewards.firstOrNull { it.id == id } ?: return state
        if (state.gold < r.cost) return state
        return state.copy(gold = state.gold - r.cost)
    }

    fun addReward(state: AppState, name: String, cost: Int): AppState =
        state.copy(customRewards = state.customRewards + CustomReward(state.nextId, name.trim(), cost.coerceIn(10, 100_000)), nextId = state.nextId + 1)

    fun removeReward(state: AppState, id: Long) = state.copy(customRewards = state.customRewards.filter { it.id != id })

    fun useItem(state: AppState, item: Item, today: LocalDate, events: MutableList<GameEvent>, random: Random = Random.Default): AppState {
        val have = state.inventory[item] ?: 0
        if (have <= 0) return state
        var s = state.copy(inventory = state.inventory + (item to have - 1))
        when (item) {
            Item.FREEZE_CRYSTAL -> {
                s = s.copy(momentum = s.momentum.copy(freezes = (s.momentum.freezes + 1).coerceAtMost(3)))
                events += GameEvent.Message("[SYSTEM] Momentum freeze added.")
            }
            Item.ELIXIR -> {
                val weakest = s.skills.minByOrNull { it.value.hp }?.key ?: Skill.RUNNING
                val sk = s.skill(weakest)
                s = s.copy(skills = s.skills + (weakest to sk.copy(hp = (sk.hp + 40).coerceAtMost(100))))
                events += GameEvent.Message("[SYSTEM] ${weakest.label} restored +40 HP.")
            }
            Item.REST_SCROLL -> {
                if (today.iso() in s.restDays) return state
                s = s.copy(restDays = s.restDays + today.iso())
                events += GameEvent.Message("[SYSTEM] Recovery day granted. Nothing today counts as a miss.")
            }
            Item.MYSTERY_BOX -> {
                val roll = random.nextInt(100)
                s = when {
                    roll < 40 -> { val g = 50 + random.nextInt(101); events += GameEvent.Message("[SYSTEM] Box opened: $g gold."); s.copy(gold = s.gold + g) }
                    roll < 65 -> { events += GameEvent.Message("[SYSTEM] Box opened: Elixir 🧪"); addItem(s, Item.ELIXIR) }
                    roll < 85 -> { events += GameEvent.Message("[SYSTEM] Box opened: Freeze crystal ❄️"); addItem(s, Item.FREEZE_CRYSTAL) }
                    else -> { events += GameEvent.Message("[SYSTEM] Box opened: Rest scroll 📜"); addItem(s, Item.REST_SCROLL) }
                }
            }
        }
        return s
    }

    // ---------- Daily quest reward ----------

    /** Finish every required quest today: 1 stat point and a mystery box, once a day. */
    fun dailyReward(state: AppState, today: LocalDate, events: MutableList<GameEvent>): AppState {
        if (today.iso() in state.dailyRewardDays) return state
        val required = QuestEngine.questsFor(state, today).filter { !it.optional && it.kind != QuestKind.SCREEN }
        if (required.size < 3) return state
        if (required.any { QuestEngine.record(state, it)?.status != QuestStatus.DONE }) return state
        events += GameEvent.Message("[SYSTEM] Daily quest complete. Reward: +1 stat point, 1 mystery box.")
        return addItem(state.copy(statPoints = state.statPoints + 1, dailyRewardDays = state.dailyRewardDays + today.iso()), Item.MYSTERY_BOX)
    }

    // ---------- Gates ----------

    private fun rankIndex(rank: String) = listOf("E", "D", "C", "B", "A", "S").indexOf(rank).coerceAtLeast(0)

    /** Three gates every week, built from your plan. One is a red gate: harder, double reward. */
    fun ensureGates(state: AppState, today: LocalDate): AppState {
        if (!state.onboarded) return state
        val ws = today.weekStart()
        if (state.gates.any { it.start == ws.iso() }) return state
        val rank = Leveling.rank(Leveling.level(state.totalXp))
        val scale = 1.0 + 0.1 * rankIndex(rank)
        val end = ws.plusDays(6).iso()
        val week = TrainingPlan.weekIndex(state, today)
        val sch = state.schedule
        val km = TrainingPlan.targetKm(state, week, today)
        fun r(v: Double) = (v / 5).roundToInt() * 5.0
        val pool = mutableListOf(
            // Running and gym gates follow the plan exactly: never more than the +10% rule allows.
            Gate("", rank, false, "Gate of the Long Road", "Run this week's full plan.", Skill.RUNNING, GateMetric.RUN_KM, km, ws.iso(), end, 150, 60),
            Gate("", rank, false, "Iron Gate", "Do every strength session this week.", Skill.GYM, GateMetric.GYM, sch.gymDays.size.toDouble(), ws.iso(), end, 150, 60),
            Gate("", rank, false, "Library Gate", "Read ${r(sch.readMinutes * 7 * scale)} min this week.", Skill.READING, GateMetric.READ_MIN, r(sch.readMinutes * 7 * scale), ws.iso(), end, 120, 50),
            Gate("", rank, false, "Scholar's Gate", "Study ${r(sch.studyMinutes * sch.studyDays.size * scale)} min this week.", Skill.STUDYING, GateMetric.STUDY_MIN, r(sch.studyMinutes * sch.studyDays.size * scale), ws.iso(), end, 140, 60),
            Gate("", rank, false, "Gate of Rest", "6 nights at ${sch.sleepHours} h or more.", Skill.SLEEP, GateMetric.SLEEP_NIGHTS, 6.0, ws.iso(), end, 120, 50),
            Gate("", rank, false, "Feast Gate", "Eat in a surplus 7 days out of 7.", Skill.WEIGHT, GateMetric.FOOD_DAYS, 7.0, ws.iso(), end, 120, 50),
            Gate("", rank, false, "Merchant Gate", "${r(sch.moneyMinutes * sch.moneyDays.size * scale)} min on money moves.", Skill.MONEY, GateMetric.MONEY_MIN, r(sch.moneyMinutes * sch.moneyDays.size * scale), ws.iso(), end, 130, 80),
        )
        if (state.screen.enabled) pool += Gate("", rank, false, "Gate of Silence", "5 days under your TikTok/YouTube budget.", Skill.FOCUS, GateMetric.SCREEN_DAYS, 5.0, ws.iso(), end, 160, 70)
        val n = pool.size
        val picks = listOf(week % n, (week + 2) % n, (week + 4) % n).distinct()
        val gates = picks.mapIndexed { i, idx ->
            val g = pool[idx]
            // The red gate is always a non-training one, so it never pushes running or lifting volume.
            val red = i == picks.lastIndex && g.metric !in setOf(GateMetric.RUN_KM, GateMetric.GYM, GateMetric.RUNS)
            val target = if (red && g.metric in setOf(GateMetric.READ_MIN, GateMetric.STUDY_MIN, GateMetric.MONEY_MIN)) r(g.target * 1.4) else g.target
            val detail = if (red) g.detail.replace(Regex("\\d+(\\.\\d+)? min"), "${target.roundToInt()} min") + " Red gate: double reward" +
                (if (state.mode.hpLoss > 0) ", but failing it once entered costs ${state.mode.hpLoss} HP." else ".") else g.detail
            g.copy(id = "${ws.iso()}:$i", red = red, target = target, detail = detail,
                xp = ((if (red) 2 else 1) * g.xp * scale).roundToInt(), gold = (if (red) 2 else 1) * g.gold)
        }
        return state.copy(gates = state.gates.filter { !it.end.toDate().isBefore(today.minusWeeks(4)) } + gates)
    }

    fun gateValue(state: AppState, g: Gate): Double {
        val from = g.start.toDate()
        val to = g.end.toDate()
        fun inGate(d: String) = d.toDate().let { !it.isBefore(from) && !it.isAfter(to) }
        fun mins(skill: Skill) = state.minutes.filter { it.skill == skill && inGate(it.date) }.sumOf { it.minutes }.toDouble()
        return when (g.metric) {
            GateMetric.RUN_KM -> state.runs.filter { inGate(it.date) }.sumOf { TrainingPlan.runEquivalentKm(it) }
            GateMetric.RUNS -> state.runs.count { inGate(it.date) }.toDouble()
            GateMetric.GYM -> state.gym.count { inGate(it.date) }.toDouble()
            GateMetric.READ_MIN -> mins(Skill.READING)
            GateMetric.STUDY_MIN -> mins(Skill.STUDYING)
            GateMetric.MONEY_MIN -> mins(Skill.MONEY)
            GateMetric.SLEEP_NIGHTS -> state.sleeps.count { inGate(it.date) && it.hours >= state.schedule.sleepHours }.toDouble()
            GateMetric.FOOD_DAYS -> state.foodDays.count { inGate(it) }.toDouble()
            GateMetric.SCREEN_DAYS -> state.screenDays.count { inGate(it.date) && it.usedMin <= it.budgetMin }.toDouble()
        }
    }

    fun enterGate(state: AppState, id: String): AppState =
        state.copy(gates = state.gates.map { if (it.id == id && it.status == GateStatus.OPEN) it.copy(status = GateStatus.ENTERED) else it })

    /** Clear gates that hit their target; fail entered gates whose week is over. */
    fun settleGates(state: AppState, today: LocalDate, events: MutableList<GameEvent>): AppState {
        var s = state
        for (g in s.gates.filter { it.status == GateStatus.ENTERED }) {
            if (gateValue(s, g) >= g.target) {
                s = s.copy(gates = s.gates.map { if (it.id == g.id) it.copy(status = GateStatus.CLEARED) else it }, gold = s.gold + g.gold)
                s = Game.grantXp(s, g.skill, g.xp, today, events, multiply = false)
                s = addItem(s, Item.MYSTERY_BOX)
                events += GameEvent.Message("[SYSTEM] ${g.title} cleared! +${g.xp} XP, +${g.gold} gold, 1 mystery box.")
            } else if (today.isAfter(g.end.toDate())) {
                s = s.copy(gates = s.gates.map { if (it.id == g.id) it.copy(status = GateStatus.FAILED) else it })
                if (g.red) s = Game.damage(s, g.skill, s.mode.hpLoss, events)
                events += GameEvent.Message("[SYSTEM] ${g.title} collapsed before you cleared it.")
            }
        }
        return s
    }

    // ---------- Bosses: your big goals ----------

    fun bosses(state: AppState): List<Boss> {
        val out = mutableListOf<Boss>()
        val goal5k = 19 * 60.0
        val first5k = state.runs.filter { it.surface != Surface.INDOOR_BIKE && it.km >= 4.95 && it.seconds > 0 }
            .minByOrNull { it.date }?.let { it.seconds * 5.0 / it.km } ?: 26 * 60.0
        val best5k = Records.best5k(state)?.first?.toDouble() ?: first5k
        out += Boss("wall19", "The 19-Minute Wall", "5 km under 19:00", (first5k - goal5k).coerceAtLeast(60.0), (best5k - goal5k).coerceAtLeast(0.0),
            if (best5k <= goal5k) "DEFEATED" else "${Fmt.duration((best5k - goal5k).roundToInt())} left to cut")
        val longest = state.runs.filter { it.surface != Surface.INDOOR_BIKE }.maxOfOrNull { it.km } ?: 0.0
        out += Boss("half", "Warden of the Half", "Run 21.1 km", 21.1 - state.profile.baseWeeklyKm.coerceAtMost(10.0) / 2, (21.1 - longest).coerceAtLeast(0.0),
            if (longest >= 21.09) "DEFEATED" else "%.1f km to go".format(21.1 - longest))
        out += Boss("marathon", "Sovereign of 42", "Marathon under 3:00", 42.2 - 21.1, (42.2 - longest).coerceAtLeast(0.0).coerceAtMost(21.1),
            "%.1f km to go".format((42.2 - longest).coerceAtLeast(0.0)), locked = longest < 21.09)
        val w = state.latestWeight
        out += Boss("mass", "Titan of Mass", "Reach ${Fmt.kg(state.profile.targetKg)}", (state.profile.targetKg - state.profile.startKg).coerceAtLeast(0.5),
            (state.profile.targetKg - w).coerceAtLeast(0.0), if (w >= state.profile.targetKg) "DEFEATED" else "%.1f kg to go".format(state.profile.targetKg - w))
        val squatGoal = Records.e1rm(state.latestWeight * 1.25, 5)
        val squatFirst = state.gym.sortedBy { it.date }.flatMap { it.entries }.firstOrNull { it.exercise == Exercises.SQUAT }?.let { Records.e1rm(it.kg, it.reps) } ?: 40.0
        val squatBest = Records.bestE1rm(state, Exercises.SQUAT)
        out += Boss("iron", "Iron Colossus", "Squat 1.25× bodyweight for 5", (squatGoal - squatFirst).coerceAtLeast(5.0), (squatGoal - squatBest.coerceAtLeast(squatFirst)).coerceAtLeast(0.0),
            if (squatBest >= squatGoal) "DEFEATED" else "${(squatGoal - squatBest.coerceAtLeast(squatFirst)).roundToInt()} kg of strength to go")
        if (state.screen.enabled) {
            val recent = state.screenDays.sortedBy { it.date }.takeLast(7)
            val avg = if (recent.isEmpty()) state.screen.baselineMin.toDouble() else recent.map { it.usedMin }.average()
            val floor = state.screen.floorMin.toDouble()
            out += Boss("wraith", "The Screen Wraith", "TikTok + YouTube down to ${Fmt.hours(state.screen.floorMin)} a day",
                (state.screen.baselineMin - floor).coerceAtLeast(30.0), (avg - floor).coerceAtLeast(0.0),
                if (avg <= floor) "DEFEATED" else "${Fmt.hours((avg - floor).roundToInt())} a day still feeding it")
        }
        return out
    }

    fun settleBosses(state: AppState, today: LocalDate, events: MutableList<GameEvent>): AppState {
        var s = state
        for (b in bosses(s)) {
            if (b.defeated && b.id !in s.defeatedBosses) {
                s = s.copy(defeatedBosses = s.defeatedBosses + b.id, gold = s.gold + 300)
                s = addItem(s, Item.MYSTERY_BOX, 3)
                s = Game.grantXp(s, Skill.FOCUS, BOSS_XP, today, events, multiply = false)
                events += GameEvent.Message("[SYSTEM] BOSS DEFEATED: ${b.name}! +$BOSS_XP XP, 300 gold, 3 boxes.")
            }
        }
        return s
    }

    // ---------- Dungeon: a focus timer for study, reading and money work ----------

    val DUNGEON_SKILLS = listOf(Skill.STUDYING, Skill.READING, Skill.MONEY)

    /**
     * A finished dungeon logs its minutes like a normal session (so quests and gates count it)
     * and adds a clear bonus. Leaving early still logs the minutes, without the bonus.
     */
    fun dungeon(state: AppState, today: LocalDate, skill: Skill, minutes: Int, cleared: Boolean, events: MutableList<GameEvent>): AppState {
        if (minutes <= 0) return state
        var s = state.copy(minutes = state.minutes + MinutesLog(state.nextId, today.iso(), skill, minutes), nextId = state.nextId + 1)
        if (cleared) {
            val xp = (minutes / 2).coerceAtLeast(5)
            val gold = minutes / 3
            s = Game.grantXp(s, skill, xp, today, events)
            s = s.copy(gold = s.gold + gold)
            events += GameEvent.Message("[SYSTEM] Dungeon cleared: $minutes min of ${skill.label.lowercase()}. Bonus +$xp XP, +$gold gold.")
        } else {
            events += GameEvent.Message("[SYSTEM] You left the dungeon. $minutes min logged, no clear bonus.")
        }
        return s
    }

    // ---------- Job change ----------

    fun jobUnlocked(state: AppState) = Leveling.level(state.totalXp) >= JOB_LEVEL

    fun startJobTrial(state: AppState, today: LocalDate): AppState =
        if (!jobUnlocked(state) || state.job != null || state.jobTrialStart != null) state
        else state.copy(jobTrialStart = today.iso(), jobTrialBase = state.momentum.streak)

    fun jobTrialProgress(state: AppState): Int =
        if (state.jobTrialStart == null) 0 else (state.momentum.streak - state.jobTrialBase).coerceIn(0, JOB_TRIAL_DAYS)

    fun canChooseJob(state: AppState) = state.job == null && state.jobTrialStart != null && jobTrialProgress(state) >= JOB_TRIAL_DAYS

    fun chooseJob(state: AppState, job: JobClass): AppState = if (!canChooseJob(state)) state else state.copy(job = job)

    /** A bad day lowers momentum below the trial's start: restart the count from there instead of going negative. */
    fun keepTrialFair(state: AppState): AppState =
        if (state.jobTrialStart != null && state.job == null && state.momentum.streak < state.jobTrialBase) state.copy(jobTrialBase = state.momentum.streak) else state
}

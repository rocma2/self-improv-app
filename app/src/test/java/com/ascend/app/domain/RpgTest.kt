package com.ascend.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class RpgTest {
    private val start = LocalDate.of(2026, 11, 23)
    private fun fresh() = Game.completeOnboarding(AppState(), Profile(name = "T", winterMode = WinterMode.OFF), start)

    @Test
    fun levelUpGivesStatPointsAndGold() {
        val events = mutableListOf<GameEvent>()
        val s = Game.grantXp(fresh(), Skill.READING, 400, start, events, multiply = false)
        assertEquals(3, Leveling.level(s.totalXp))
        assertEquals(6, s.statPoints)
        assertTrue(s.gold > 0)
        val a = Rpg.allocate(s, "STR")
        assertEquals(5, a.statPoints)
        assertEquals(Rpg.attributes(s).first { it.first == "STR" }.second + 1, Rpg.attributes(a).first { it.first == "STR" }.second)
    }

    @Test
    fun threeGatesPerWeekAndRedGateIsNeverTraining() {
        val s = Rpg.ensureGates(fresh(), start)
        val week = s.gates.filter { it.start == start.toString() }
        assertEquals(3, week.size)
        week.filter { it.red }.forEach { assertTrue(it.metric != GateMetric.RUN_KM && it.metric != GateMetric.GYM) }
        assertEquals(s, Rpg.ensureGates(s, start.plusDays(2)))
    }

    @Test
    fun enteredGateClearsAndFails() {
        var s = Rpg.ensureGates(fresh(), start)
        val g = s.gates.first()
        s = Rpg.enterGate(s, g.id)
        val forced = s.copy(gates = s.gates.map { if (it.id == g.id) it.copy(metric = GateMetric.READ_MIN, target = 30.0, skill = Skill.READING) else it })
        val cleared = Game.logMinutes(forced, start, start, Skill.READING, 30).state
        assertEquals(GateStatus.CLEARED, cleared.gates.first { it.id == g.id }.status)
        val failed = Rpg.settleGates(forced, start.plusDays(8), mutableListOf())
        assertEquals(GateStatus.FAILED, failed.gates.first { it.id == g.id }.status)
    }

    @Test
    fun bossesTrackGoalsAndPayOnce() {
        var s = fresh()
        assertFalse(Rpg.bosses(s).first { it.id == "mass" }.defeated)
        s = Game.logWeight(s, start, start, 72.5).state
        assertTrue("mass" in s.defeatedBosses)
        val gold = s.gold
        s = Game.logWeight(s, start, start.plusDays(1), 73.0).state
        assertEquals(1, s.defeatedBosses.count { it == "mass" })
        assertTrue(s.gold >= gold)
        assertTrue(Rpg.bosses(s).first { it.id == "marathon" }.locked)
    }

    @Test
    fun dailyRewardOncePerDay() {
        var s = fresh()
        val today = start // Monday: gym, sleep, food, read, study, money
        s = Game.logGym(s, today, today, listOf(LiftEntry(Exercises.SQUAT, 40.0, 5, 3)), false).state
        s = Game.logSleep(s, today, today, 8.0).state
        s = Game.logFood(s, today, today).state
        s = Game.logMinutes(s, today, today, Skill.READING, 20).state
        s = Game.logMinutes(s, today, today, Skill.STUDYING, 45).state
        val before = s.inventory[Item.MYSTERY_BOX] ?: 0
        s = Game.logMinutes(s, today, today, Skill.MONEY, 30).state
        assertTrue(today.toString() in s.dailyRewardDays)
        assertEquals(before + 1, s.inventory[Item.MYSTERY_BOX])
        val again = Game.logMinutes(s, today, today, Skill.MONEY, 30).state
        assertEquals(s.inventory[Item.MYSTERY_BOX], again.inventory[Item.MYSTERY_BOX])
    }

    @Test
    fun itemsWork() {
        var s = fresh().copy(gold = 1000)
        s = Rpg.buy(s, Item.ELIXIR)
        assertEquals(900, s.gold)
        s = s.copy(skills = s.skills + (Skill.SLEEP to SkillState(0, 10)))
        s = Rpg.useItem(s, Item.ELIXIR, start, mutableListOf())
        assertEquals(50, s.skill(Skill.SLEEP).hp)
        s = Rpg.useItem(s, Item.MYSTERY_BOX, start, mutableListOf(), Random(1))
        assertEquals(0, s.inventory[Item.MYSTERY_BOX])
        s = Rpg.addReward(s, "Cheat meal", 400)
        s = Rpg.buyReward(s, s.customRewards.first().id)
        assertTrue(s.gold < 900)
    }

    @Test
    fun jobChangeNeedsLevelAndSevenGoodDays() {
        var s = fresh()
        assertEquals(s, Rpg.startJobTrial(s, start))
        s = s.copy(totalXp = Leveling.xpForLevel(10))
        s = Rpg.startJobTrial(s, start)
        assertFalse(Rpg.canChooseJob(s))
        s = s.copy(momentum = s.momentum.copy(streak = s.jobTrialBase + 7))
        s = Rpg.chooseJob(s, JobClass.STRIKER)
        assertEquals(JobClass.STRIKER, s.job)
        assertEquals(1.15, Rpg.jobMultiplier(s, Skill.RUNNING), 0.001)
    }

    @Test
    fun dungeonLogsMinutesAndBonusOnlyWhenCleared() {
        val base = fresh()
        val e = mutableListOf<GameEvent>()
        val won = Rpg.dungeon(base, start, Skill.STUDYING, 45, cleared = true, events = e)
        val left = Rpg.dungeon(base, start, Skill.STUDYING, 20, cleared = false, events = e)
        assertEquals(45, won.minutes.single().minutes)
        assertEquals(20, left.minutes.single().minutes)
        assertTrue(won.totalXp > base.totalXp && won.gold > base.gold)
        assertEquals(base.totalXp, left.totalXp)
    }

    @Test
    fun ranksGrowExponentially() {
        assertEquals("E", Leveling.rank(0))
        assertEquals("D", Leveling.rank(2_500))
        assertEquals("C", Leveling.rank(7_500))
        assertEquals("B", Leveling.rank(22_500))
        assertEquals("A", Leveling.rank(67_500))
        assertEquals("S", Leveling.rank(202_500))
        for (i in 2 until Leveling.RANKS.size) assertEquals(3 * Leveling.xpForRank(i - 1), Leveling.xpForRank(i))
        val e = mutableListOf<GameEvent>()
        val s = Game.grantXp(fresh().copy(totalXp = 2_400), Skill.READING, 200, start, e, multiply = false)
        assertTrue(e.any { it is GameEvent.RankUp && it.rank == "D" })
        assertTrue(s.statPoints >= 5 && s.gold >= 500)
    }

    private fun runDay(): Pair<AppState, LocalDate> {
        var s = fresh()
        var d = start
        repeat(10) { if (Rpg.skippableWorkout(s, d) == null) d = d.plusDays(1) }
        return s to d
    }

    @Test
    fun skipPassIsLimitedAndGetsPricier() {
        val (base, d) = runDay()
        val e = mutableListOf<GameEvent>()
        assertTrue(Rpg.skippableWorkout(base, d) != null)
        assertFalse(Rpg.canSkip(base, d))
        val rich = base.copy(gold = 5_000)
        val skipped = Rpg.skipWorkout(rich, d, e)
        assertEquals(5_000 - Rpg.SKIP_BASE_PRICE, skipped.gold)
        assertEquals(QuestStatus.REST, skipped.questRecords.last().status)
        assertFalse(Rpg.canSkip(skipped, d))
        assertEquals(2 * Rpg.SKIP_BASE_PRICE, Rpg.skipPrice(skipped, d.plusDays(7)))
        assertEquals(Rpg.SKIP_BASE_PRICE, Rpg.skipPrice(skipped, d.plusDays(28)))
    }

    @Test
    fun shieldBlocksOneMissAndPotionBoostsXp() {
        val base = fresh().let { it.copy(profile = it.profile.copy(difficulty = Difficulty.NEW_LIFE)) }
        val shielded = Rpg.addItem(base, Item.SHIELD)
        val r1 = Game.evaluate(base, start.plusDays(1)) { null }.state
        val r2 = Game.evaluate(shielded, start.plusDays(1)) { null }.state
        assertTrue(r2.skills.values.sumOf { it.hp } > r1.skills.values.sumOf { it.hp })
        assertEquals(0, r2.inventory[Item.SHIELD])
        val e = mutableListOf<GameEvent>()
        val boosted = Rpg.useItem(Rpg.addItem(base, Item.XP_POTION), Item.XP_POTION, start, e)
        assertEquals(Game.xpMultiplier(base, start) * 1.5, Game.xpMultiplier(boosted, start), 1e-9)
        assertEquals(Game.xpMultiplier(base, start.plusDays(1)), Game.xpMultiplier(boosted, start.plusDays(1)), 1e-9)
    }

    @Test
    fun radarHasFiveSubStatsPerSkillInRange() {
        var s = fresh()
        s = Game.logRun(s, start, start, 5.0, 25 * 60, RunType.EASY, Surface.OUTDOOR, null).state
        for (sk in Skill.entries) {
            val subs = SkillRadar.subStats(s, sk, start)
            assertEquals(5, subs.size)
            subs.forEach { assertTrue(it.score in 0..100) }
        }
        assertTrue(SkillRadar.subStats(s, Skill.RUNNING, start).first { it.name == "Speed" }.score > 0)
        assertEquals(8, SkillRadar.overview(s).size)
    }

    @Test
    fun checkInTrackGivesRewardsOncePerDay() {
        var s = fresh()
        val e = mutableListOf<GameEvent>()
        s = Rpg.checkIn(s, start, e)
        val g = s.gold
        assertEquals(s, Rpg.checkIn(s, start, e))
        for (i in 1..6) s = Rpg.checkIn(s, start.plusDays(i.toLong()), e)
        assertEquals(0, Rpg.checkInIndex(s))
        assertTrue(s.gold > g && (s.inventory[Item.SHIELD] ?: 0) == 1)
        val hidden = (0 until 300).count { Rpg.hiddenQuestDay(start.plusDays(it.toLong())) }
        assertTrue(hidden in 60..140)
    }
}

package com.ascend.app.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GameTest {
    // A Monday, so week 1 starts here.
    private val start = LocalDate.of(2026, 11, 23)

    private fun fresh(mode: Difficulty = Difficulty.NEW_LIFE): AppState =
        Game.completeOnboarding(AppState(), Profile(name = "Test", difficulty = mode, winterMode = WinterMode.OFF), start)

    @Test
    fun levelCurve() {
        assertEquals(1, Leveling.level(0))
        assertEquals(2, Leveling.level(100))
        assertEquals(2, Leveling.level(299))
        assertEquals(3, Leveling.level(300))
        assertEquals("Rookie", Leveling.title(1))
        assertEquals("Challenger", Leveling.title(12))
    }

    @Test
    fun weeklyTargetGrowsAtMostTenPercentAndDeloads() {
        var s = fresh()
        // Do the plan in week 1-3.
        for (w in 0 until 3) {
            val ws = start.plusWeeks(w.toLong())
            s = Game.logRun(s, ws, ws, 20.0, 6000, RunType.EASY, Surface.OUTDOOR, null).state
        }
        val today = start.plusWeeks(4)
        assertEquals(8.0, TrainingPlan.targetKm(s, 1, today), 0.01)
        assertEquals(9.0, TrainingPlan.targetKm(s, 2, today), 0.51)
        val w3 = TrainingPlan.targetKm(s, 3, today)
        assertTrue(w3 <= 8.0 * 1.1 * 1.1 + 0.5)
        val w4 = TrainingPlan.targetKm(s, 4, today)
        assertTrue("deload is lighter", w4 < w3)
    }

    @Test
    fun targetHoldsWhenWeekMissed() {
        val s = fresh()
        val today = start.plusWeeks(1)
        // Nothing run in week 1, so week 2 steps back instead of growing.
        assertTrue(TrainingPlan.targetKm(s, 2, today) <= 8.0)
    }

    @Test
    fun runDaysHaveSpeedRecoveryLong() {
        val s = fresh()
        val tue = start.plusDays(1)
        val thu = start.plusDays(3)
        val sat = start.plusDays(5)
        assertEquals(RunType.SPEED, TrainingPlan.plannedRun(s, tue)!!.type)
        assertEquals(RunType.RECOVERY, TrainingPlan.plannedRun(s, thu)!!.type)
        assertEquals(RunType.LONG, TrainingPlan.plannedRun(s, sat)!!.type)
        assertEquals(null, TrainingPlan.plannedRun(s, start))
        assertEquals("Strength A", TrainingPlan.gymSession(s, start)!!.name)
        assertEquals("Strength B", TrainingPlan.gymSession(s, start.plusDays(4))!!.name)
    }

    @Test
    fun loggingCompletesQuestAndGivesXp() {
        val s = fresh()
        val r = Game.logMinutes(s, start, start, Skill.READING, 20)
        assertTrue(r.state.totalXp > 0)
        assertTrue(r.state.questRecords.any { it.questId == "$start:READ" && it.status == QuestStatus.DONE })
        assertTrue(r.events.any { it is GameEvent.QuestDone })
    }

    @Test
    fun missedDayCostsHpAndCreatesOnePenaltyAndLocks() {
        val s = fresh()
        val next = start.plusDays(1)
        val r = Game.evaluate(s, next) { null }
        val st = r.state
        assertTrue(st.skill(Skill.GYM).hp < 100)
        assertEquals(1, st.penalties.size)
        assertEquals(Skill.GYM, st.penalties.first().skill)
        assertTrue(Game.penaltyLockActive(st, next))
        val cleared = Game.completePenalty(st, next, st.penalties.first().id).state
        assertFalse(Game.penaltyLockActive(cleared, next))
        assertTrue("Paid in full" in cleared.badges.keys.mapNotNull { Badges.byId(it)?.title })
    }

    @Test
    fun softModeNeverPunishes() {
        val s = fresh(Difficulty.SOFT)
        val st = Game.evaluate(s, start.plusDays(3)) { null }.state
        assertEquals(100, st.skill(Skill.GYM).hp)
        assertTrue(st.penalties.isEmpty())
    }

    @Test
    fun restDayIsNeverAMiss() {
        var s = fresh()
        s = Game.takeRestDay(s, start)
        val st = Game.evaluate(s, start.plusDays(1)) { null }.state
        assertTrue(st.questRecords.none { it.status == QuestStatus.MISSED })
        assertTrue(st.penalties.isEmpty())
        assertFalse(Game.canTakeRestDay(st, start.plusDays(1)))
    }

    @Test
    fun deloadWeekTrainingIsOptional() {
        val s = fresh()
        val deloadMonday = start.plusWeeks(3)
        val gym = QuestEngine.questsFor(s, deloadMonday).first { it.kind == QuestKind.GYM }
        assertTrue(gym.optional)
    }

    @Test
    fun momentumNeverResetsToZero() {
        var s = fresh().copy(momentum = Momentum(streak = 20, best = 20, freezes = 0))
        s = Game.evaluate(s, start.plusDays(1)) { null }.state
        assertEquals(15, s.momentum.streak)
        assertEquals(20, s.momentum.best)
    }

    @Test
    fun hpZeroDropsSkillLevel() {
        var s = fresh().copy(skills = Skill.entries.associateWith { SkillState(xp = Leveling.skillXpForLevel(5) + 10, hp = 15) })
        val events = mutableListOf<GameEvent>()
        s = Game.damage(s, Skill.READING, 20, events)
        assertEquals(4, Leveling.skillLevel(s.skill(Skill.READING).xp))
        assertEquals(60, s.skill(Skill.READING).hp)
        assertTrue(events.first() is GameEvent.SkillLevelDown)
    }

    @Test
    fun screenTimeBudgetDropsAnHourAWeekAndPaysXp() {
        var s = Game.enableScreen(fresh(), start, 600)
        assertEquals(600, QuestEngine.screenBudget(s, start))
        assertEquals(540, QuestEngine.screenBudget(s, start.plusWeeks(1)))
        assertEquals(60, QuestEngine.screenBudget(s, start.plusWeeks(40)))
        s = Game.evaluate(s, start.plusDays(1)) { 300 }.state
        val day = s.screenDays.single()
        assertEquals(300, day.usedMin)
        assertEquals(100, day.xp) // 5 h won back x 20
        assertTrue(s.questRecords.any { it.questId == "$start:SCREEN" && it.status == QuestStatus.DONE })
    }

    @Test
    fun recordsAndBadges() {
        var s = fresh()
        s = Game.logRun(s, start, start, 5.0, 25 * 60, RunType.EASY, Surface.OUTDOOR, -16).state
        assertTrue("cold15" in s.badges)
        assertTrue("first_5k" in s.badges)
        val r = Game.logRun(s, start.plusDays(1), start.plusDays(1), 5.0, 23 * 60, RunType.SPEED, Surface.OUTDOOR, 2)
        assertTrue(r.events.any { it is GameEvent.NewRecord && it.title.startsWith("Best 5K") })
        assertEquals(23 * 60, Records.best5k(r.state)!!.first)
    }

    @Test
    fun weeklyChestLostAtThreeMissesInNewLife() {
        val s = fresh()
        val st = Game.evaluate(s, start.plusDays(7)) { null }.state
        val wr = st.weeklyResults.single()
        assertFalse(wr.rewarded)
    }

    @Test
    fun seasonCreatedWithGoals() {
        val s = fresh()
        val season = Seasons.current(s, start)!!
        assertEquals(1, season.number)
        assertTrue(season.goals.size >= 6)
    }

    @Test
    fun stateSurvivesJsonRoundTrip() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val s = Game.logRun(fresh(), start, start, 6.0, 2000, RunType.LONG, Surface.TREADMILL, null).state
        val back = json.decodeFromString(AppState.serializer(), json.encodeToString(AppState.serializer(), s))
        assertEquals(s, back)
    }

    @Test
    fun recapCountsMonth() {
        var s = fresh()
        s = Game.logRun(s, start, start, 5.0, 1700, RunType.EASY, Surface.OUTDOOR, null).state
        s = Game.logMinutes(s, start, start, Skill.READING, 30).state
        val rc = Stats.recap(s, java.time.YearMonth.from(start))
        assertEquals(1, rc.runs)
        assertEquals(30, rc.readMin)
        assertTrue(rc.xp > 0)
    }
}

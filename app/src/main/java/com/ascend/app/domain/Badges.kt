package com.ascend.app.domain

import java.time.Month

data class Badge(val id: String, val emoji: String, val title: String, val description: String, val check: (AppState) -> Boolean)

/** Badges are earned from real life, not from tapping buttons. */
object Badges {
    private fun totalKm(s: AppState) = s.runs.sumOf { TrainingPlan.runEquivalentKm(it) }
    private fun foot(s: AppState) = s.runs.filter { it.surface != Surface.INDOOR_BIKE }
    private fun minutes(s: AppState, skill: Skill) = s.minutes.filter { it.skill == skill }.sumOf { it.minutes }
    private fun wintry(d: String) = d.toDate().month in setOf(Month.DECEMBER, Month.JANUARY, Month.FEBRUARY)

    val all: List<Badge> = listOf(
        Badge("first_run", "👟", "First steps", "Log your first run") { foot(it).isNotEmpty() },
        Badge("first_5k", "5️⃣", "5K finisher", "Run 5 km in one go") { s -> foot(s).any { it.km >= 4.95 } },
        Badge("first_10k", "🔟", "Double digits", "Run 10 km in one go") { s -> foot(s).any { it.km >= 9.95 } },
        Badge("half", "🥈", "Half marathoner", "Run 21.1 km in one go") { s -> foot(s).any { it.km >= 21.09 } },
        Badge("sub20", "⚡", "Sub-20", "5K under 20:00") { s -> (Records.best5k(s)?.first ?: Int.MAX_VALUE) < 20 * 60 },
        Badge("sub19", "🚀", "Sub-19", "5K under 19:00, this year's big goal") { s -> (Records.best5k(s)?.first ?: Int.MAX_VALUE) < 19 * 60 },
        Badge("km100", "💯", "Century", "100 km run in total") { totalKm(it) >= 100 },
        Badge("km500", "🛣️", "Road warrior", "500 km run in total") { totalKm(it) >= 500 },
        Badge("cold5", "🥶", "Cold start", "Run outside at -5 °C or colder") { s -> s.runs.any { it.surface == Surface.OUTDOOR && (it.tempC ?: 99) <= -5 } },
        Badge("cold15", "🧊", "Frostbite club", "Run outside at -15 °C or colder") { s -> s.runs.any { it.surface == Surface.OUTDOOR && (it.tempC ?: 99) <= -15 } },
        Badge("winter12", "❄️", "Winter arc", "12 runs in December to February") { s -> s.runs.count { wintry(it.date) } >= 12 },
        Badge("bixi", "🚲", "Pedal power", "Log 100 km of bike commutes") { s -> s.bikes.sumOf { it.km } >= 100 },
        Badge("gym10", "🏋️", "Iron habit", "10 strength sessions") { it.gym.size >= 10 },
        Badge("squat_bw", "🦵", "Bodyweight squat", "Squat your bodyweight for 5 reps") { s ->
            s.gym.flatMap { it.entries }.any { it.exercise == Exercises.SQUAT && it.reps >= 5 && it.kg >= s.latestWeight }
        },
        Badge("dl_15", "🪨", "Heavy lifter", "Deadlift 1.5× bodyweight") { s ->
            s.gym.flatMap { it.entries }.any { it.exercise == Exercises.DEADLIFT && it.kg >= s.latestWeight * 1.5 }
        },
        Badge("pullup10", "🧗", "Ten clean", "10 pull-ups in one set") { s -> s.gym.flatMap { it.entries }.any { it.exercise == Exercises.PULLUP && it.reps >= 10 } },
        Badge("gain2", "📈", "Building up", "+2 kg from your start weight") { it.latestWeight >= it.profile.startKg + 2 },
        Badge("target", "🎯", "Target weight", "Reach your target weight") { it.latestWeight >= it.profile.targetKg },
        Badge("weigh8", "⚖️", "Data driven", "8 weekly weigh-ins") { it.weighIns.size >= 8 },
        Badge("read10h", "📚", "Bookworm", "10 hours of reading") { minutes(it, Skill.READING) >= 600 },
        Badge("study25h", "🧠", "Scholar", "25 hours of studying") { minutes(it, Skill.STUDYING) >= 1500 },
        Badge("money10h", "💸", "Hustle", "10 hours on money tasks") { minutes(it, Skill.MONEY) >= 600 },
        Badge("sleep7", "🌙", "Well rested", "7 nights in a row at your sleep target") { s -> sleepRun(s) >= 7 },
        Badge("screen2h", "📵", "Unplugged", "A day under 2 h on TikTok + YouTube") { s -> s.screenDays.any { it.usedMin < 120 } },
        Badge("screen100", "⏳", "Time thief", "Win back 100 hours from social apps") { s ->
            s.screenDays.sumOf { (s.screen.baselineMin - it.usedMin).coerceAtLeast(0) } >= 6000
        },
        Badge("penance", "⚔️", "Paid in full", "Complete a penalty quest") { s -> s.penalties.any { it.done } },
        Badge("streak14", "🔥", "On fire", "Momentum of 14") { it.momentum.best >= 14 },
        Badge("streak50", "☄️", "Unstoppable", "Momentum of 50") { it.momentum.best >= 50 },
        Badge("chest4", "🧰", "Clean month", "Win 4 weekly chests") { s -> s.weeklyResults.count { it.rewarded } >= 4 },
        Badge("season1", "🏆", "Season clear", "Finish every goal in a season") { s -> s.seasons.any { se -> se.goals.isNotEmpty() && se.claimed.size >= se.goals.size } },
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }

    private fun sleepRun(s: AppState): Int {
        val good = s.sleeps.filter { it.hours >= s.schedule.sleepHours }.map { it.date.toDate() }.toSortedSet()
        var best = 0
        var run = 0
        var prev: java.time.LocalDate? = null
        for (d in good) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }
}

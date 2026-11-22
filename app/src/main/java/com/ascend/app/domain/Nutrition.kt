package com.ascend.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** Daily fuel target for gaining weight while running and biking. */
data class Fuel(val kcal: Int, val proteinG: Int, val weeklyGainKg: Double?, val note: String)

object Nutrition {
    /**
     * Start from about 38 kcal per kg (active: runs, gym, 10 km of bike a day) plus a 400 kcal surplus,
     * then nudge it from your real weigh-ins: gaining under 0.2 kg a week adds 250, over 0.6 removes 250.
     * Protein: 1.8 g per kg. A rough guide, not medical advice.
     */
    fun target(state: AppState, today: LocalDate): Fuel {
        val kg = state.latestWeight
        var kcal = kg * 38 + 400
        val recent = state.weighIns.filter { !it.date.toDate().isBefore(today.minusDays(28)) }.sortedBy { it.date }
        val gain = if (recent.size >= 2) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(recent.first().date.toDate(), recent.last().date.toDate())
            if (days >= 10) (recent.last().kg - recent.first().kg) / days * 7 else null
        } else null
        val note = when {
            kg >= state.profile.targetKg -> { kcal -= 400; "Goal weight reached: eat at maintenance to keep it." }
            gain == null -> "Weigh in weekly so the target can adjust to you."
            gain < 0.2 -> { kcal += 250; "Gaining slowly (%+.2f kg/week): +250 kcal added.".format(gain) }
            gain > 0.6 -> { kcal -= 250; "Gaining fast (%+.2f kg/week): -250 kcal so it's mostly muscle.".format(gain) }
            else -> "Right on pace (%+.2f kg/week). Keep it up.".format(gain)
        }
        return Fuel(((kcal / 50).roundToInt() * 50), (kg * 1.8).roundToInt(), gain, note)
    }

    val IDEAS = listOf(
        "Big glass of milk + 2 PB toasts (~600 kcal)",
        "Oats with banana, milk and peanut butter (~700 kcal)",
        "Rice bowl with chicken, eggs and avocado (~900 kcal)",
        "Greek yogurt, granola and honey (~450 kcal)",
        "Pasta with ground beef sauce (~900 kcal)",
        "Smoothie: milk, banana, oats, PB (~700 kcal)",
    )
}

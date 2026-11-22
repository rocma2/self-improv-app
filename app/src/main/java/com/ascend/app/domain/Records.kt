package com.ascend.app.domain

import kotlin.math.roundToInt

data class RecordEntry(val key: String, val title: String, val value: String, val date: String)

object Fmt {
    fun duration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun pace(secPerKm: Double): String {
        val t = secPerKm.roundToInt()
        return "%d:%02d/km".format(t / 60, t % 60)
    }

    fun km(v: Double): String = if (v % 1.0 == 0.0) "%.0f km".format(v) else "%.1f km".format(v)
    fun kg(v: Double): String = if (v % 1.0 == 0.0) "%.0f kg".format(v) else "%.1f kg".format(v)
    fun hours(minutes: Int): String = if (minutes >= 60) "%.1f h".format(minutes / 60.0) else "$minutes min"
}

object Records {
    private fun footRuns(state: AppState) = state.runs.filter { it.surface != Surface.INDOOR_BIKE && it.seconds > 0 && it.km > 0 }

    /** Best 5K estimate in seconds, from any run of at least 5 km (pace held over 5 km). */
    fun best5k(state: AppState): Pair<Int, String>? =
        footRuns(state).filter { it.km >= 4.95 }
            .map { (it.seconds * 5.0 / it.km).roundToInt() to it.date }
            .minByOrNull { it.first }

    fun bestOver(state: AppState, km: Double): Pair<Int, String>? =
        footRuns(state).filter { it.km >= km - 0.05 }
            .map { (it.seconds * km / it.km).roundToInt() to it.date }
            .minByOrNull { it.first }

    fun e1rm(kg: Double, reps: Int): Double = if (reps <= 1) kg else kg * (1 + reps / 30.0)

    fun bestE1rm(state: AppState, exercise: String): Double =
        state.gym.flatMap { it.entries }.filter { it.exercise == exercise }.maxOfOrNull { e1rm(it.kg, it.reps) } ?: 0.0

    fun easyPaceHint(state: AppState): String {
        val b = best5k(state) ?: return ""
        val p = b.first / 5.0
        return " Easy pace for you: ${Fmt.pace(p + 75)} to ${Fmt.pace(p + 105)}."
    }

    /** All current personal records, used for the records list and for spotting new ones. */
    fun all(state: AppState): List<RecordEntry> {
        val out = mutableListOf<RecordEntry>()
        footRuns(state).maxByOrNull { it.km }?.let { out += RecordEntry("longest", "Longest run", Fmt.km(it.km), it.date) }
        best5k(state)?.let { out += RecordEntry("5k", "Best 5K", Fmt.duration(it.first), it.second) }
        bestOver(state, 10.0)?.let { out += RecordEntry("10k", "Best 10K", Fmt.duration(it.first), it.second) }
        bestOver(state, 21.0975)?.let { out += RecordEntry("half", "Best half marathon", Fmt.duration(it.first), it.second) }
        footRuns(state).filter { it.km >= 3 }.minByOrNull { it.seconds / it.km }?.let {
            out += RecordEntry("pace", "Best pace (3 km+)", Fmt.pace(it.seconds / it.km), it.date)
        }
        val weeks = state.runs.groupBy { it.date.toDate().weekStart() }
            .mapValues { (_, r) -> r.sumOf { TrainingPlan.runEquivalentKm(it) } }
        weeks.maxByOrNull { it.value }?.let { out += RecordEntry("week", "Biggest week", Fmt.km((it.value * 10).roundToInt() / 10.0), it.key.iso()) }
        for (ex in Exercises.all) {
            val entries = state.gym.flatMap { g -> g.entries.filter { it.exercise == ex }.map { it to g.date } }
            if (entries.isEmpty()) continue
            if (ex in Exercises.loaded) {
                val best = entries.maxBy { it.first.kg }
                out += RecordEntry("lift:$ex", ex, "${Fmt.kg(best.first.kg)} × ${best.first.reps}", best.second)
            } else {
                val best = entries.maxBy { it.first.kg * 100 + it.first.reps }
                val load = if (best.first.kg > 0) " +${Fmt.kg(best.first.kg)}" else ""
                out += RecordEntry("lift:$ex", ex, "${best.first.reps}$load", best.second)
            }
        }
        state.weighIns.maxByOrNull { it.kg }?.let { out += RecordEntry("weight", "Heaviest weigh-in", Fmt.kg(it.kg), it.date) }
        return out
    }

    /** Records that are new or changed between [before] and [after]. */
    fun newRecords(before: AppState, after: AppState): List<RecordEntry> {
        val old = all(before).associateBy { it.key }
        return all(after).filter { r ->
            val o = old[r.key]
            r.key != "week" && (o == null && hadAnyBefore(before, r.key) || (o != null && o.value != r.value))
        }
    }

    /** A first-ever log isn't a "record" worth celebrating, the 2nd improvement is. */
    private fun hadAnyBefore(before: AppState, key: String): Boolean = when {
        key.startsWith("lift:") -> before.gym.any { g -> g.entries.any { it.exercise == key.removePrefix("lift:") } }
        key == "weight" -> before.weighIns.isNotEmpty()
        else -> before.runs.isNotEmpty()
    }
}

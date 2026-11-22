package com.ascend.app.domain

data class LevelInfo(val level: Int, val intoLevel: Long, val levelSize: Long) {
    val fraction: Float get() = if (levelSize <= 0) 0f else (intoLevel.toFloat() / levelSize).coerceIn(0f, 1f)
}

object Leveling {
    /** Total XP needed to reach [level] (level 1 = 0 XP). Level 2 = 100, 3 = 300, 4 = 600 ... */
    fun xpForLevel(level: Int): Long = 50L * level * (level - 1)

    fun level(xp: Long): Int {
        var l = 1
        while (xpForLevel(l + 1) <= xp) l++
        return l
    }

    fun info(xp: Long): LevelInfo {
        val l = level(xp)
        return LevelInfo(l, xp - xpForLevel(l), xpForLevel(l + 1) - xpForLevel(l))
    }

    /** Skills level on a smaller curve: 2 = 40, 3 = 120, 4 = 240 ... */
    fun skillXpForLevel(level: Int): Long = 20L * level * (level - 1)

    fun skillLevel(xp: Long): Int {
        var l = 1
        while (skillXpForLevel(l + 1) <= xp) l++
        return l
    }

    fun skillInfo(xp: Long): LevelInfo {
        val l = skillLevel(xp)
        return LevelInfo(l, xp - skillXpForLevel(l), skillXpForLevel(l + 1) - skillXpForLevel(l))
    }

    private val titles = listOf(
        1 to "Rookie",
        5 to "Initiate",
        10 to "Challenger",
        15 to "Pathfinder",
        20 to "Vanguard",
        30 to "Elite",
        40 to "Warden",
        50 to "Titan",
        65 to "Legend",
        80 to "Mythic",
    )

    fun title(level: Int): String = titles.last { level >= it.first }.second

    fun nextTitle(level: Int): Pair<Int, String>? = titles.firstOrNull { it.first > level }

    /**
     * Letter ranks grow exponentially: each rank needs 3x the total XP of the one before.
     * D 2,500 · C 7,500 · B 22,500 · A 67,500 · S 202,500. At a normal pace that is about
     * a week to D, a month to C, a couple of months to B, half a year to A and close to two years to S.
     */
    val RANKS = listOf("E", "D", "C", "B", "A", "S")
    const val RANK_BASE_XP = 2_500L
    const val RANK_FACTOR = 3L

    fun xpForRank(index: Int): Long {
        if (index <= 0) return 0
        var x = RANK_BASE_XP
        repeat(index - 1) { x *= RANK_FACTOR }
        return x
    }

    fun rankIndex(xp: Long): Int = RANKS.indices.last { xpForRank(it) <= xp }

    fun rank(xp: Long): String = RANKS[rankIndex(xp)]

    /** Next rank, the total XP it needs, and how far you are from the current rank to it (0..1). */
    fun nextRank(xp: Long): Triple<String, Long, Float>? {
        val i = rankIndex(xp)
        if (i >= RANKS.lastIndex) return null
        val from = xpForRank(i)
        val to = xpForRank(i + 1)
        return Triple(RANKS[i + 1], to, ((xp - from).toFloat() / (to - from)).coerceIn(0f, 1f))
    }

    /** Five attributes built from your skill levels. */
    fun attributes(state: AppState): List<Pair<String, Int>> {
        fun lv(s: Skill) = skillLevel(state.skill(s).xp)
        fun avg(a: Skill, b: Skill) = (lv(a) + lv(b) + 1) / 2
        return listOf(
            "STR" to lv(Skill.GYM),
            "AGI" to lv(Skill.RUNNING),
            "VIT" to avg(Skill.SLEEP, Skill.WEIGHT),
            "INT" to avg(Skill.READING, Skill.STUDYING),
            "WIL" to avg(Skill.FOCUS, Skill.MONEY),
        )
    }
}

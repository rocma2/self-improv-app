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

    /** Letter rank shown in the status window. */
    fun rank(level: Int): String = when {
        level >= 70 -> "S"
        level >= 50 -> "A"
        level >= 35 -> "B"
        level >= 20 -> "C"
        level >= 10 -> "D"
        else -> "E"
    }

    fun nextRank(level: Int): Pair<Int, String>? =
        listOf(10 to "D", 20 to "C", 35 to "B", 50 to "A", 70 to "S").firstOrNull { it.first > level }

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

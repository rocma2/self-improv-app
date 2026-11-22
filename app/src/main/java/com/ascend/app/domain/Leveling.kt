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
}

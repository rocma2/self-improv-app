package com.ascend.app.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Game
import com.ascend.app.domain.QuestEngine
import com.ascend.app.domain.iso
import java.time.LocalDate
import java.time.ZoneId

object ScreenTime {
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun lockServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, BlockerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /**
     * Minutes the [packages] were on screen during [date]. Null when we can't know
     * (no permission, or Android no longer keeps data that old).
     */
    fun minutesOn(context: Context, packages: List<String>, date: LocalDate): Int? {
        if (!hasUsageAccess(context)) return null
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        if (start > now) return 0
        val ms = foregroundMs(context, packages.toSet(), start, minOf(end, now)) ?: return null
        return (ms / 60_000).toInt()
    }

    private fun foregroundMs(context: Context, packages: Set<String>, start: Long, end: Long): Long? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        // Look back a bit so an app already open at midnight is counted.
        val events = usm.queryEvents(start - 6 * 3_600_000L, end) ?: return null
        val ev = UsageEvents.Event()
        val openSince = HashMap<String, Long>()
        var total = 0L
        var anyEventInRange = false
        fun add(from: Long, to: Long) {
            val a = maxOf(from, start)
            val b = minOf(to, end)
            if (b > a) total += b - a
        }
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.timeStamp >= start) anyEventInRange = true
            val pkg = ev.packageName ?: continue
            if (pkg !in packages) continue
            when (ev.eventType) {
                1 -> if (pkg !in openSince) openSince[pkg] = ev.timeStamp // resumed / moved to foreground
                2, 23 -> openSince.remove(pkg)?.let { add(it, ev.timeStamp) } // paused / stopped
            }
        }
        openSince.values.forEach { add(it, end) }
        // No events at all means Android has no data for that day.
        if (!anyEventInRange && end - start > 3_600_000L) return null
        return total
    }

    /** Average daily minutes over the last [days] full days, for setting your starting budget. */
    fun averageDaily(context: Context, packages: List<String>, days: Int = 7): Int? {
        val today = LocalDate.now()
        val values = (1..days).mapNotNull { minutesOn(context, packages, today.minusDays(it.toLong())) }
        if (values.isEmpty()) return null
        return values.average().toInt()
    }
}

/** Small shared settings the lock service reads without loading the whole app. */
object LockPrefs {
    private const val NAME = "ascend_lock"

    data class Lock(
        val penaltyLock: Boolean,
        val penaltyTitle: String,
        val budgetLock: Boolean,
        val budgetMin: Int,
        val packages: Set<String>,
        val day: String,
        val screenEnabled: Boolean,
    )

    fun write(context: Context, s: AppState, today: LocalDate) {
        val pending = QuestEngine.pendingPenalties(s, today)
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("penaltyLock", Game.penaltyLockActive(s, today))
            .putString("penaltyTitle", pending.firstOrNull()?.title ?: "")
            .putBoolean("budgetLock", Game.budgetLockEnabled(s))
            .putInt("budgetMin", QuestEngine.screenBudget(s, today))
            .putStringSet("packages", s.screen.packages.toSet())
            .putString("day", today.iso())
            .putBoolean("screenEnabled", s.screen.enabled)
            .apply()
    }

    fun read(context: Context): Lock {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return Lock(
            penaltyLock = p.getBoolean("penaltyLock", false),
            penaltyTitle = p.getString("penaltyTitle", "") ?: "",
            budgetLock = p.getBoolean("budgetLock", false),
            budgetMin = p.getInt("budgetMin", Int.MAX_VALUE),
            packages = p.getStringSet("packages", null) ?: emptySet(),
            day = p.getString("day", "") ?: "",
            screenEnabled = p.getBoolean("screenEnabled", false),
        )
    }
}

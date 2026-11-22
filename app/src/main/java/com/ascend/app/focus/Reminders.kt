package com.ascend.app.focus

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ascend.app.R
import com.ascend.app.data.Repository
import com.ascend.app.domain.QuestEngine
import com.ascend.app.domain.TrainingPlan
import com.ascend.app.ui.MainActivity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Three nudges a day: morning plan, evening "quests left", bedtime.
 * Uses inexact alarms (no special permission) and reschedules itself every time one fires.
 */
object Reminders {
    const val CHANNEL = "ascend_reminders"
    private const val EXTRA_KIND = "kind"

    enum class Kind(val time: LocalTime, val requestCode: Int) {
        MORNING(LocalTime.of(8, 0), 101),
        EVENING(LocalTime.of(19, 0), 102),
        BEDTIME(LocalTime.of(22, 30), 103),
    }

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Quest reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun scheduleAll(context: Context) {
        Kind.entries.forEach { schedule(context, it) }
    }

    private fun schedule(context: Context, kind: Kind) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        var at = LocalDate.now().atTime(kind.time)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            context, kind.requestCode,
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ascend)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    /** Builds the message for [kind], or null when there is nothing worth saying. */
    fun message(context: Context, kind: Kind): Pair<String, String>? {
        Repository.init(context)
        Repository.refresh()
        val s = Repository.state.value
        if (!s.onboarded) return null
        val today = LocalDate.now()
        val quests = QuestEngine.questsFor(s, today)
        val open = quests.filter { q -> QuestEngine.record(s, q) == null && QuestEngine.progress(s, q) < 1.0 && !q.optional }
        val penalties = QuestEngine.pendingPenalties(s, today)
        return when (kind) {
            Kind.MORNING -> {
                val run = TrainingPlan.plannedRun(s, today)
                val gym = TrainingPlan.gymSession(s, today)
                val train = when {
                    run != null -> "${run.title}, ${run.km} km"
                    gym != null -> gym.name
                    else -> "no training, recovery day"
                }
                "⚔️ ${quests.size} quests today" to "Training: $train." +
                    (if (penalties.isNotEmpty()) " You still owe: ${penalties.first().title}." else "") +
                    (if (!com.ascend.app.domain.Rpg.checkedInToday(s, today)) " Check-in reward waiting: ${com.ascend.app.domain.Rpg.CHECK_IN_REWARDS[com.ascend.app.domain.Rpg.checkInIndex(s)]}." else "")
            }
            Kind.EVENING -> when {
                penalties.isNotEmpty() -> "🔒 Penalty still open" to "${penalties.first().title}. TikTok and YouTube stay locked until it's done."
                open.isEmpty() -> null
                else -> "⏳ ${open.size} quest(s) left today" to open.take(4).joinToString(" · ") { it.title } +
                    ". They count as missed at midnight."
            }
            Kind.BEDTIME -> "🌙 Bedtime soon" to
                "Phone down, lights out in 30 min for your ${s.schedule.sleepHours} h. Log it tomorrow morning."
        }
    }

    fun kindOf(intent: Intent): Kind? = intent.getStringExtra(EXTRA_KIND)?.let { n -> Kind.entries.firstOrNull { it.name == n } }

    private const val BUDGET_PREFS = "ascend_budget_warn"

    /** Warn once a day when 15 minutes of TikTok/YouTube budget are left. */
    fun maybeWarnBudget(context: Context, usedMin: Int, budgetMin: Int) {
        if (budgetMin <= 0 || usedMin < budgetMin - 15 || usedMin >= budgetMin) return
        val prefs = context.getSharedPreferences(BUDGET_PREFS, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString("day", "") == today) return
        prefs.edit().putString("day", today).apply()
        notify(context, 201, "⏱️ 15 min of TikTok/YouTube left", "You've used $usedMin of $budgetMin min today. Every minute you skip is XP.")
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.scheduleAll(context)
            return
        }
        val kind = Reminders.kindOf(intent) ?: return
        runCatching { Reminders.message(context, kind) }.getOrNull()?.let { (title, text) ->
            Reminders.notify(context, kind.requestCode, title, text)
        }
        Reminders.scheduleAll(context)
    }
}

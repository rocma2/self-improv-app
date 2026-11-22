package com.ascend.app.focus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ascend.app.R
import com.ascend.app.data.Repository
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Leveling
import com.ascend.app.domain.QuestEngine
import com.ascend.app.domain.QuestStatus
import com.ascend.app.ui.MainActivity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Home screen widget: rank, quests done today, the next one, and time left. Tapping opens the app. */
class QuestWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Repository.init(context)
        val views = build(context, Repository.state.value)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        fun updateAll(context: Context, state: AppState) {
            runCatching {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, QuestWidget::class.java))
                if (ids.isEmpty()) return
                val views = build(context, state)
                ids.forEach { manager.updateAppWidget(it, views) }
            }
        }

        private fun build(context: Context, s: AppState): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_quests)
            val today = LocalDate.now()
            val level = Leveling.level(s.totalXp)
            v.setTextViewText(R.id.widget_title, "RANK ${Leveling.rank(s.totalXp)} · LV $level · 🔥 ${s.momentum.streak}")
            if (!s.onboarded) {
                v.setTextViewText(R.id.widget_progress, "Ascend")
                v.setTextViewText(R.id.widget_next, "Open the app to start your path.")
                v.setTextViewText(R.id.widget_warn, "")
            } else {
                val quests = QuestEngine.questsFor(s, today).filter { !it.optional }
                val done = quests.count { QuestEngine.record(s, it)?.status == QuestStatus.DONE || QuestEngine.progress(s, it) >= 1.0 }
                val next = quests.firstOrNull { QuestEngine.record(s, it) == null && QuestEngine.progress(s, it) < 1.0 }
                val penalty = QuestEngine.pendingPenalties(s, today).firstOrNull()
                v.setTextViewText(R.id.widget_progress, if (done == quests.size && quests.isNotEmpty()) "✔ All ${quests.size} quests cleared" else "$done / ${quests.size} quests")
                v.setTextViewText(R.id.widget_next, when {
                    penalty != null -> "🔒 ${penalty.title}"
                    next != null -> "Next: ${next.title}"
                    else -> "Daily quest complete. Rest well."
                })
                val left = Duration.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay())
                v.setTextViewText(R.id.widget_warn, if (next != null) "⏳ ${left.toHours()} h ${left.toMinutes() % 60} min until midnight" else "")
            }
            val open = PendingIntent.getActivity(
                context, 7, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.widget_root, open)
            return v
        }
    }
}

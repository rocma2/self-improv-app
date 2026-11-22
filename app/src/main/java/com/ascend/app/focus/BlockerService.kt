package com.ascend.app.focus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.ascend.app.data.Repository
import com.ascend.app.domain.ScreenSettings
import java.time.LocalDate

/**
 * Sees which app comes to the front. If it's TikTok or YouTube and you owe a penalty quest,
 * or today's budget is used up, it sends you home and shows the lock screen.
 */
class BlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null

    private val tick = object : Runnable {
        override fun run() {
            current?.let { if (isTarget(it)) check() }
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Repository.init(this)
        handler.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Keyboards and the system bar pop up over apps; they don't change what you're watching.
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("keyboard")) return
        current = pkg
        if (isTarget(pkg)) check()
    }

    private fun isTarget(pkg: String): Boolean {
        val lock = LockPrefs.read(this)
        val targets = lock.packages.ifEmpty { ScreenSettings.DEFAULT_BLOCKED.toSet() }
        return pkg in targets
    }

    private fun check() {
        // A new day may have started since the app was last opened: judge it first.
        if (LockPrefs.read(this).day != LocalDate.now().toString()) runCatching { Repository.refresh() }
        val lock = LockPrefs.read(this)
        val reason = when {
            lock.penaltyLock -> BlockActivity.REASON_PENALTY
            lock.budgetLock && (ScreenTime.minutesOn(this, lock.packages.toList(), LocalDate.now()) ?: 0) >= lock.budgetMin -> BlockActivity.REASON_BUDGET
            else -> null
        } ?: return
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockActivity.EXTRA_REASON, reason)
                .putExtra(BlockActivity.EXTRA_DETAIL, if (reason == BlockActivity.REASON_PENALTY) lock.penaltyTitle else "${lock.budgetMin}")
        )
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}

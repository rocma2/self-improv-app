package com.ascend.app.focus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascend.app.domain.Fmt
import com.ascend.app.ui.AscendTheme
import com.ascend.app.ui.C
import com.ascend.app.ui.MainActivity

class BlockActivity : ComponentActivity() {
    companion object {
        const val EXTRA_REASON = "reason"
        const val EXTRA_DETAIL = "detail"
        const val REASON_PENALTY = "penalty"
        const val REASON_BUDGET = "budget"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    private fun render() {
        val reason = intent.getStringExtra(EXTRA_REASON) ?: REASON_PENALTY
        val detail = intent.getStringExtra(EXTRA_DETAIL) ?: ""
        setContent {
            AscendTheme {
                Column(
                    Modifier.fillMaxSize().background(C.bg).padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🔒", fontSize = 64.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (reason == REASON_PENALTY) "Locked until your penalty is done" else "Today's budget is used up",
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.text, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (reason == REASON_PENALTY) "$detail\n\nFinish it, tap Done in Ascend, and TikTok and YouTube open again."
                        else "You set ${Fmt.hours(detail.toIntOrNull() ?: 0)} for TikTok + YouTube today. It resets at midnight. Every minute you don't spend here is XP.",
                        color = C.muted, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    Button(onClick = {
                        startActivity(Intent(this@BlockActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open Ascend") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { goHome() }, modifier = Modifier.fillMaxWidth()) { Text("Go to home screen") }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}

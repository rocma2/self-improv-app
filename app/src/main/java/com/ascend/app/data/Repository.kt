package com.ascend.app.data

import android.content.Context
import com.ascend.app.domain.AppState
import com.ascend.app.domain.Game
import com.ascend.app.domain.GameEvent
import com.ascend.app.domain.QuestEngine
import com.ascend.app.focus.LockPrefs
import com.ascend.app.focus.ScreenTime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * Holds the whole app state in one JSON file on the phone. Nothing leaves the device
 * unless you export it yourself.
 */
object Repository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var appContext: Context
    private lateinit var file: File

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GameEvent> = _events

    @Volatile private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            appContext = context.applicationContext
            file = File(appContext.filesDir, "ascend_state.json")
            _state.value = read(file) ?: read(File(appContext.filesDir, "ascend_state.bak")) ?: AppState()
            loaded = true
        }
    }

    private fun read(f: File): AppState? = runCatching {
        if (!f.exists()) null else json.decodeFromString(AppState.serializer(), f.readText())
    }.getOrNull()

    fun today(): LocalDate = LocalDate.now()

    @Synchronized
    fun set(newState: AppState) {
        if (newState == _state.value) return
        _state.value = newState
        save(newState)
    }

    @Synchronized
    fun update(change: (AppState) -> Game.Result) {
        val r = change(_state.value)
        set(r.state)
        r.events.forEach { _events.tryEmit(it) }
    }

    @Synchronized
    fun edit(change: (AppState) -> AppState) {
        set(change(_state.value))
    }

    /** Judge past days and refresh lock info. Safe to call often. */
    fun refresh() {
        update { s ->
            Game.evaluate(s, today()) { d -> ScreenTime.minutesOn(appContext, s.screen.packages, d) }
        }
        LockPrefs.write(appContext, _state.value, today())
    }

    private fun save(s: AppState) {
        runCatching {
            val text = json.encodeToString(AppState.serializer(), s)
            val tmp = File(appContext.filesDir, "ascend_state.tmp")
            tmp.writeText(text)
            if (file.exists()) file.copyTo(File(appContext.filesDir, "ascend_state.bak"), overwrite = true)
            if (!tmp.renameTo(file)) { file.writeText(text); tmp.delete() }
        }
        LockPrefs.write(appContext, s, today())
    }

    fun exportJson(): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(AppState.serializer(), _state.value)

    fun importJson(text: String): Boolean {
        val s = runCatching { json.decodeFromString(AppState.serializer(), text) }.getOrNull() ?: return false
        set(s)
        return true
    }

    fun pendingPenaltyCount(): Int = QuestEngine.pendingPenalties(_state.value, today()).size
}

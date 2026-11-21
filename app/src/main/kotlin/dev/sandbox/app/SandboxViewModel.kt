package dev.sandbox.app

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sandbox.core.El
import dev.sandbox.core.Element
import dev.sandbox.core.Scenes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Single source of truth for the UI: tool state, engine settings, saved scenes and stats. */
class SandboxViewModel(app: Application) : AndroidViewModel(app) {

    val engine = SandboxEngine()
    private val store = SceneStore(app)
    private val prefs = app.getSharedPreferences("sandbox", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val density = app.resources.displayMetrics.density

    var selectedId by mutableIntStateOf(prefs.getInt(KEY_ELEMENT, El.SAND.id))
        private set
    var brushDp by mutableFloatStateOf(prefs.getFloat(KEY_BRUSH, 26f))
        private set
    var coverage by mutableIntStateOf(prefs.getInt(KEY_COVERAGE, 100))
        private set
    var tool by mutableStateOf(Tool.BRUSH)
        private set
    var thermal by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var quality by mutableIntStateOf(prefs.getInt(KEY_QUALITY, 1))
        private set
    var invertGravity by mutableStateOf(prefs.getBoolean(KEY_INVERT, false))
        private set
    var glowEnabled by mutableStateOf(prefs.getBoolean(KEY_GLOW, true))
        private set
    var haptics by mutableStateOf(prefs.getBoolean(KEY_HAPTICS, true))
        private set
    var stats by mutableStateOf(EngineStats())
        private set
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    val saves = mutableStateListOf<SceneInfo>()

    val selected: Element get() = El.ALL[selectedId]
    val brushPx: Float get() = brushDp * density
    val targetCells: Int
        get() = when (quality) {
            0 -> 60_000
            2 -> 220_000
            else -> 120_000
        }

    init {
        engine.onStats = { s -> main.post { stats = s } }
        engine.thermal = thermal
        engine.glow = glowEnabled
        engine.paused = paused
        engine.speed = speed
        engine.world.gravityUp = invertGravity
        refreshSaves()
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            engine.pendingScene = 1
        }
    }

    fun select(elementId: Int) {
        if (elementId in El.ALL.indices) {
            selectedId = elementId
            prefs.edit().putInt(KEY_ELEMENT, elementId).apply()
        }
    }

    fun selectByKey(key: String) {
        El.maybeByKey(key)?.let { select(it.id) }
    }

    fun setTool(value: Tool) { tool = value }

    fun setBrushDp(value: Float) {
        brushDp = value.coerceIn(4f, 120f)
        prefs.edit().putFloat(KEY_BRUSH, brushDp).apply()
    }

    fun setCoverage(value: Int) {
        coverage = value.coerceIn(5, 100)
        prefs.edit().putInt(KEY_COVERAGE, coverage).apply()
    }

    fun togglePause() {
        paused = !paused
        engine.paused = paused
    }

    fun stepOnce() {
        paused = true
        engine.paused = true
        engine.submit(SimCommand.StepOnce)
    }

    fun setSpeed(value: Float) {
        speed = value
        engine.speed = value
    }

    fun toggleThermal() {
        thermal = !thermal
        engine.thermal = thermal
    }

    fun setQuality(value: Int) {
        quality = value.coerceIn(0, 2)
        prefs.edit().putInt(KEY_QUALITY, quality).apply()
    }

    fun setInvertGravity(value: Boolean) {
        invertGravity = value
        prefs.edit().putBoolean(KEY_INVERT, value).apply()
        engine.gravityUp = value
    }

    fun setGlow(value: Boolean) {
        glowEnabled = value
        engine.glow = value
        prefs.edit().putBoolean(KEY_GLOW, value).apply()
    }

    fun setHaptics(value: Boolean) {
        haptics = value
        prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
    }

    fun clearAll() = engine.submit(SimCommand.Clear)

    fun applyScene(index: Int) {
        engine.submit(SimCommand.LoadScene(index))
        paused = false
        engine.paused = false
    }

    fun applyWorld(world: dev.sandbox.core.World) {
        engine.submit(SimCommand.ReplaceWorld(world))
        paused = false
        engine.paused = false
    }

    fun exportScreenshot(view: SandView) {
        val bitmap = view.exportBitmap()
        val app = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { ScreenshotExporter.save(app, bitmap) }
            message = app.getString(if (ok) R.string.toast_exported else R.string.toast_export_failed)
        }
    }

   // ------------------------------------------------------------------- storage --

    fun refreshSaves() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { store.list() }
            saves.clear()
            saves.addAll(list)
        }
    }

    fun saveCurrent(name: String) {
        val world = engine.world
        val app = getApplication<Application>()
        viewModelScope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                runCatching { store.save(name, world) }.isSuccess
            }
            busy = false
            if (ok) {
                message = app.getString(R.string.toast_saved)
                refreshSaves()
            }
        }
    }

    fun loadScene(info: SceneInfo) {
        viewModelScope.launch {
            val world = withContext(Dispatchers.IO) {
                runCatching { store.load(info) }.getOrNull()
            }
            if (world != null) applyWorld(world)
        }
    }

    fun deleteScene(info: SceneInfo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { store.delete(info) } }
            refreshSaves()
        }
    }

    fun sceneNames(): List<String> = Scenes.NAMES

    private companion object {
        const val KEY_ELEMENT = "element"
        const val KEY_BRUSH = "brush"
        const val KEY_COVERAGE = "coverage"
        const val KEY_QUALITY = "quality"
        const val KEY_INVERT = "invert"
        const val KEY_GLOW = "glow"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SEEDED = "seeded"
    }
}

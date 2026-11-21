package dev.sandbox.app

import dev.sandbox.core.Scenes
import dev.sandbox.core.World
import dev.sandbox.core.paintPixels
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Work items produced by the UI thread and executed on the simulation thread. */
sealed interface SimCommand {
    data class Paint(val x: Int, val y: Int, val radius: Int, val element: Int, val coverage: Int) : SimCommand
    data class Line(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val radius: Int, val element: Int, val coverage: Int) : SimCommand
    data class Rect(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val element: Int, val thickness: Int) : SimCommand
    data class LoadScene(val index: Int) : SimCommand
    data class ReplaceWorld(val world: World) : SimCommand
    data object Clear : SimCommand
    data class Resize(val widthPx: Int, val heightPx: Int, val targetCells: Int) : SimCommand
    data object StepOnce : SimCommand
}

data class EngineStats(
    val fps: Int = 0,
    val particles: Int = 0,
    val cells: Int = 0,
    val stepMs: Float = 0f,
)

/**
 * Owns the [World] and runs it on a dedicated thread.
 *
 * The UI thread only talks to the simulation through [submit] (a lock free queue) and reads the
 * rendered pixels under [renderLock] while drawing. Materials and temperatures are therefore only
 * ever mutated by one thread.
 */
class SandboxEngine {

    val renderLock = Any()

    @Volatile
    var world: World = World(72, 120)
        private set

    @Volatile var paused = false
    @Volatile var speed = 1f
    @Volatile var thermal = false
    @Volatile var glow = true

    /** Inverted gravity flag, mirrored into the world on every (re)build. */
    @Volatile var gravityUp = false

    /** Scene applied once the grid has been sized for the first time. */
    var pendingScene: Int = -1

    @Volatile var onFrame: (() -> Unit)? = null
    @Volatile var onStats: ((EngineStats) -> Unit)? = null

    private val queue = ConcurrentLinkedQueue<SimCommand>()
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    @Volatile private var dirty = true

    fun start() {
        if (!running.compareAndSet(false, true)) return
        thread = Thread({ loop() }, "sandbox-sim").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    fun submit(command: SimCommand) {
        queue.offer(command)
        dirty = true
    }

    /** Reads a material id from the grid; used by the eyedropper tool. */
    fun sample(x: Int, y: Int): Int = world.at(x, y)

    private fun loop() {
        var last = System.nanoTime()
        var accumulator = 0.0
        var frames = 0
        var statStart = System.nanoTime()
        var stepNanos = 0L
        var stepCount = 0

        while (running.get()) {
            val frameStart = System.nanoTime()
            var dt = (frameStart - last) / 1e9
            last = frameStart
            if (dt > 0.2) dt = 0.2
            accumulator += dt * 60.0 * speed
            var didStep = false
            val wasDirty = dirty

            synchronized(renderLock) {
                drain()
                if (!paused) {
                    var n = 0
                    while (accumulator >= 1.0 && n < 8) {
                        val t0 = System.nanoTime()
                        world.step()
                        stepNanos += System.nanoTime() - t0
                        stepCount++
                        accumulator -= 1.0
                        n++
                        didStep = true
                    }
                } else {
                    accumulator = 0.0
                }
                if (didStep || dirty) {
                    world.paintPixels(thermal, glow)
                    dirty = false
                }
            }

            frames++
            if (didStep || wasDirty) onFrame?.invoke()

            val now = System.nanoTime()
            val window = now - statStart
            if (window > 500_000_000L) {
                val fps = (frames * 1_000_000_000.0 / window).toInt()
                val particles = world.particleCount()
                val ms = if (stepCount > 0) stepNanos / 1_000_000.0 / stepCount else 0.0
                onStats?.invoke(EngineStats(fps, particles, world.n, ms.toFloat()))
                frames = 0
                statStart = now
                stepNanos = 0
                stepCount = 0
            }

            val spent = System.nanoTime() - frameStart
            val sleep = 16_666_666L - spent
            if (sleep > 1_500_000L) {
                try {
                    Thread.sleep(sleep / 1_000_000L, (sleep % 1_000_000L).toInt())
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun drain() {
        while (true) {
            val cmd = queue.poll() ?: return
            dirty = true
            when (cmd) {
                is SimCommand.Paint ->
                    world.paint(cmd.x, cmd.y, cmd.radius, cmd.element, cmd.coverage)

                is SimCommand.Line ->
                    world.paintLine(cmd.x0, cmd.y0, cmd.x1, cmd.y1, cmd.radius, cmd.element, cmd.coverage)

                is SimCommand.Rect ->
                    world.paintRect(cmd.x0, cmd.y0, cmd.x1, cmd.y1, cmd.element, false, cmd.thickness)

                is SimCommand.LoadScene -> Scenes.apply(world, cmd.index)
                is SimCommand.ReplaceWorld -> {
                    cmd.world.gravityUp = gravityUp
                    world = cmd.world
                }
                SimCommand.Clear -> world.clear()
                is SimCommand.Resize -> resizeLocked(cmd.widthPx, cmd.heightPx, cmd.targetCells)
                SimCommand.StepOnce -> if (paused) world.step()
            }
        }
    }

    /** Rebuilds the grid for a new viewport, keeping the scene anchored to the bottom. */
    private fun resizeLocked(widthPx: Int, heightPx: Int, targetCells: Int) {
        if (widthPx <= 0 || heightPx <= 0 || targetCells <= 0) return
        val cell = max(2, sqrt(widthPx.toDouble() * heightPx / targetCells).toInt())
        val newW = max(24, widthPx / cell)
        val newH = max(24, heightPx / cell)
        val old = world
        if (old.width == newW && old.height == newH) return

        val fresh = World(newW, newH)
        val copyW = min(old.width, newW)
        val copyH = min(old.height, newH)
        val srcX = (old.width - copyW) / 2
        val srcY = old.height - copyH
        val dstX = (newW - copyW) / 2
        val dstY = newH - copyH
        for (y in 0 until copyH) {
            val si = (srcY + y) * old.width + srcX
            val di = (dstY + y) * newW + dstX
            System.arraycopy(old.mat, si, fresh.mat, di, copyW)
            System.arraycopy(old.temp, si, fresh.temp, di, copyW)
            System.arraycopy(old.life, si, fresh.life, di, copyW)
            System.arraycopy(old.flags, si, fresh.flags, di, copyW)
            System.arraycopy(old.tone, si, fresh.tone, di, copyW)
        }
        fresh.ambient = old.ambient
        fresh.gravity = old.gravity
        fresh.gravityUp = gravityUp
        val scene = pendingScene
        if (scene >= 0) {
            pendingScene = -1
            Scenes.apply(fresh, scene)
        }
        world = fresh
    }
}

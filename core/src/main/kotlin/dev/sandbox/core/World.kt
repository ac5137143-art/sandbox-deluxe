package dev.sandbox.core

/** Per-cell bit flags. */
object Flag {
    /** Cell is on fire: [World.life] counts down as fuel is consumed. */
    const val BURNING = 1

    /** Cell carries electric charge. */
    const val CHARGED = 2

    /** Cell was already updated in this motion pass. */
    const val MOVED = 4
}

/**
 * The simulation grid: material ids, temperature field, per-cell counters and the render buffer.
 *
 * All coordinates are cell coordinates, `(0,0)` is the top-left corner.
 */
class World(
    val width: Int,
    val height: Int,
    seed: Long = 0x2545F4914F6CDD1DL,
) {
    val n: Int = width * height

    /** Material id per cell, 0 = empty. */
    val mat = IntArray(n)

    /** Temperature in \u00b0C. */
    val temp = FloatArray(n) { AMBIENT }

    /** Generic per-cell counter: fuel left, spark charge, growth energy, decay timer. */
    val life = IntArray(n)

    /** See [Flag]. */
    val flags = ByteArray(n)

    /** Per-cell colour jitter, keeps large uniform areas from looking flat. */
    val tone = ByteArray(n)

    /** ARGB output buffer filled by [dev.sandbox.core.paintPixels]. */
    val pixels = IntArray(n)

    var frame = 0
        private set

    private var rngState: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    internal val stamp = IntArray(n)
    internal var stampId = 0
    internal val thermalDelta = FloatArray(n)
    internal var explosionBudget = 0

    var ambient: Float = AMBIENT

    /** Movement speed multiplier, 0 = frozen solids/liquids, 1 = normal, 2 = fast. */
    var gravity: Float = 1f

    /** Inverted gravity: heavy things float, gases sink. */
    var gravityUp: Boolean = false

    /** Blow away cells that leave the grid instead of bouncing off invisible walls. */
    var openEdges: Boolean = false

    // ------------------------------------------------------------------ basics --
    fun index(x: Int, y: Int): Int = y * width + x

    fun inBounds(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < width && y < height

    fun at(x: Int, y: Int): Int = if (inBounds(x, y)) mat[y * width + x] else 0

    operator fun get(i: Int): Int = mat[i]

    // --------------------------------------------------------------------- rng --
    fun nextLong(): Long {
        var x = rngState
        x = x xor (x shl 13)
        x = x xor (x ushr 7)
        x = x xor (x shl 17)
        rngState = x
        return x
    }

    /** Uniform value in `0 until bound`. */
    fun randInt(bound: Int): Int = ((nextLong() ushr 33).toInt() and 0x7FFFFFFF) % bound

    /** Returns true with probability `k / outOf`. */
    fun chance(k: Int, outOf: Int): Boolean = randInt(outOf) < k

    fun chance(k: Float): Boolean = randInt(1000) < (k * 1000f).toInt()

    fun randFloat(): Float = ((nextLong() ushr 40).toInt() and 0xFFFFFF) / 16777216f

    // ------------------------------------------------------------------- cells --
    /** Puts [id] into cell [i], resetting counters and applying the material spawn temperature. */
    fun place(i: Int, id: Int) {
        mat[i] = id
        flags[i] = 0
        if (id == 0) {
            temp[i] = ambient
            life[i] = 0
            return
        }
        val e = El.ALL[id]
        tone[i] = ((nextLong() ushr 5) and 0x1F).toByte()
        life[i] = 0
        if (e.phase == Phase.ENERGY) life[i] = e.decayTicks
        val st = e.spawnTemp
        if (!st.isNaN()) temp[i] = st
    }

    fun place(x: Int, y: Int, id: Int) {
        if (inBounds(x, y)) place(y * width + x, id)
    }

    /** Paints a circular brush blob; [coverage] is the percentage of cells actually filled. */
    fun paint(cx: Int, cy: Int, radius: Int, id: Int, coverage: Int = 100) {
        val r = radius.coerceAtLeast(0)
        val r2 = r * r
        val target = El.ALL[id]
        val x0 = (cx - r).coerceAtLeast(0)
        val x1 = (cx + r).coerceAtMost(width - 1)
        val y0 = (cy - r).coerceAtLeast(0)
        val y1 = (cy + r).coerceAtMost(height - 1)
        for (y in y0..y1) {
            val dy = y - cy
            for (x in x0..x1) {
                val dx = x - cx
                if (dx * dx + dy * dy > r2) continue
                if (coverage < 100 && randInt(100) >= coverage) continue
                val i = y * width + x
                val cur = mat[i]
                if (cur != 0 && cur != id) {
                    // Indestructible materials survive everything but their own brush.
                    if (El.ALL[cur].indestructible && !target.indestructible) continue
                }
                place(i, id)
            }
        }
    }

    fun paintLine(x0: Int, y0: Int, x1: Int, y1: Int, radius: Int, id: Int, coverage: Int = 100) {
        val steps = maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)).coerceAtLeast(1)
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            paint(
                (x0 + (x1 - x0) * t).toInt(),
                (y0 + (y1 - y0) * t).toInt(),
                radius, id, coverage,
            )
        }
    }

    fun paintRect(x0: Int, y0: Int, x1: Int, y1: Int, id: Int, filled: Boolean, thickness: Int = 1) {
        val ax = minOf(x0, x1); val bx = maxOf(x0, x1)
        val ay = minOf(y0, y1); val by = maxOf(y0, y1)
        for (y in ay..by) {
            for (x in ax..bx) {
                if (!filled) {
                    val edge = x < ax + thickness || x > bx - thickness ||
                        y < ay + thickness || y > by - thickness
                    if (!edge) continue
                }
                val i = y * width + x
                val cur = mat[i]
                if (cur != 0 && cur != id && El.ALL[cur].indestructible) continue
                place(i, id)
            }
        }
    }

    /** Fills every cell with [id]. */
    fun fillAll(id: Int) {
        for (i in 0 until n) place(i, id)
    }

    fun clear() {
        for (i in 0 until n) {
            mat[i] = 0
            flags[i] = 0
            life[i] = 0
            temp[i] = ambient
        }
    }

    // -------------------------------------------------------------------- state --
    fun countOf(id: Int): Int {
        var c = 0
        for (i in 0 until n) if (mat[i] == id) c++
        return c
    }

    /** Number of non-empty cells. */
    fun particleCount(): Int {
        var c = 0
        for (i in 0 until n) if (mat[i] != 0) c++
        return c
    }

    fun counts(): IntArray {
        val out = IntArray(El.ALL.size)
        for (i in 0 until n) out[mat[i]]++
        return out
    }

    // --------------------------------------------------------------------- step --
    /** Advances the simulation by one tick. */
    fun step() {
        frame++
        gravityPass()
        thermalPass()
        reactionPass()
        motionPass()
    }

    private fun gravityPass() {
        gravityFrozen = gravity <= 0f
        gravitySkipPercent = if (gravity >= 1f) 0 else ((1f - gravity) * 100f).toInt().coerceIn(0, 100)
    }

    internal var gravityFrozen = false
    internal var gravitySkipPercent = 0

    internal fun moveAllowed(): Boolean =
        !gravityFrozen && (gravitySkipPercent == 0 || randInt(100) >= gravitySkipPercent)

    companion object {
        /** Builds a world sized for a viewport, keeping the cell count inside a sane budget. */
        fun forViewport(widthPx: Int, heightPx: Int, cellPx: Int, maxCells: Int = 260_000): World {
            var cell = cellPx.coerceAtLeast(1)
            while ((widthPx / cell) * (heightPx / cell) > maxCells) cell++
            val w = (widthPx / cell).coerceAtLeast(16)
            val h = (heightPx / cell).coerceAtLeast(16)
            return World(w, h)
        }
    }
}

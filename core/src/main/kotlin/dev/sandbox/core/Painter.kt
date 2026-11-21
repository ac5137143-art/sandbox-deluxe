package dev.sandbox.core

/** Colour helpers and the infrared palette used by the "thermal camera" view. */
object Painter {
    private const val LUT = 1024
    private const val T_MIN = -250f
    private const val T_MAX = 2500f

    /** Colour stops of the infrared palette: temperature, ARGB. */
    private val stops = intArrayOf(
        -250, 0xFF14143A.toInt(),
        -60, 0xFF23266E.toInt(),
        0, 0xFF2F6FB4.toInt(),
        25, 0xFF1FA07A.toInt(),
        80, 0xFF8CC63F.toInt(),
        180, 0xFFE8C63A.toInt(),
        400, 0xFFF07A20.toInt(),
        900, 0xFFE02A18.toInt(),
        1600, 0xFFFF7A60.toInt(),
        2500, 0xFFFFFFFF.toInt(),
    )

    private val lut = IntArray(LUT) { i ->
        val t = T_MIN + (T_MAX - T_MIN) * i / (LUT - 1)
        sampleInfrared(t)
    }

    private fun sampleInfrared(t: Float): Int {
        var k = 0
        while (k < stops.size - 2 && t > stops[k + 2]) k += 2
        val t0 = stops[k].toFloat()
        val c0 = stops[k + 1]
        val t1 = stops[k + 2].toFloat()
        val c1 = stops[k + 3]
        val f = if (t1 == t0) 0f else ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
        return mix(c0, c1, f)
    }

    /** Infrared colour of a temperature in \u00b0C. */
    fun thermal(t: Float): Int {
        val clamped = when {
            t <= T_MIN -> 0
            t >= T_MAX -> LUT - 1
            else -> ((t - T_MIN) / (T_MAX - T_MIN) * (LUT - 1)).toInt()
        }
        return lut[clamped]
    }

    /** Per-cell brightness variation, optionally animated (fire flicker). */
    fun jitter(color: Int, tone: Byte, amount: Int, animFrame: Int): Int {
        if (amount <= 0) return color
        var t = tone.toInt() and 0x1F
        if (animFrame >= 0) t = (t + (animFrame shr 2)) and 0x1F
        val f = 256 + (t - 16) * amount
        val r = (((color shr 16) and 0xFF) * f) shr 8
        val g = (((color shr 8) and 0xFF) * f) shr 8
        val b = ((color and 0xFF) * f) shr 8
        return (color and 0xFF000000.toInt()) or
            (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    /** Glow of a hot, non-emissive material (red hot steel, melted stone...). */
    fun hot(color: Int, t: Float): Int {
        val k = ((t - 250f) / 900f).coerceIn(0f, 1f)
        if (k <= 0f) return color
        val r = ((color shr 16) and 0xFF) * (1f - k) + 255f * k
        val g = ((color shr 8) and 0xFF) * (1f - k) + 140f * k
        val b = (color and 0xFF) * (1f - k) + 25f * k
        return (color and 0xFF000000.toInt()) or
            (r.toInt().coerceIn(0, 255) shl 16) or
            (g.toInt().coerceIn(0, 255) shl 8) or
            b.toInt().coerceIn(0, 255)
    }

    fun mix(c0: Int, c1: Int, f: Float): Int {
        val a = (c0 ushr 24) + (((c1 ushr 24) - (c0 ushr 24)) * f).toInt()
        val r = ((c0 shr 16) and 0xFF) + ((((c1 shr 16) and 0xFF) - ((c0 shr 16) and 0xFF)) * f).toInt()
        val g = ((c0 shr 8) and 0xFF) + ((((c1 shr 8) and 0xFF) - ((c0 shr 8) and 0xFF)) * f).toInt()
        val b = (c0 and 0xFF) + (((c1 and 0xFF) - (c0 and 0xFF)) * f).toInt()
        return (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or
            (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    /** Vertical background gradient used behind transparent cells. */
    fun background(y: Int, height: Int): Int {
        val f = if (height <= 1) 0f else y.toFloat() / (height - 1)
        val top = 0xFF10131C.toInt()
        val bottom = 0xFF05070C.toInt()
        return mix(top, bottom, f)
    }
}

/**
 * Fills [World.pixels] with ARGB values.
 *
 * @param thermal infrared view: every cell is coloured by its temperature
 * @param glow draw red hot non-emissive materials
 */
fun World.paintPixels(thermal: Boolean = false, glow: Boolean = true) {
    for (i in 0 until n) {
        val m = mat[i]
        val t = temp[i]
        if (m == 0) {
            pixels[i] = if (thermal) Painter.thermal(t) else 0
            continue
        }
        val e = El.ALL[m]
        if (thermal) {
            pixels[i] = Painter.thermal(t)
            continue
        }
        var c = e.color
        if (e.jitter > 0) {
            c = Painter.jitter(c, tone[i], e.jitter, if (e.glow >= 200) frame else -1)
        }
        if (glow && e.glow < 200 && t > 250f) c = Painter.hot(c, t)
        if (e.alpha < 255) c = (c and 0x00FFFFFF) or (e.alpha shl 24)
        pixels[i] = c
    }
}

/** Composites the cell buffer over the background gradient into [out] (width * height ARGB). */
fun World.composeBackground(out: IntArray) {
    for (y in 0 until height) {
        val bg = Painter.background(y, height)
        val row = y * width
        for (x in 0 until width) {
            val i = row + x
            val c = pixels[i]
            val a = c ushr 24
            out[i] = when (a) {
                255 -> c
                0 -> bg
                else -> Painter.mix(bg, c, a / 255f)
            }
        }
    }
}

package dev.sandbox.core

internal const val MIN_T = -273f
internal const val MAX_T = 6000f

/** Conductivity/heat capacity of the void (air). */
private const val AIR_COND = 0.14f
private const val AIR_CAP = 0.3f

/**
 * Pairwise heat exchange factors. Filling one cell from another is a two-step process:
 *
 *   flux      = (Ta - Tb) * k,  k = min(kA, kB) * RATE
 *   dTa       = -flux / capA
 *   dTb       = +flux / capB
 *
 * [RATE] keeps the explicit scheme inside its stability limit: every cell exchanges heat with four
 * neighbours, and the sum of the four coefficients must stay below 0.5, otherwise a hot spot starts
 * to oscillate and amplifies itself instead of cooling down. The factors are precomputed per material
 * pair so the inner loop costs two multiplications.
 */
internal object Conduct {
    private const val RATE = 0.14f

    private var factorA = FloatArray(0)
    private var factorB = FloatArray(0)

    fun ensure() {
        if (factorA.isNotEmpty()) return
        val c = El.ALL.size
        val a = FloatArray(c * c)
        val b = FloatArray(c * c)
        for (i in 0 until c) {
            val ki = condOf(i)
            val ci = capOf(i)
            for (j in 0 until c) {
                val kj = condOf(j)
                val cj = capOf(j)
                val k = minOf(ki, kj) * RATE
                val sum = ci + cj
                a[i * c + j] = k * cj / sum
                b[i * c + j] = k * ci / sum
            }
        }
        factorA = a
        factorB = b
    }

    fun fa(m1: Int, m2: Int): Float {
        ensure()
        return factorA[m1 * El.ALL.size + m2]
    }

    fun fb(m1: Int, m2: Int): Float {
        ensure()
        return factorB[m1 * El.ALL.size + m2]
    }

    fun condOf(m: Int): Float = if (m == 0) AIR_COND else El.ALL[m].conductivity

    fun capOf(m: Int): Float = if (m == 0) AIR_CAP else El.ALL[m].heatCapacity
}

/**
 * Heat diffusion pass: solves the explicit heat equation on the grid, applies heat sources and
 * relaxes open air towards the world temperature (crude convection).
 */
internal fun World.thermalPass() {
    Conduct.ensure()
    val delta = thermalDelta
    java.util.Arrays.fill(delta, 0f)

    // 1. heat sources and ambient exchange
    for (i in 0 until n) {
        val m = mat[i]
        if (m == 0) {
            temp[i] += (ambient - temp[i]) * 0.02f
            continue
        }
        val e = El.ALL[m]
        val src = e.heatSource
        if (!src.isNaN()) {
            temp[i] = src
        } else if (e.phase == Phase.GAS || e.phase == Phase.ENERGY) {
            temp[i] += (ambient - temp[i]) * 0.01f
        }
    }

    // 2. conduction to the right and down neighbours (every pair is visited once)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            val i = row + x
            val m1 = mat[i]
            val t1 = temp[i]
            if (x + 1 < width) {
                val j = i + 1
                val m2 = mat[j]
                val diff = t1 - temp[j]
                if (diff != 0f) {
                    delta[i] -= diff * Conduct.fa(m1, m2)
                    delta[j] += diff * Conduct.fb(m1, m2)
                }
            }
            if (y + 1 < height) {
                val j = i + width
                val m2 = mat[j]
                val diff = t1 - temp[j]
                if (diff != 0f) {
                    delta[i] -= diff * Conduct.fa(m1, m2)
                    delta[j] += diff * Conduct.fb(m1, m2)
                }
            }
        }
    }

    // 3. apply
    for (i in 0 until n) {
        val t = temp[i] + delta[i]
        temp[i] = when {
            t < MIN_T -> MIN_T
            t > MAX_T -> MAX_T
            else -> t
        }
    }
}

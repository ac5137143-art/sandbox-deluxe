package dev.sandbox.core

/**
 * Chemistry and material behaviour pass.
 *
 * Order inside one tick: phase transitions (melting, boiling, condensation, decay), combustion,
 * per-material behaviour (sparks, cloners, plants, acid...) and finally pair reactions between
 * neighbouring cells.
 */
internal fun World.reactionPass() {
    explosionBudget = 24
    var i = 0
    while (i < n) {
        val m = mat[i]
        if (m != 0) reactCell(i, m)
        i++
    }
}

private fun World.reactCell(i: Int, m: Int) {
    val e = El.ALL[m]
    val x = i % width
    val y = i / width
    val t = temp[i]

    if (phaseChange(i, e, t)) return
    if (combustion(i, x, y, e, t)) return
    if (decay(i, e)) return
    if (behaviour(i, x, y, m, e)) return
    chemistry(i, x, y, m)
}

// -------------------------------------------------------------- phase changes --

/** Probabilistic gate: a big temperature overshoot converts immediately, a small one gradually. */
private fun World.overThreshold(t: Float, limit: Float): Boolean {
    val over = t - limit
    if (over <= 0f) return false
    if (over >= 20f) return true
    return randInt(8) < (1 + (over * 0.35f).toInt())
}

private fun World.phaseChange(i: Int, e: Element, t: Float): Boolean {
    if (t >= e.meltPoint && overThreshold(t, e.meltPoint)) {
        place(i, e.meltInto)
        temp[i] = e.meltPoint
        return true
    }
    if (t >= e.boilPoint && overThreshold(t, e.boilPoint)) {
        place(i, e.boilInto)
        temp[i] = e.boilPoint + 5f
        return true
    }
    if (t <= e.freezePoint && overThreshold(e.freezePoint, t)) {
        place(i, e.freezeInto)
        temp[i] = e.freezePoint
        return true
    }
    return false
}

/** Gases, smoke and energy fade away after their lifetime. */
private fun World.decay(i: Int, e: Element): Boolean {
    if (e.decayTicks <= 0) return false
    if (life[i] <= 0) life[i] = e.decayTicks
    life[i]--
    if (life[i] > 0) return false
    place(i, e.decayInto)
    return true
}

// ---------------------------------------------------------------- combustion --

private fun World.combustion(i: Int, x: Int, y: Int, e: Element, t: Float): Boolean {
    if ((flags[i].toInt() and Flag.BURNING) != 0) {
        life[i]--
        if (life[i] <= 0) {
            place(i, e.burnInto)
            if (chance(1, 4)) spawnNear(x, y, El.SMOKE.id, 0.7f)
            return true
        }
        temp[i] = maxOf(t, e.flameTemp * 0.55f)
        if (chance(1, 9)) spawnNear(x, y, El.FIRE.id, 0.5f)
        if (chance(1, 24)) spawnNear(x, y, El.SMOKE.id, 0.6f)
        // a burning block keeps setting its neighbours alight
        if (chance(1, 3)) {
            eachNeighbor(x, y) { j ->
                val nm = mat[j]
                if (nm != 0) {
                    val ne = El.ALL[nm]
                    if (ne.flammable && ne.ignition <= 700f) ignite(j, ne)
                }
                false
            }
        }
        return false
    }
    if (e.flammable && t >= e.ignition && overThreshold(t, e.ignition)) {
        if (e.explosive > 0f) {
            detonate(x, y, e)
            return true
        }
        flags[i] = (flags[i].toInt() or Flag.BURNING).toByte()
        life[i] = e.burnTicks
        temp[i] = maxOf(t, e.ignition)
    }
    return false
}

// ------------------------------------------------------------------ behaviour --

private fun World.behaviour(i: Int, x: Int, y: Int, m: Int, e: Element): Boolean {
    if (m == El.FIRE.id) return fireBehaviour(i, x, y)
    if (m == El.SPARK.id) return sparkBehaviour(i, x, y)
    if (m == El.LIGHTNING.id) {
        if (life[i] <= 2 && explosionBudget > 0) detonate(x, y, e)
        return false
    }
    if (m == El.CLONER.id) { cloner(i, x, y); return false }
    if (m == El.VOID.id) { annihilate(i, x, y); return false }
    if (m == El.WATER_TAP.id) { tap(i, x, y, El.WATER.id, 2); return false }
    if (m == El.LAVA_VENT.id) { tap(i, x, y, El.LAVA.id, 4); return false }
    if (m == El.BATTERY.id) { battery(i, x, y); return false }
    if (m == El.ACID.id) { dissolve(i, x, y); return false }
    if (m == El.PLANT.id) { grow(i, x, y); return false }
    if (m == El.SEED.id) { germinate(i, x, y); return false }
    if (m == El.FUNGUS.id) { spreadFungus(i, x, y); return false }
    if (m == El.VIRUS.id) { infect(i, x, y); return false }
    if (m == El.CHLORINE.id) { poison(i, x, y); return false }
    return false
}

private fun World.fireBehaviour(i: Int, x: Int, y: Int): Boolean {
    if (temp[i] < 700f) temp[i] = 700f
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if (nm != 0) {
            val ne = El.ALL[nm]
            if (ne.flammable && ne.ignition <= 700f) {
                ignite(j, ne)
            } else if (ne.condenses && temp[j] < ne.freezePoint) {
                // flames heat up cold neighbours a bit faster than pure conduction would
                temp[j] += 40f
            }
        }
        false
    }
    return false
}

/** Sets a neighbour on fire, or detonates it when the material is an explosive. */
private fun World.ignite(j: Int, e: Element) {
    if ((flags[j].toInt() and Flag.BURNING) != 0) return
    if (e.explosive > 0f) {
        detonate(j % width, j / width, e)
        return
    }
    flags[j] = (flags[j].toInt() or Flag.BURNING).toByte()
    life[j] = e.burnTicks
    temp[j] = maxOf(temp[j], e.ignition)
}

private fun World.sparkBehaviour(i: Int, x: Int, y: Int): Boolean {
    val next = life[i] - 1
    if (next <= 0) return false
    if (!chance(3, 4)) return false
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if (nm == 0) return@eachNeighbor false
        val ne = El.ALL[nm]
        if (ne.conductive) {
            place(j, El.SPARK.id)
            life[j] = next
            temp[j] = maxOf(temp[j], 300f)
        } else if (ne.flammable && ne.ignition <= 400f && chance(1, 2)) {
            ignite(j, ne)
        } else if (ne.phase == Phase.LIFE && chance(1, 6)) {
            place(j, El.ASH.id)
        } else if (ne.phase == Phase.GAS && nm == El.HYDROGEN.id && chance(1, 3)) {
            detonate(x, y, El.ALL[El.HYDROGEN.id])
            return@eachNeighbor true
        }
        false
    }
    return false
}

private fun World.cloner(i: Int, x: Int, y: Int) {
    if (!chance(1, 3)) return
    var source = 0
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if (nm != 0 && nm != El.CLONER.id) { source = nm; true } else false
    }
    if (source == 0) return
    eachNeighbor(x, y) { j ->
        if (mat[j] == 0 && chance(1, 2)) place(j, source)
        false
    }
}

private fun World.annihilate(i: Int, x: Int, y: Int) {
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if (nm != 0 && !El.ALL[nm].indestructible) place(j, 0)
        false
    }
}

private fun World.tap(i: Int, x: Int, y: Int, id: Int, every: Int) {
    if (!chance(1, every)) return
    val uy = y - if (gravityUp) -1 else 1
    if (uy in 0 until height) {
        val j = uy * width + x
        if (mat[j] == 0) {
            place(j, id)
            return
        }
    }
    eachNeighbor(x, y) { j ->
        if (mat[j] == 0) { place(j, id); true } else false
    }
}

private fun World.battery(i: Int, x: Int, y: Int) {
    if (frame % 24 != 0) return
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if (nm != 0 && El.ALL[nm].conductive && mat[j] != El.SPARK.id) {
            place(j, El.SPARK.id)
            life[j] = 18
            true
        } else false
    }
}

private fun World.dissolve(i: Int, x: Int, y: Int) {
    if (!chance(1, 4)) return
    var eaten = 0
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if (nm == 0 || nm == El.ACID.id) return@eachNeighbor false
        val ne = El.ALL[nm]
        if (ne.indestructible || ne.acidResist >= 1f) return@eachNeighbor false
        if (randFloat() < ne.acidResist) return@eachNeighbor false
        val into = if (chance(1, 3)) El.SMOKE.id else 0
        val wasMetal = ne.conductive
        place(j, into)
        eaten++
        if (wasMetal && chance(1, 2)) {
            // metal + acid releases hydrogen
            eachNeighbor(x, y) { k -> if (mat[k] == 0) { place(k, El.HYDROGEN.id); true } else false }
        }
        false
    }
    if (eaten > 0 && chance(1, 7)) place(i, 0)
}

private fun World.grow(i: Int, x: Int, y: Int) {
    if (!chance(1, 6)) return
    var waterFound = false
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if (nm == El.WATER.id || nm == El.SALT_WATER.id) {
            waterFound = true
            if (chance(1, 4)) {
                place(j, El.PLANT.id)
                life[j] = 30
            }
        }
        false
    }
    val up = y - 1
    if (up >= 0 && life[i] > 0 && chance(1, 8)) {
        val j = up * width + x
        if (mat[j] == 0) {
            place(j, El.PLANT.id)
            life[j] = life[i] - 1
            life[i]--
        }
    }
    if (waterFound && life[i] < 60) life[i]++
}

private fun World.germinate(i: Int, x: Int, y: Int) {
    if (!chance(1, 8)) return
    var wet = false
    var soil = false
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if (nm == El.WATER.id || nm == El.SALT_WATER.id) wet = true
        if (nm == El.DIRT.id || nm == El.SAND.id) soil = true
        false
    }
    if (wet && soil && chance(1, 3)) {
        place(i, El.PLANT.id)
        life[i] = 24
        if (y > 0) {
            val j = (y - 1) * width + x
            if (mat[j] == 0) {
                place(j, El.PLANT.id)
                life[j] = 20
            }
        }
    }
}

private fun World.spreadFungus(i: Int, x: Int, y: Int) {
    if (!chance(1, 6)) return
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if ((nm == El.WOOD.id || nm == El.PLANT.id || nm == El.DIRT.id) && chance(1, 3)) {
            place(j, El.FUNGUS.id)
            true
        } else if ((nm == El.WATER.id) && chance(1, 12)) {
            place(j, El.FUNGUS.id)
            true
        } else false
    }
}

private fun World.infect(i: Int, x: Int, y: Int) {
    if (!chance(1, 4)) return
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        val ne = if (nm == 0) null else El.ALL[nm]
        if (ne != null && (nm == El.PLANT.id || nm == El.FUNGUS.id || nm == El.SEED.id) && chance(1, 3)) {
            place(j, El.VIRUS.id)
            true
        } else if (nm == El.WATER.id && chance(1, 20)) {
            place(j, El.VIRUS.id)
            true
        } else false
    }
}

private fun World.poison(i: Int, x: Int, y: Int) {
    if (!chance(1, 6)) return
    eachNeighbor(x, y) { j ->
        val nm = mat[j]
        if ((nm == El.PLANT.id || nm == El.FUNGUS.id || nm == El.SEED.id || nm == El.VIRUS.id) && chance(1, 2)) {
            place(j, El.ASH.id)
            true
        } else false
    }
}

// ---------------------------------------------------------------- explosions --

private fun World.detonate(x: Int, y: Int, e: Element) {
    if (explosionBudget <= 0) return
    explosionBudget--
    explode(x.toFloat(), y.toFloat(), e.explosive, 700f + e.explosive * 90f)
}

/** Blows a crater: destroys weak materials in [radius], heats everything and leaves fire/smoke. */
fun World.explode(cx: Float, cy: Float, radius: Float, heat: Float) {
    val r = radius
    if (r <= 0f) return
    val r2 = r * r
    val x0 = (cx - r).toInt().coerceAtLeast(0)
    val x1 = (cx + r).toInt().coerceAtMost(width - 1)
    val y0 = (cy - r).toInt().coerceAtLeast(0)
    val y1 = (cy + r).toInt().coerceAtMost(height - 1)
    for (y in y0..y1) {
        for (x in x0..x1) {
            val dx = x - cx
            val dy = y - cy
            val d2 = dx * dx + dy * dy
            if (d2 > r2) continue
            val d = kotlin.math.sqrt(d2)
            val falloff = 1f - d / r
            val i = y * width + x
            if (falloff > 0.05f) temp[i] = maxOf(temp[i], heat * falloff)
            val m = mat[i]
            if (m == 0) {
                if (chance(falloff * 0.4f)) place(i, El.FIRE.id)
                continue
            }
            val e = El.ALL[m]
            if (e.indestructible) continue
            if (e.strength > falloff * 16f) continue
            when {
                falloff > 0.6f -> place(i, 0)
                chance(falloff) -> place(i, El.FIRE.id)
                chance(falloff * 0.4f) -> place(i, El.SMOKE.id)
            }
        }
    }
}

// ----------------------------------------------------------------- chemistry --

private class Reaction(
    val a: Int,
    val b: Int,
    val aInto: Int,
    val bInto: Int,
    val chance: Int = 255,
    val aTemp: Float = Float.NaN,
    val bTemp: Float = Float.NaN,
    val minTemp: Float = -Float.MAX_VALUE,
    val maxTemp: Float = Float.MAX_VALUE,
)

private object Chem {
    private val list: List<Reaction> by lazy {
        val w = El.WATER.id
        val lava = El.LAVA.id
        buildList {
            add(Reaction(w, lava, El.STEAM.id, El.OBSIDIAN.id, 210, aTemp = 130f, bTemp = 780f))
            add(Reaction(w, El.FIRE.id, El.STEAM.id, 0, 230, aTemp = 110f))
            add(Reaction(w, El.MOLTEN_METAL.id, El.STEAM.id, El.METAL.id, 200, aTemp = 120f, bTemp = 1300f))
            add(Reaction(w, El.MOLTEN_GLASS.id, El.STEAM.id, El.GLASS.id, 200, aTemp = 120f, bTemp = 900f))
            add(Reaction(El.ICE.id, lava, w, El.STONE.id, 220, aTemp = 30f, bTemp = 800f))
            add(Reaction(El.SNOW.id, lava, w, El.STONE.id, 220, aTemp = 60f, bTemp = 800f))
            add(Reaction(El.SALT.id, w, 0, El.SALT_WATER.id, 45, bTemp = 20f))
            add(Reaction(El.SALT.id, El.ICE.id, 0, El.SALT_WATER.id, 40, bTemp = -2f))
            add(Reaction(w, El.CHLORINE.id, El.ACID.id, 0, 6))
            add(Reaction(w, El.PLASMA.id, El.STEAM.id, -1, 255, aTemp = 140f))
            add(Reaction(w, El.SPARK.id, El.STEAM.id, 0, 60, aTemp = 105f))
            add(Reaction(El.OXYGEN.id, El.FIRE.id, El.FIRE.id, -1, 90, aTemp = 1500f))
            add(Reaction(El.CO2.id, El.FIRE.id, -1, 0, 200))
            add(Reaction(El.SALT_WATER.id, El.MOLTEN_METAL.id, El.STEAM.id, El.METAL.id, 180, aTemp = 120f, bTemp = 1300f))
        }
    }

    private val byMat: Array<MutableList<Reaction>?> by lazy {
        val out = arrayOfNulls<MutableList<Reaction>>(El.ALL.size)
        for (r in list) {
            val la = out[r.a] ?: ArrayList<Reaction>().also { out[r.a] = it }
            la.add(r)
            if (r.b != r.a) {
                val lb = out[r.b] ?: ArrayList<Reaction>().also { out[r.b] = it }
                lb.add(r)
            }
        }
        out
    }

    fun of(m: Int): List<Reaction>? = byMat[m]
}

private fun World.chemistry(i: Int, x: Int, y: Int, m: Int) {
    val rules = Chem.of(m) ?: return
    if (x + 1 < width) pair(i, i + 1, m, rules)
    if (y + 1 < height) pair(i, i + width, m, rules)
}

private fun World.pair(i: Int, j: Int, m: Int, rules: List<Reaction>) {
    val m2 = mat[j]
    if (m2 == 0) return
    for (r in rules) {
        val forward = r.a == m && r.b == m2
        val backward = r.b == m && r.a == m2
        if (!forward && !backward) continue
        if (r.chance < 255 && randInt(255) >= r.chance) return
        val ia = if (forward) r.aInto else r.bInto
        val ib = if (forward) r.bInto else r.aInto
        val ta = if (forward) r.aTemp else r.bTemp
        val tb = if (forward) r.bTemp else r.aTemp
        if (ia != -1) {
            place(i, ia)
            if (!ta.isNaN()) temp[i] = ta
        }
        if (ib != -1) {
            place(j, ib)
            if (!tb.isNaN()) temp[j] = tb
        }
        return
    }
}

// ------------------------------------------------------------------- helpers --

private inline fun World.eachNeighbor(x: Int, y: Int, body: (Int) -> Boolean) {
    if (x > 0 && body(y * width + x - 1)) return
    if (x + 1 < width && body(y * width + x + 1)) return
    if (y > 0 && body((y - 1) * width + x)) return
    if (y + 1 < height) body((y + 1) * width + x)
}

private fun World.spawnNear(x: Int, y: Int, id: Int, prob: Float): Boolean {
    if (randFloat() > prob) return false
    val nx = x + randInt(3) - 1
    val ny = y + randInt(3) - 1
    if (nx < 0 || nx >= width || ny < 0 || ny >= height) return false
    val j = ny * width + nx
    if (mat[j] == 0) {
        place(j, id)
        return true
    }
    return false
}

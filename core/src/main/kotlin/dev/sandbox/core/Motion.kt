package dev.sandbox.core

/**
 * Gravity driven movement pass.
 *
 * Heavy materials (powders, liquids, heavy gases) are scanned bottom-up so that a cell which moves
 * one row down lands in a row that was already processed and cannot move twice per tick. Light
 * materials (steam, fire, hydrogen...) are scanned in the opposite direction for the same reason.
 * The per-cell [World.stamp] prevents double updates of a single cell.
 */
internal fun World.motionPass() {
    if (gravityFrozen) return
    stampId++
    val down = if (gravityUp) -1 else 1

    if (down > 0) {
        for (y in height - 2 downTo 0) motionRow(y, down, falling = true)
        for (y in 1 until height) motionRow(y, down, falling = false)
    } else {
        for (y in 1 until height) motionRow(y, down, falling = true)
        for (y in height - 2 downTo 0) motionRow(y, down, falling = false)
    }
}

private fun World.motionRow(y: Int, down: Int, falling: Boolean) {
    val leftToRight = ((frame + y) and 1) == 0
    val bias = if (((frame * 7 + y * 13) and 1) == 0) 1 else -1
    var x = if (leftToRight) 0 else width - 1
    val step = if (leftToRight) 1 else -1

    while (x in 0 until width) {
        val i = y * width + x
        if (stamp[i] != stampId) {
            val m = mat[i]
            if (m != 0) {
                val e = El.ALL[m]
                when (e.phase) {
                    Phase.POWDER -> if (falling) movePowder(x, y, i, e, down, bias)
                    Phase.LIQUID -> if (falling) moveLiquid(x, y, i, e, down, bias)
                    Phase.GAS ->
                        if (falling != e.rises) moveGas(x, y, i, e, down, bias)
                    Phase.ENERGY ->
                        if (e.rises && !falling && chance(1, 2)) moveGas(x, y, i, e, down, bias)
                    else -> {}
                }
            }
        }
        x += step
    }
}

// ----------------------------------------------------------------------- helpers --

/** Can a cell of [e] take over cell [to]? Vertical moves may displace lighter/heavier fluids. */
private fun World.canMoveInto(to: Int, e: Element, movingDown: Boolean): Boolean {
    val t = mat[to]
    if (t == 0) return true
    val te = El.ALL[t]
    if (te.indestructible) return false
    when (te.phase) {
        Phase.SOLID, Phase.LIFE, Phase.SPECIAL, Phase.POWDER -> return false
        else -> {}
    }
    if (te.density == e.density) return false
    return if (movingDown) te.density < e.density else te.density > e.density
}

/**
 * Tries to move the cell at ([x],[y]) by ([dx],[dy]). Vertical moves may swap with a fluid of a
 * different density (buoyancy), sideways moves only enter empty cells.
 */
private fun World.tryMove(x: Int, y: Int, i: Int, e: Element, dx: Int, dy: Int): Boolean {
    val nx = x + dx
    val ny = y + dy
    if (nx < 0 || nx >= width || ny < 0 || ny >= height) return false
    val t = ny * width + nx
    val ok = if (dx == 0) canMoveInto(t, e, dy > 0) else mat[t] == 0
    if (!ok) return false
    swapCells(i, t)
    stamp[t] = stampId
    return true
}

/** Slides a liquid along its row up to [maxDist] cells into free space (surface levelling). */
private fun World.slide(x: Int, y: Int, i: Int, e: Element, dir: Int, maxDist: Int): Boolean {
    var best = -1
    var d = 1
    while (d <= maxDist) {
        val nx = x + dir * d
        if (nx < 0 || nx >= width) break
        val t = y * width + nx
        if (mat[t] != 0) break
        best = t
        d++
    }
    if (best < 0) return false
    swapCells(i, best)
    stamp[best] = stampId
    return true
}

internal fun World.swapCells(a: Int, b: Int) {
    val mA = mat[a]; mat[a] = mat[b]; mat[b] = mA
    val tA = temp[a]; temp[a] = temp[b]; temp[b] = tA
    val lA = life[a]; life[a] = life[b]; life[b] = lA
    val fA = flags[a]; flags[a] = flags[b]; flags[b] = fA
    val cA = tone[a]; tone[a] = tone[b]; tone[b] = cA
}

// ------------------------------------------------------------------- behaviours --

private fun World.movePowder(x: Int, y: Int, i: Int, e: Element, down: Int, bias: Int) {
    if (tryMove(x, y, i, e, 0, down)) return
    if (e.slide < 100 && randInt(100) >= e.slide) return
    val first = if (bias > 0) 1 else -1
    if (tryMove(x, y, i, e, first, down)) return
    tryMove(x, y, i, e, -first, down)
}

private fun World.moveLiquid(x: Int, y: Int, i: Int, e: Element, down: Int, bias: Int) {
    if (e.viscosity in 1..99 && randInt(100) < e.viscosity) return

    if (tryMove(x, y, i, e, 0, down)) return
    val first = if (bias > 0) 1 else -1
    if (tryMove(x, y, i, e, first, down)) return
    if (tryMove(x, y, i, e, -first, down)) return

    // Buoyancy: a lighter liquid climbs through a heavier one sitting on top of it.
    val uy = y - down
    if (uy in 0 until height) {
        val up = uy * width + x
        val um = mat[up]
        if (um != 0) {
            val ue = El.ALL[um]
            val fluid = ue.phase == Phase.LIQUID || ue.phase == Phase.GAS
            if (fluid && !ue.indestructible && ue.density > e.density) {
                swapCells(i, up)
                stamp[up] = stampId
                return
            }
        }
    }

    if (e.dispersion > 0) {
        // Liquids first fill holes (a free cell with nothing underneath) up to `dispersion` cells
        // away, then optionally wander one cell sideways. Random directions keep a pool level
        // instead of drifting towards one wall.
        val dir = if (chance(1, 2)) 1 else -1
        if (flowToHole(x, y, i, e, dir, e.dispersion)) return
        if (flowToHole(x, y, i, e, -dir, e.dispersion)) return
        if (chance(1, 6)) {
            if (tryMove(x, y, i, e, dir, 0)) return
            tryMove(x, y, i, e, -dir, 0)
        }
    }
}

/** Slides to the farthest free cell within [maxDist] that has a hole underneath it. */
private fun World.flowToHole(x: Int, y: Int, i: Int, e: Element, dir: Int, maxDist: Int): Boolean {
    var best = -1
    var d = 1
    while (d <= maxDist) {
        val nx = x + dir * d
        if (nx < 0 || nx >= width) break
        val t = y * width + nx
        if (mat[t] != 0) break
        if (y + 1 < height && mat[t + width] == 0) best = t
        d++
    }
    if (best < 0) return false
    swapCells(i, best)
    stamp[best] = stampId
    return true
}

private fun World.moveGas(x: Int, y: Int, i: Int, e: Element, down: Int, bias: Int) {
    val vy = if (e.rises) -down else down
    if (tryMove(x, y, i, e, 0, vy)) return
    val first = if (bias > 0) 1 else -1
    if (tryMove(x, y, i, e, first, vy)) return
    if (tryMove(x, y, i, e, -first, vy)) return
    if (e.dispersion > 0) {
        if (!slide(x, y, i, e, first, e.dispersion)) slide(x, y, i, e, -first, e.dispersion)
    } else if (chance(1, 2)) {
        tryMove(x, y, i, e, first, 0)
    }
}

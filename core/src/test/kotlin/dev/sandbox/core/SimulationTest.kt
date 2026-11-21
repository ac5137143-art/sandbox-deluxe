package dev.sandbox.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

/**
 * Behavioural tests for the simulation engine. They check the physical properties a falling sand
 * game must have (mass conservation, no floating piles, levelling liquids, stratification,
 * thermodynamics, combustion, explosions) rather than exact cell layouts, so they stay stable as
 * the tuning changes.
 */
class SimulationTest {

    private fun world(w: Int = 48, h: Int = 48, seed: Long = 0x5EEDL): World = World(w, h, seed)

    private fun World.rect(x0: Int, y0: Int, x1: Int, y1: Int, id: Int) {
        for (y in y0..y1) for (x in x0..x1) place(x, y, id)
    }

    private fun World.run(steps: Int) = repeat(steps) { step() }

    private fun World.runTracking(steps: Int, watch: Int): Int {
        var max = 0
        repeat(steps) {
            step()
            val c = countOf(watch)
            if (c > max) max = c
        }
        return max
    }

    private fun World.columnHeights(id: Int): IntArray {
        val out = IntArray(width) { -1 }
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (at(x, y) == id) { out[x] = y; break }
            }
        }
        return out
    }

    /** True when no cell of [id] hangs in the air (nothing directly underneath). */
    private fun World.isSupported(id: Int): Boolean {
        for (y in 0 until height - 1) {
            for (x in 0 until width) {
                if (at(x, y) == id && at(x, y + 1) == 0) return false
            }
        }
        return true
    }

    private fun maxTemp(w: World, id: Int): Float {
        var t = -Float.MAX_VALUE
        for (i in 0 until w.n) if (w.mat[i] == id) t = maxOf(t, w.temp[i])
        return t
    }

    // -------------------------------------------------------------------- powders --

    @Test
    fun sandFallsAndPilesWithoutFloating() {
        val w = world(24, 48)
        w.rect(8, 1, 15, 20, El.SAND.id) // 8 x 20 = 160 cells
        val before = w.countOf(El.SAND.id)
        assertEquals(160, before)

        w.run(400)

        assertEquals(before, w.countOf(El.SAND.id), "sand must be conserved while falling")
        assertTrue(w.isSupported(El.SAND.id), "no sand may stay floating in mid air")
        for (y in 0 until 24) {
            for (x in 0 until w.width) {
                assertNotEquals(El.SAND.id, w.at(x, y), "sand still in the upper half at y=$y")
            }
        }
        for (x in 0 until w.width) {
            assertEquals(El.SAND.id, w.at(x, w.height - 1), "bottom row must be covered at x=$x")
        }
    }

    @Test
    fun gravelPilesSteeperThanSand() {
        fun pileHeight(id: Int): Int {
            val w = world(40, 40)
            w.rect(18, 1, 21, 20, id)
            w.run(400)
            var lowest = 0
            for (x in 0 until w.width) if (w.at(x, w.height - 1) == id) lowest++
            val heights = w.columnHeights(id)
            return heights.filter { it >= 0 }.max()
        }
        val sandTop = pileHeight(El.SAND.id)
        val gravelTop = pileHeight(El.GRAVEL.id)
        assertTrue(gravelTop >= sandTop, "gravel pile ($gravelTop) should be at least as steep as sand ($sandTop)")
    }

    @Test
    fun sandSinksThroughWater() {
        val w = world(30, 40)
        w.rect(0, 39, 29, 39, El.WALL.id)
        w.rect(2, 20, 27, 38, El.WATER.id)
        val waterTop = w.columnHeights(El.WATER.id).filter { it >= 0 }.min()
        w.rect(12, 2, 17, 6, El.SAND.id)

        w.run(500)

        assertTrue(w.isSupported(El.SAND.id), "sand must not float on the surface")
        val sandTop = w.columnHeights(El.SAND.id).filter { it >= 0 }.min()
        assertTrue(sandTop > waterTop, "sand must sink below the water surface ($sandTop <= $waterTop)")
        assertEquals(El.SAND.id, w.at(15, 38), "sand should rest on the floor")
    }

    // -------------------------------------------------------------------- liquids --

    @Test
    fun waterLevelsOutInAContainer() {
        val w = world(40, 40)
        w.rect(0, 0, 1, 39, El.WALL.id)
        w.rect(38, 0, 39, 39, El.WALL.id)
        w.rect(0, 38, 39, 39, El.WALL.id)
        w.rect(5, 10, 9, 37, El.WATER.id)
        val before = w.countOf(El.WATER.id)

        w.run(1200)

        assertEquals(before, w.countOf(El.WATER.id), "water must be conserved")
        val heights = w.columnHeights(El.WATER.id).filter { it in 0 until w.height }
        val spread = heights.max() - heights.min()
        assertTrue(spread <= 3, "surface should be level, spread was $spread (heights=${heights.toList()})")
    }

    @Test
    fun oilFloatsOnWater() {
        val w = world(40, 40)
        w.rect(0, 0, 1, 39, El.WALL.id)
        w.rect(38, 0, 39, 39, El.WALL.id)
        w.rect(0, 38, 39, 39, El.WALL.id)
        w.rect(2, 25, 37, 37, El.WATER.id)
        w.rect(2, 18, 37, 24, El.OIL.id)

        w.run(900)

        var lowestOil = -1
        var highestWater = -1
        for (y in 0 until w.height) {
            for (x in 0 until w.width) {
                val m = w.at(x, y)
                if (m == El.OIL.id) lowestOil = maxOf(lowestOil, y)
                if (m == El.WATER.id) {
                    if (highestWater < 0 || y < highestWater) highestWater = y
                }
            }
        }
        assertTrue(lowestOil in 0 until highestWater, "oil (down to $lowestOil) must stay above water (from $highestWater)")
    }

    @Test
    fun viscousLiquidSpreadsSlowerThanWater() {
        val w = world(30, 30)
        w.rect(0, 0, 0, 29, El.WALL.id)
        w.rect(29, 0, 29, 29, El.WALL.id)
        w.rect(1, 29, 28, 29, El.WALL.id)
        w.rect(1, 20, 28, 24, El.SLIME.id)

        w.run(600)

        val heights = w.columnHeights(El.SLIME.id).filter { it >= 0 }
        assertTrue(heights.isNotEmpty(), "slime must stay in the sandbox")
        val spread = heights.max() - heights.min()
        assertTrue(spread <= 6, "viscous slime should not level out instantly, spread was $spread")
        assertTrue(w.countOf(El.SLIME.id) > 0)
    }

    // ------------------------------------------------------------------ thermodynamics --

    @Test
    fun heatDiffusesAndCoolsTowardsAmbient() {
        val w = world(40, 40)
        w.rect(12, 12, 22, 22, El.METAL.id)
        for (y in 12..22) for (x in 12..22) w.temp[w.index(x, y)] = 900f
        val center = w.index(17, 17)
        val outside = w.index(24, 17)

        w.run(1500)

        assertTrue(w.temp[center] < 300f, "hot metal must cool down, was ${w.temp[center]}")
        assertTrue(w.temp[outside] > AMBIENT + 1f, "heat must reach the surrounding air, was ${w.temp[outside]}")
    }

    @Test
    fun iceMeltsNearAHeater() {
        val w = world(30, 30)
        w.rect(10, 10, 20, 16, El.ICE.id)
        val before = w.countOf(El.ICE.id)
        w.place(15, 18, El.HEATER.id)

        w.run(600)

        assertTrue(w.countOf(El.ICE.id) < before, "ice must melt near a heater")
        assertTrue(w.countOf(El.WATER.id) > 0, "melting ice must produce water")
    }

    @Test
    fun waterBoilsOnAHeaterAndCondensesAwayFromIt() {
        val w = world(30, 40)
        w.rect(2, 25, 27, 38, El.WATER.id)
        w.place(15, 37, El.HEATER.id)

        val maxSteam = w.runTracking(600, El.STEAM.id)

        assertTrue(maxSteam > 0, "boiling water must produce steam")
        assertTrue(w.countOf(El.WATER.id) > 0, "not all water should boil away")
    }

    @Test
    fun steamCondensesInTheCold() {
        val w = world(30, 30)
        w.ambient = -40f
        w.rect(5, 20, 25, 25, El.STEAM.id)
        assertTrue(w.countOf(El.STEAM.id) > 0)

        w.run(300)

        assertTrue(w.countOf(El.WATER.id) > 0, "cold steam must condense into water")
    }

    @Test
    fun lavaFreezesIntoStone() {
        val w = world(30, 30)
        w.rect(10, 10, 20, 20, El.LAVA.id)

        w.run(3000)

        assertTrue(w.countOf(El.STONE.id) > 0 || w.countOf(El.OBSIDIAN.id) > 0, "lava must solidify")
        assertTrue(maxTemp(w, El.STONE.id) < 1200f)
    }

    @Test
    fun waterAndLavaMakeSteamAndObsidian() {
        val w = world(40, 40)
        w.rect(0, 30, 39, 39, El.STONE.id)
        w.rect(5, 20, 15, 29, El.WATER.id)
        w.rect(16, 20, 26, 29, El.LAVA.id)

        w.run(400)

        assertTrue(w.countOf(El.STEAM.id) > 0, "lava + water must make steam")
        assertTrue(w.countOf(El.OBSIDIAN.id) > 0, "lava + water must leave solidified rock")
    }

    // -------------------------------------------------------------------- combustion --

    @Test
    fun woodBurnsIntoAsh() {
        val w = world(40, 40)
        w.rect(0, 30, 39, 39, El.DIRT.id)
        w.rect(15, 20, 24, 29, El.WOOD.id)
        val wood = w.countOf(El.WOOD.id)
        w.rect(15, 19, 17, 19, El.FIRE.id)

        val maxFire = w.runTracking(4000, El.FIRE.id)
        val maxSmoke = w.countOf(El.SMOKE.id)

        assertTrue(w.countOf(El.WOOD.id) < wood, "fire must consume wood")
        assertTrue(w.countOf(El.ASH.id) > 0, "burning wood must leave ash")
        assertTrue(maxFire > 0 || maxSmoke > 0, "burning must produce smoke or flame")
    }

    @Test
    fun gunpowderDetonatesInAChain() {
        val w = world(60, 40)
        w.rect(0, 35, 59, 39, El.CONCRETE.id)
        w.rect(4, 34, 55, 34, El.GUNPOWDER.id)
        val before = w.countOf(El.GUNPOWDER.id)
        w.place(6, 33, El.FIRE.id)

        w.run(300)

        assertTrue(w.countOf(El.GUNPOWDER.id) < before / 4, "the blast must consume the powder trail")
        var crater = 0
        for (y in 35..39) for (x in 0 until 60) if (w.at(x, y) == 0) crater++
        assertTrue(crater > 0, "the explosion must tear a crater into the floor")
    }

    @Test
    fun tntExplosionIsStrongerThanGunpowder() {
        fun crater(id: Int): Int {
            val w = world(60, 60)
            w.rect(0, 40, 59, 59, El.CONCRETE.id)
            w.rect(20, 30, 40, 39, id)
            w.rect(25, 29, 35, 29, El.FIRE.id)
            w.run(300)
            var holes = 0
            for (y in 40..59) for (x in 0 until 60) if (w.at(x, y) == 0) holes++
            return holes
        }
        val tnt = crater(El.TNT.id)
        val powder = crater(El.GUNPOWDER.id)
        assertTrue(tnt > 0, "TNT must leave a crater")
        assertTrue(tnt >= powder, "TNT ($tnt) should not be weaker than gunpowder ($powder)")
    }

    @Test
    fun indestructibleWallsSurviveExplosions() {
        val w = world(60, 60)
        w.rect(0, 40, 59, 41, El.WALL.id)
        w.rect(20, 30, 40, 39, El.C4.id)
        w.rect(28, 29, 32, 29, El.FIRE.id)

        w.run(300)

        assertEquals(2 * 60, w.countOf(El.WALL.id), "the shell must not be damaged")
        assertTrue(w.countOf(El.C4.id) < 21 * 10, "the charge must detonate")
    }

    @Test
    fun acidDissolvesMetalAndReleasesHydrogen() {
        val w = world(30, 30)
        w.rect(0, 29, 29, 29, El.WALL.id)
        w.rect(4, 22, 25, 28, El.ACID.id)
        w.rect(12, 12, 18, 21, El.METAL.id)
        val metal = w.countOf(El.METAL.id)

        var maxFumes = 0
        repeat(1200) {
            w.step()
            val fumes = w.countOf(El.SMOKE.id) + w.countOf(El.HYDROGEN.id)
            if (fumes > maxFumes) maxFumes = fumes
        }

        assertTrue(w.countOf(El.METAL.id) < metal, "acid must eat metal")
        assertTrue(maxFumes > 0, "dissolving metal releases fumes")
    }

    // ------------------------------------------------------------------------- life --

    @Test
    fun plantsGrowTowardsWater() {
        val w = world(40, 40)
        w.rect(0, 30, 39, 39, El.DIRT.id)
        w.rect(20, 25, 39, 29, El.WATER.id)
        w.place(19, 29, El.SEED.id)

        w.run(2000)

        assertTrue(w.countOf(El.PLANT.id) > 0, "a watered seed must grow into a plant")
    }

    @Test
    fun virusesInfectPlants() {
        val w = world(40, 40)
        w.rect(5, 25, 34, 29, El.PLANT.id)
        w.place(10, 24, El.VIRUS.id)

        w.run(600)

        assertTrue(w.countOf(El.VIRUS.id) > 1, "the virus must spread")
    }

    // --------------------------------------------------------------------- machines --

    @Test
    fun clonerCopiesMaterials() {
        val w = world(30, 30)
        w.rect(0, 29, 29, 29, El.WALL.id)
        w.place(15, 28, El.CLONER.id)
        w.place(14, 28, El.SAND.id)
        w.place(16, 28, El.SAND.id)

        w.run(400)

        assertTrue(w.countOf(El.SAND.id) > 2, "the cloner must multiply the material")
    }

    @Test
    fun annihilatorDeletesEverything() {
        val w = world(30, 30)
        w.rect(5, 5, 24, 24, El.SAND.id)
        w.place(15, 15, El.VOID.id)

        w.run(200)

        assertTrue(w.countOf(El.SAND.id) < 400 - 10, "the annihilator must swallow sand")
        assertEquals(1, w.countOf(El.VOID.id), "machines are not destroyed by their own effect")
    }

    @Test
    fun waterTapKeepsSupplyingWater() {
        val w = world(30, 30)
        w.rect(0, 29, 29, 29, El.WALL.id)
        w.place(15, 28, El.WATER_TAP.id)

        w.run(600)

        assertTrue(w.countOf(El.WATER.id) > 50, "the tap must fill the sandbox")
    }

    @Test
    fun batteryLightsUpAConductor() {
        val w = world(30, 30)
        w.place(15, 15, El.BATTERY.id)
        w.rect(15, 16, 15, 24, El.WIRE.id)

        val maxSpark = w.runTracking(200, El.SPARK.id)

        assertTrue(maxSpark > 0, "the battery must energise the wire")
    }

    @Test
    fun electricityIgnitesPetrolVapour() {
        val w = world(40, 40)
        w.rect(0, 39, 39, 39, El.WALL.id)
        w.rect(5, 30, 34, 38, El.PETROL.id)
        w.place(20, 29, El.LIGHTNING.id)

        w.run(400)

        assertTrue(w.countOf(El.FIRE.id) > 0 || w.countOf(El.SMOKE.id) > 0, "a strike must ignite fuel")
    }

    // ------------------------------------------------------------------- persistence --

    @Test
    fun scenesRunWithoutBlowingUp() {
        for (index in Scenes.NAMES.indices) {
            val w = world(160, 240, seed = 0xBEEFL + index)
            Scenes.apply(w, index)
            w.run(400)
            for (i in 0 until w.n) {
                val t = w.temp[i]
                assertTrue(t.isFinite(), "scene ${Scenes.NAMES[index]} produced a non finite temperature")
                assertTrue(t in -300f..6100f, "scene ${Scenes.NAMES[index]} produced $t")
                assertTrue(w.mat[i] in 0 until El.ALL.size, "scene ${Scenes.NAMES[index]} produced a bad id")
            }
            w.paintPixels(thermal = true)
            for (i in 0 until w.n) assertTrue((w.pixels[i] ushr 24) != 0, "thermal view must be opaque")
        }
    }

    @Test
    fun sceneFilesRoundTrip() {
        val w = world(64, 96, seed = 7L)
        Scenes.apply(w, 2)
        w.run(200)
        w.paint(32, 40, 8, El.ACID.id)

        val bytes = Codec.encode(w, "тест")
        val decoded = Codec.decode(bytes)
        val back = decoded.world

        assertEquals("тест", decoded.name)
        assertEquals(w.width, back.width)
        assertEquals(w.height, back.height)
        for (i in 0 until w.n) {
            assertEquals(w.mat[i], back.mat[i], "material mismatch at $i")
            assertEquals(w.temp[i], back.temp[i], 0.0001f, "temperature mismatch at $i")
            assertEquals(w.life[i], back.life[i], "life mismatch at $i")
            assertEquals(w.flags[i], back.flags[i], "flag mismatch at $i")
        }
        assertTrue(bytes.size < w.n * 8, "scene should compress, was ${bytes.size} bytes for ${w.n} cells")
    }

    @Test
    fun brushRespectsIndestructibleCells() {
        val w = world(40, 40)
        w.rect(10, 10, 30, 30, El.WALL.id)
        w.paint(20, 20, 6, El.SAND.id)
        assertEquals(21 * 21, w.countOf(El.WALL.id), "painting must not erase a shell")
        w.paint(20, 20, 6, El.EMPTY.id)
        assertEquals(21 * 21, w.countOf(El.WALL.id), "the eraser must not erase a shell either")
        assertEquals(0, w.countOf(El.SAND.id))
    }

    // ------------------------------------------------------------------ performance --

    @Test
    fun engineKeepsUpWithSixtyFramesPerSecond() {
        val w = world(300, 460, seed = 42L)
        Scenes.apply(w, 2)
        w.paint(120, 40, 30, El.WATER.id)
        w.paint(200, 60, 24, El.SAND.id)
        w.paint(60, 90, 18, El.LAVA.id)
        w.paint(150, 120, 20, El.GUNPOWDER.id)

        repeat(60) { w.step() }
        val elapsed = measureTimeMillis { repeat(240) { w.step() } }
        val perStep = elapsed / 240.0
        println("PERF: ${w.n} cells, ${w.particleCount()} particles -> $perStep ms/step")

        assertTrue(perStep < 60.0, "simulation step took $perStep ms, expected far below the 16.7 ms frame budget")
    }
}

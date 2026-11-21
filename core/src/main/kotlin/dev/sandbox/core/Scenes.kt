package dev.sandbox.core

/** Ready made scenes, built from the same materials the player can paint with. */
object Scenes {
    val NAMES: List<String> = listOf(
        "Пустая песочница",
        "Песочный водопад",
        "Вулкан",
        "Лесной пожар",
        "Химлаборатория",
        "Полигон",
        "Ледяная пещера",
        "Океан",
    )

    fun apply(world: World, index: Int) {
        world.clear()
        world.ambient = AMBIENT
        when (index) {
            1 -> sandFall(world)
            2 -> volcano(world)
            3 -> forestFire(world)
            4 -> lab(world)
            5 -> provingGround(world)
            6 -> iceCave(world)
            7 -> ocean(world)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------ scenes --

    private fun sandFall(w: World) {
        val cx = w.width / 2
        ground(w, El.STONE.id)
        // funnel walls
        for (y in w.height / 5 until w.height * 3 / 5) {
            val dy = y - w.height / 5
            w.plate(cx - w.width / 4 + dy / 3, y, 4, 1, El.WALL.id)
            w.plate(cx + w.width / 4 - dy / 3, y, 4, 1, El.WALL.id)
        }
        // sand reservoir with a narrow outlet
        val top = w.height / 6
        w.plate(cx - w.width / 4, top, w.width / 2, w.height / 10, El.SAND.id)
        w.plate(cx - 2, top + w.height / 10, 5, 2, El.SAND.id)
        w.plate(cx - 1, top + w.height / 10 + 2, 3, 3, El.SAND.id)
        // water tap on the left shoulder
        w.place(cx - w.width / 4 - 3, top + 2, El.WATER_TAP.id)
        w.plate(cx - w.width / 4 - 3, top + 4, 1, w.height / 3, El.WATER.id)
        // a few bricks to bury
        w.plate(cx - w.width / 8, w.height - w.height / 8, w.width / 4, 3, El.BRICK.id)
    }

    private fun volcano(w: World) {
        val cx = w.width / 2
        ground(w, El.STONE.id)
        val h = w.height * 2 / 5
        for (y in 0 until h) {
            val half = (w.width / 3) * (h - y) / h + 2
            w.plate(cx - half, w.height - w.height / 8 - y, half * 2, 1, El.STONE.id)
        }
        val ventTop = w.height - w.height / 8 - h - 1
        w.plate(cx - 2, ventTop - 6, 5, 7, El.LAVA_VENT.id)
        w.plate(cx - 1, ventTop - 8, 3, 2, El.LAVA.id)
        // a lake at the foot of the mountain
        w.plate(0, w.height - w.height / 8 - 3, w.width / 3, 3, El.WATER.id)
        w.plate(w.width * 2 / 3, w.height - w.height / 8 - 3, w.width / 3, 3, El.WATER.id)
        // snow cap
        w.plate(cx - 3, ventTop - 10, 7, 2, El.SNOW.id)
        // plants on the slopes
        for (x in 0 until w.width step 9) {
            val y = w.height - w.height / 8 - 2
            if (w.at(x, y) == El.STONE.id && w.at(x, y - 1) == 0) w.place(x, y - 1, El.PLANT.id)
        }
    }

    private fun forestFire(w: World) {
        ground(w, El.DIRT.id)
        val base = w.height - w.height / 8 - 1
        for (i in 0 until 4) {
            val x = w.width / 8 + i * w.width / 5
            tree(w, x, base, w.height / 5)
        }
        w.plate(0, base - 1, w.width / 3, 1, El.WATER.id)
        w.place(w.width / 12, base - 4, El.SEED.id)
        w.place(w.width * 3 / 4, base - 2, El.FIRE.id)
        w.place(w.width * 3 / 4 + 1, base - 2, El.FIRE.id)
    }

    private fun lab(w: World) {
        ground(w, El.CONCRETE.id)
        val top = w.height / 3
        // glass tanks
        w.hollowRect(w.width / 10, top, w.width / 10 + w.width / 4, w.height - w.height / 8, 1, El.GLASS.id)
        w.hollowRect(w.width / 2, top + 4, w.width / 2 + w.width / 4, w.height - w.height / 8, 1, El.GLASS.id)
        w.plate(w.width / 10 + 2, w.height - w.height / 8 - 3, w.width / 5, 3, El.ACID.id)
        w.plate(w.width / 2 + 2, w.height - w.height / 8 - 4, w.width / 5, 4, El.WATER.id)
        // metal samples dropped into the acid
        w.plate(w.width / 10 + 5, top + 2, 3, 3, El.METAL.id)
        w.plate(w.width / 2 + 5, top + 2, 3, 3, El.COPPER.id)
        w.plate(w.width / 2 + w.width / 8, top + 2, 3, 3, El.SALT.id)
        w.place(w.width / 12, top - 3, El.HEATER.id)
        w.place(w.width * 3 / 4, top - 3, El.FREEZER.id)
        w.place(w.width / 3, top - 3, El.SEED.id)
    }

    private fun provingGround(w: World) {
        ground(w, El.CONCRETE.id)
        val floor = w.height - w.height / 8
        // bunker walls
        w.plate(w.width / 10, floor - w.height / 4, 3, w.height / 4, El.CONCRETE.id)
        w.plate(w.width - w.width / 10 - 3, floor - w.height / 4, 3, w.height / 4, El.CONCRETE.id)
        w.plate(w.width / 10, floor - w.height / 4, w.width - w.width / 5, 2, El.CONCRETE.id)
        // explosives
        w.plate(w.width / 2 - 6, floor - 6, 6, 6, El.C4.id)
        w.plate(w.width / 2 + 2, floor - 5, 4, 5, El.TNT.id)
        w.plate(w.width / 2 + 7, floor - 4, 4, 4, El.TNT.id)
        // fuse from the ceiling
        w.plate(w.width / 2 - 3, floor - w.height / 4, 1, w.height / 4 - 8, El.FUSE.id)
        w.plate(w.width / 2 - 3, floor - w.height / 4 - 1, 1, 1, El.FIRE.id)
        // props
        w.plate(w.width / 6, floor - 3, 5, 3, El.METAL.id)
        w.plate(w.width * 4 / 5, floor - 3, 5, 3, El.WOOD.id)
        w.plate(2, floor - 2, w.width / 8, 2, El.GUNPOWDER.id)
    }

    private fun iceCave(w: World) {
        ground(w, El.STONE.id)
        w.plate(0, 2, w.width, 3, El.ICE.id)
        w.plate(w.width / 8, w.height / 3, w.width / 3, w.height / 4, El.ICE.id)
        w.plate(w.width * 3 / 5, w.height / 3, w.width / 4, w.height / 5, El.ICE.id)
        w.plate(w.width / 5, w.height - w.height / 8 - 3, w.width / 2, 3, El.WATER.id)
        w.place(w.width / 12, w.height - w.height / 8 - 2, El.HEATER.id)
        w.place(w.width * 9 / 10, w.height - w.height / 8 - 2, El.LAVA_VENT.id)
        w.plate(w.width / 3, w.height - w.height / 8 - 1, 4, 1, El.SNOW.id)
    }

    private fun ocean(w: World) {
        w.plate(0, w.height * 2 / 3, w.width, w.height / 3, El.WATER.id)
        // sandy bottom
        for (x in 0 until w.width) {
            val h = (w.height / 10) + (w.height / 16) * kotlin.math.sin(x / 9.0).toInt() / 4
            w.plate(x, w.height - h, 1, h, El.SAND.id)
        }
        w.plate(w.width / 6, w.height - w.height / 4, w.width / 8, 2, El.SALT.id)
        w.plate(w.width / 10, w.height - 4, 6, 4, El.LAVA_VENT.id)
        w.plate(w.width / 2, w.height - w.height / 3, w.width / 5, 2, El.STONE.id)
        for (i in 0 until 6) {
            w.plate(w.width / 5 + i * w.width / 8, w.height - w.height / 6, 2, w.height / 8, El.PLANT.id)
        }
    }

    // ------------------------------------------------------------------ helpers --

    private fun ground(w: World, id: Int) {
        w.plate(0, w.height - w.height / 8, w.width, w.height / 8, id)
    }

    private fun tree(w: World, x: Int, base: Int, height: Int) {
        w.plate(x, base - height, 2, height, El.WOOD.id)
        val crown = height / 2
        for (dy in 0 until crown) {
            val half = (crown - dy) / 2 + 1
            w.plate(x - half + 1, base - height - crown + dy, half * 2, 1, El.PLANT.id)
        }
    }

    fun World.plate(x: Int, y: Int, width: Int, height: Int, id: Int) {
        for (yy in y until y + height) {
            for (xx in x until x + width) {
                if (inBounds(xx, yy)) place(xx, yy, id)
            }
        }
    }

    private fun World.hollowRect(x0: Int, y0: Int, x1: Int, y1: Int, thickness: Int, id: Int) {
        plate(x0, y0, x1 - x0, thickness, id)
        plate(x0, y1 - thickness, x1 - x0, thickness, id)
        plate(x0, y0, thickness, y1 - y0, id)
        plate(x1 - thickness, y0, thickness, y1 - y0, id)
    }
}

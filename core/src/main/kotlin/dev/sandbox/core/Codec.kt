package dev.sandbox.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Compact scene serialisation: run length encoded material/life/temperature triplets, gzip'ed.
 * A 100k cell scene without much structure is usually a few tens of kilobytes.
 */
object Codec {
    private const val MAGIC = 0x53425831 // SBX1
    private const val VERSION = 2

    /** Guards against hostile or corrupted files. */
    const val MAX_CELLS = 4_000_000

    /** A decoded scene: display name plus the grid. */
    data class Scene(val name: String, val world: World)

    fun encode(world: World, name: String = ""): ByteArray {
        val bos = ByteArrayOutputStream(64 * 1024)
        DataOutputStream(GZIPOutputStream(bos)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(name)
            out.writeInt(world.width)
            out.writeInt(world.height)
            var i = 0
            while (i < world.n) {
                val m = world.mat[i]
                val life = world.life[i]
                val t = world.temp[i]
                val f = world.flags[i]
                var run = 1
                while (i + run < world.n &&
                    world.mat[i + run] == m &&
                    world.life[i + run] == life &&
                    world.temp[i + run] == t &&
                    world.flags[i + run] == f
                ) {
                    run++
                }
                out.writeInt(run)
                out.writeInt(m)
                out.writeInt(life)
                out.writeFloat(t)
                out.writeByte(f.toInt())
                i += run
            }
        }
        return bos.toByteArray()
    }

    fun decode(bytes: ByteArray): Scene {
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { input ->
            require(input.readInt() == MAGIC) { "not a scene file" }
            val version = input.readInt()
            require(version == VERSION) { "unsupported scene version $version" }
            val name = input.readUTF()
            val width = input.readInt()
            val height = input.readInt()
            require(width in 4..8192 && height in 4..8192) { "bad scene size ${width}x$height" }
            require(width.toLong() * height <= MAX_CELLS) { "scene too large" }
            val world = World(width, height)
            var i = 0
            while (i < world.n) {
                val run = input.readInt()
                val m = input.readInt()
                val life = input.readInt()
                val t = input.readFloat()
                val f = input.readByte()
                require(run > 0 && i + run <= world.n) { "corrupted run" }
                require(m in 0 until El.ALL.size) { "unknown material $m" }
                for (k in i until i + run) {
                    world.mat[k] = m
                    world.life[k] = life
                    world.temp[k] = t
                    world.flags[k] = f
                }
                i += run
            }
            return Scene(name, world)
        }
    }
}

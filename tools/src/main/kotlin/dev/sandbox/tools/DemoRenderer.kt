package dev.sandbox.tools

import dev.sandbox.core.El
import dev.sandbox.core.Scenes
import dev.sandbox.core.World
import dev.sandbox.core.composeBackground
import dev.sandbox.core.paintPixels
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Headless renderer used to verify the engine and to produce the demo video without a device.
 *
 *   ./gradlew :tools:demo -PdemoArgs="--out demo-out --scene all --frames 600 --scale 2"
 *
 * Every selected scene is simulated for [frames] ticks; a PNG is written every [every] frames.
 */
fun main(args: Array<String>) {
    var out = "demo-out"
    var scene = "all"
    var frames = 600
    var width = 260
    var height = 400
    var every = 1
    var scale = 2
    var bench = false
    var benchSteps = 600

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--out" -> out = args[++i]
            "--scene" -> scene = args[++i]
            "--frames" -> frames = args[++i].toInt()
            "--width" -> width = args[++i].toInt()
            "--height" -> height = args[++i].toInt()
            "--every" -> every = args[++i].toInt()
            "--scale" -> scale = args[++i].toInt()
            "--bench" -> bench = true
            "--bench-steps" -> benchSteps = args[++i].toInt()
            else -> error("unknown argument ${args[i]}")
        }
        i++
    }

    if (bench) {
        benchmark(benchSteps)
        return
    }

    val outDir = File(out).apply { mkdirs() }
    val selection: List<Int> = when {
        scene == "all" -> Scenes.NAMES.indices.toList()
        scene.toIntOrNull() != null -> listOf(scene.toInt())
        else -> Scenes.NAMES.indexOf(scene).takeIf { it >= 0 }?.let { listOf(it) }
            ?: error("unknown scene '$scene'")
    }

    for (index in selection) {
        val name = Scenes.NAMES[index]
        // Scene names are localised, so non Latin names need a stable ASCII fallback to avoid
        // every scene writing its frames into the same directory.
        val slug = slugify(name).ifEmpty { "scene%02d".format(index) }
        val dir = File(outDir, "frames_$slug").apply { mkdirs() }
        val world = World(width, height)
        Scenes.apply(world, index)

        val composed = IntArray(world.n)
        var written = 0
        val start = System.nanoTime()
        var stepNanos = 0L
        val img = BufferedImage(world.width * scale, world.height * scale, BufferedImage.TYPE_INT_RGB)
        val scaled = IntArray(world.width * scale * world.height * scale)

        for (frame in 0 until frames) {
            val t0 = System.nanoTime()
            world.step()
            stepNanos += System.nanoTime() - t0
            if (frame % every == 0) {
                world.paintPixels()
                world.composeBackground(composed)
                upscale(composed, scaled, world.width, world.height, scale)
                img.setRGB(0, 0, img.width, img.height, scaled, 0, img.width)
                ImageIO.write(img, "png", File(dir, "frame_%04d.png".format(written)))
                written++
            }
        }
        val totalMs = (System.nanoTime() - start) / 1_000_000.0
        val stepMs = stepNanos / 1_000_000.0 / frames
        println(
            "scene %-18s cells=%dx%d particles=%-7d frames=%d step=%.2f ms  avg fps=%.0f  total=%.1f s  files=%d".format(
                slug, world.width, world.height, world.particleCount(), frames, stepMs,
                if (stepMs > 0) 1000.0 / stepMs else 0.0, totalMs / 1000.0, written,
            )
        )
    }
}

private fun upscale(src: IntArray, dst: IntArray, width: Int, height: Int, scale: Int) {
    if (scale == 1) {
        System.arraycopy(src, 0, dst, 0, src.size)
        return
    }
    val dw = width * scale
    var di = 0
    for (y in 0 until height) {
        val row = y * width
        for (s in 0 until scale) {
            for (x in 0 until width) {
                val c = src[row + x]
                for (t in 0 until scale) dst[di++] = c
            }
        }
    }
}

private fun slugify(name: String): String = name
    .lowercase()
    .replace(' ', '_')
    .replace(Regex("[^a-z0-9_]"), "")

/** Measures raw engine throughput on realistic content; used to guard against regressions. */
private fun benchmark(steps: Int) {
    val world = World(300, 460)
    Scenes.apply(world, 1)
    Scenes.apply(world, 2)
    world.paint(world.width / 2, 40, 30, El.WATER.id)
    world.paint(world.width / 3, 60, 24, El.SAND.id)
    world.paint(world.width * 2 / 3, 80, 18, El.LAVA.id)

    // warm up
    repeat(60) { world.step() }

    val times = DoubleArray(steps)
    var renderNanos = 0L
    for (s in 0 until steps) {
        val t0 = System.nanoTime()
        world.step()
        times[s] = (System.nanoTime() - t0) / 1_000_000.0
        val r0 = System.nanoTime()
        world.paintPixels()
        renderNanos += System.nanoTime() - r0
    }
    times.sort()
    val avg = times.average()
    println(
        "bench cells=${world.n} particles=${world.particleCount()} steps=$steps " +
            "step avg=%.2f ms p50=%.2f p95=%.2f max=%.2f  render=%.2f ms  budget(60fps)=16.7 ms"
                .format(avg, times[steps / 2], times[(steps * 95) / 100], times[steps - 1],
                    renderNanos / 1_000_000.0 / steps)
    )
    println("counts: " + topCounts(world))
}

private fun topCounts(world: World): String {
    val counts = world.counts()
    return counts.withIndex()
        .filter { it.value > 0 && it.index != 0 }
        .sortedByDescending { it.value }
        .take(8)
        .joinToString(", ") { "${El.ALL[it.index].name}=${it.value}" }
}

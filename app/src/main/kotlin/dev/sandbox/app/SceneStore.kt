package dev.sandbox.app

import android.content.Context
import dev.sandbox.core.Codec
import dev.sandbox.core.World
import java.io.File

data class SceneInfo(
    val name: String,
    val file: File,
    val bytes: Long,
    val modified: Long,
)

/** Stores player scenes as gzip'ed .sbx files inside the app private storage. */
class SceneStore(context: Context) {
    private val dir = File(context.filesDir, "scenes").apply { mkdirs() }

    fun list(): List<SceneInfo> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".sbx") } ?: return emptyList()
        return files.map { file ->
            val name = runCatching { Codec.decode(file.readBytes()).name }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            SceneInfo(name, file, file.length(), file.lastModified())
        }.sortedByDescending { it.modified }
    }

    fun save(name: String, world: World): SceneInfo {
        val clean = name.trim().ifEmpty { "Сцена" }
        val file = File(dir, "${System.currentTimeMillis()}_${sanitize(clean)}.sbx")
        file.writeBytes(Codec.encode(world, clean))
        return SceneInfo(clean, file, file.length(), file.lastModified())
    }

    fun load(info: SceneInfo): World = Codec.decode(info.file.readBytes()).world

    fun delete(info: SceneInfo): Boolean = info.file.delete()

    private fun sanitize(name: String): String =
        name.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(40)
}

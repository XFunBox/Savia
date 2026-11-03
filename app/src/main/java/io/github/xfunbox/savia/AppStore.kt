package io.github.xfunbox.savia

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.ByteArrayOutputStream

class AppStore(context: Context) {
    // Compartido entre Activities: una rotación no permite leer durante otra escritura.
    private companion object { val lock = Any() }
    private val file = AtomicFile(File(context.filesDir, "ahorro.json"))

    fun load(): AppData = synchronized(lock) {
        // AtomicFile.openRead also recovers an interrupted write from its backup.
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized AppData()
        file.openRead().use { input ->
            val buffer = ByteArray(8192)
            val result = ByteArrayOutputStream()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(result.size() + count <= 5 * 1024 * 1024) { "El archivo supera el tamaño permitido." }
                result.write(buffer, 0, count)
            }
            val bytes = result.toByteArray()
            Backup.decode(bytes.toString(Charsets.UTF_8))
        }
    }

    fun save(data: AppData): Unit = synchronized(lock) {
        val bytes = Backup.encode(data).toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    fun export(): String = synchronized(lock) { Backup.encode(load()) }
    fun import(text: String): AppData = synchronized(lock) { Backup.decode(text).also(::save) }
}

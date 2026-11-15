package dev.notcustom.app.core

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IRemoteProcess
import rikka.shizuku.Shizuku

class ShellException(message: String) : Exception(message)

object Shell {
    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val binder = Shizuku.getBinder()
            ?: throw ShellException("Shizuku is not running")
        val service = IShizukuService.Stub.asInterface(binder)
        var process: IRemoteProcess? = null
        try {
            process = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
                .bufferedReader()
                .use { it.readText() }
            val error = ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
                .bufferedReader()
                .use { it.readText() }
            val code = process.waitFor()
            if (code != 0) {
                throw ShellException(
                    error.trim().ifBlank { output.trim() }.ifBlank { "exit $code" }
                )
            }
            output.trim()
        } catch (t: Throwable) {
            if (t is ShellException) throw t
            throw ShellException(t.message ?: "command failed")
        } finally {
            runCatching { process?.destroy() }
        }
    }

    fun reset() {
    }
}

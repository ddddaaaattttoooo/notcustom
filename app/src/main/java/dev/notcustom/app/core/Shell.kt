package dev.notcustom.app.core

import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import moe.shizuku.server.IRemoteProcess
import rikka.shizuku.Shizuku

class ShellException(message: String, cause: Throwable? = null) : Exception(message, cause)

object Shell {
    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        var process: IRemoteProcess? = null
        try {
            val binder = Shizuku.getBinder()
                ?: throw ShellException("Shizuku is not running")
            val service = IShizukuService.Stub.asInterface(binder)
            val running = service.newProcess(arrayOf("sh", "-c", command), null, null)
            process = running
            coroutineScope {
                val outputDeferred = async { read(running.inputStream) }
                val errorDeferred = async { read(running.errorStream) }
                val code = running.waitFor()
                val output = outputDeferred.await()
                val error = errorDeferred.await()
                if (code != 0) {
                    throw ShellException(
                        error.trim().ifBlank { output.trim() }.ifBlank { "exit $code" }
                    )
                }
                output.trim()
            }
        } catch (t: Throwable) {
            throw when (t) {
                is ShellException -> t
                is DeadObjectException -> ShellException(
                    "Shizuku disconnected. Reopen Shizuku and try again.",
                    t
                )
                is RemoteException -> ShellException(
                    "Lost connection to Shizuku. Reopen Shizuku and try again.",
                    t
                )
                is SecurityException -> ShellException(
                    "NotCustom does not have Shizuku permission.",
                    t
                )
                else -> ShellException(t.message ?: "command failed", t)
            }
        } finally {
            runCatching { process?.destroy() }
        }
    }

    private fun read(descriptor: ParcelFileDescriptor): String =
        ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }

    fun reset() {
    }
}

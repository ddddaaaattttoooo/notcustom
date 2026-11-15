package dev.notcustom.app.core

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import dev.notcustom.app.BuildConfig
import dev.notcustom.app.IShellService
import dev.notcustom.app.ShellService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

class ShellException(message: String) : Exception(message)

object Shell {
    private val mutex = Mutex()
    private var service: IShellService? = null
    private var pending: CompletableDeferred<IShellService>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null) return
            val bound = IShellService.Stub.asInterface(binder)
            service = bound
            pending?.complete(bound)
            pending = null
            runCatching {
                binder?.linkToDeath({
                    service = null
                    pending = null
                }, 0)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            pending = null
        }
    }

    private fun args() = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private suspend fun service(): IShellService {
        service?.let { return it }
        val deferred = mutex.withLock {
            service?.let { return@withLock CompletableDeferred(it) }
            pending ?: CompletableDeferred<IShellService>().also { created ->
                pending = created
                try {
                    Shizuku.bindUserService(args(), connection)
                } catch (t: Throwable) {
                    pending = null
                    throw t
                }
            }
        }
        val bound = withTimeoutOrNull(15_000) { deferred.await() }
        if (bound == null) {
            pending = null
            throw ShellException("Shizuku service did not connect")
        }
        return bound
    }

    suspend fun exec(command: String): String {
        val bound = service()
        return withContext(Dispatchers.IO) {
            val result = try {
                bound.exec(command)
            } catch (t: Throwable) {
                throw ShellException(t.message ?: "command failed")
            }
            if (result.startsWith(ShellService.ERROR_PREFIX)) {
                throw ShellException(result.removePrefix(ShellService.ERROR_PREFIX))
            }
            result
        }
    }

    fun reset() {
        service = null
        pending?.cancel()
        pending = null
    }
}

package dev.notcustom.app.core

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

object ShizukuManager {
    const val PERMISSION_REQUEST_CODE = 4102
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val binderReceivedListeners = Collections.synchronizedMap(
        WeakHashMap<Shizuku.OnBinderReceivedListener, Shizuku.OnBinderReceivedListener>()
    )
    private val binderDeadListeners = Collections.synchronizedMap(
        WeakHashMap<Shizuku.OnBinderDeadListener, Shizuku.OnBinderDeadListener>()
    )
    private val permissionListeners = Collections.synchronizedMap(
        WeakHashMap<
            Shizuku.OnRequestPermissionResultListener,
            Shizuku.OnRequestPermissionResultListener
            >()
    )

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun requestPermission() {
        runCatching {
            if (isRunning() && !hasPermission()) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        }
    }

    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        val wrapper = synchronized(binderReceivedListeners) {
            binderReceivedListeners.getOrPut(listener) {
                WeakBinderReceivedListener(listener)
            }
        }
        runCatching { Shizuku.addBinderReceivedListener(wrapper) }
    }

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        val wrapper = synchronized(binderDeadListeners) {
            binderDeadListeners.getOrPut(listener) {
                WeakBinderDeadListener(listener)
            }
        }
        runCatching { Shizuku.addBinderDeadListener(wrapper) }
    }

    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        val wrapper = synchronized(binderReceivedListeners) {
            binderReceivedListeners.remove(listener)
        } ?: return
        runCatching { Shizuku.removeBinderReceivedListener(wrapper) }
    }

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        val wrapper = synchronized(binderDeadListeners) {
            binderDeadListeners.remove(listener)
        } ?: return
        runCatching { Shizuku.removeBinderDeadListener(wrapper) }
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        val wrapper = synchronized(permissionListeners) {
            permissionListeners.getOrPut(listener) {
                WeakPermissionListener(listener)
            }
        }
        runCatching { Shizuku.addRequestPermissionResultListener(wrapper) }
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        val wrapper = synchronized(permissionListeners) {
            permissionListeners.remove(listener)
        } ?: return
        runCatching { Shizuku.removeRequestPermissionResultListener(wrapper) }
    }

    private class WeakBinderReceivedListener(
        listener: Shizuku.OnBinderReceivedListener
    ) : Shizuku.OnBinderReceivedListener {
        private val reference = WeakReference(listener)
        override fun onBinderReceived() {
            reference.get()?.onBinderReceived()
        }
    }

    private class WeakBinderDeadListener(
        listener: Shizuku.OnBinderDeadListener
    ) : Shizuku.OnBinderDeadListener {
        private val reference = WeakReference(listener)
        override fun onBinderDead() {
            reference.get()?.onBinderDead()
        }
    }

    private class WeakPermissionListener(
        listener: Shizuku.OnRequestPermissionResultListener
    ) : Shizuku.OnRequestPermissionResultListener {
        private val reference = WeakReference(listener)
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            reference.get()?.onRequestPermissionResult(requestCode, grantResult)
        }
    }
}

package dev.notcustom.app.core

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuManager {
    const val PERMISSION_REQUEST_CODE = 4102
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

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
        if (isRunning() && !hasPermission()) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
    }

    fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) =
        Shizuku.addBinderReceivedListener(listener)

    fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) =
        Shizuku.addBinderDeadListener(listener)

    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) =
        Shizuku.removeBinderReceivedListener(listener)

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) =
        Shizuku.removeBinderDeadListener(listener)

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) =
        Shizuku.addRequestPermissionResultListener(listener)

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) =
        Shizuku.removeRequestPermissionResultListener(listener)
}

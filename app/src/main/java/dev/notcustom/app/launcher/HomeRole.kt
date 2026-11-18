package dev.notcustom.app.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

fun homeRoleIntent(context: Context): Intent? {
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
    return if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    } else {
        null
    }
}

fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

fun isDefaultHome(context: Context): Boolean =
    context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

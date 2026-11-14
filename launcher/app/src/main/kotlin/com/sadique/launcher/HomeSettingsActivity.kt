package com.sadique.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/** Opens Android's Home role picker or the firmware's Home settings page. */
open class HomeSettingsActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LauncherLocale.wrap(base))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // The original request still belongs to this activity after recreation.
        if (state != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canRequestHome(this)) {
            try {
                val roles = getSystemService(RoleManager::class.java)
                if (roles != null) {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
                    return
                }
            } catch (_: ActivityNotFoundException) {
                // Some TVs advertise the role even though there is no picker.
            } catch (_: SecurityException) {
                // The same TV can still let the user choose Home in Settings.
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show()
        } finally {
            finish()
        }
    }

    @Deprecated("Uses the Home role request started by this activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        finish()
    }

    companion object {
        @JvmStatic
        fun canRequestHome(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            val roles = context.getSystemService(RoleManager::class.java) ?: return false
            return roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)
        }

        @JvmStatic
        fun available(context: Context): Boolean {
            return canRequestHome(context) ||
                context.packageManager.resolveActivity(Intent(Settings.ACTION_HOME_SETTINGS), 0) != null
        }
    }
}

package com.sadique.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle

/** Handles market://launch?id=<package> (Android TV Remote "launch app" by package name), which the
 *  stock launcher normally serves: opens the app's TV launch intent, then the phone launch intent. */
open class AppLaunchActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        try {
            val data = intent.data
            val pkg = data?.getQueryParameter("id")
            if (pkg != null) {
                var launch = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                if (launch == null) launch = packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (ignored: ActivityNotFoundException) {
        } catch (ignored: SecurityException) {
        } finally {
            finish()
        }
    }
}

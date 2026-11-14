package com.sadique.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Routes com.android.tv.action.VIEW_INPUTS to Home's Inputs menu. MainActivity enables
 * this no-display component only while handle_view_inputs is configured.
 */
open class InputsRequestActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LauncherLocale.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_VIEW_INPUTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}

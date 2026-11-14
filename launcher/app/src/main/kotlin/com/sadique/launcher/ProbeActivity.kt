package com.sadique.launcher

import android.app.Activity
import android.os.Bundle

/**
 * Invisible activity opened by HomeKeyService at boot for stock_home_redirect.
 * Closing it sends a window event from the prior foreground activity so the service can
 * distinguish stock Home from an HDMI input.
 */
open class ProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.post {
            finish()
            overridePendingTransition(0, 0)
        }
    }
}

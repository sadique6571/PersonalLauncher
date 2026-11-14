package com.sadique.launcher

import android.util.Log

/**
 * The guard around the module card feature: every call from the home screen into [Cards]
 * goes through [run]. The first failure of any kind that is safe to catch (an exception, or
 * the class failing to load or initialise) is logged once and switches cards off for the rest of
 * the process, so Home keeps working exactly as with module_cards false. Out of memory and other
 * virtual machine errors are not caught.
 */
object CardFeature {

    fun interface Step<T> {
        @Throws(Exception::class)
        fun run(): T
    }

    fun interface Reporter {
        fun failed(t: Throwable)
    }

    private var broken = false
    /** Where the one failure line goes; tests replace it, since android.util.Log is not available there. */
    @JvmField
    var reporter: Reporter = Reporter { t -> Log.w(Cards.TAG, "module cards switched off for this session: $t") }

    /** True while the feature has not failed in this process. */
    @JvmStatic
    fun working(): Boolean = !broken

    /** Runs one step and returns its value, or the fallback once the feature is off or the step fails. */
    @JvmStatic
    fun <T> run(step: Step<T>, fallback: T): T {
        if (broken) return fallback
        return try {
            step.run()
        } catch (e: Exception) {
            handleError(e, fallback)
        } catch (e: LinkageError) {
            handleError(e, fallback)
        }
    }

    private fun <T> handleError(e: Throwable, fallback: T): T {
        broken = true
        try {
            reporter.failed(e)
        } catch (ignored: Exception) {
        }
        return fallback
    }

    /** For tests only: forget a failure. */
    @JvmStatic
    fun reset() {
        broken = false
    }
}

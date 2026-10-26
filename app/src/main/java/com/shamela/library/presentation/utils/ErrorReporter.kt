package com.shamela.library.presentation.utils

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Handled errors worth knowing about in production: logged, and sent to Crashlytics as non-fatal
 * so they show up there instead of only in the device's logcat.
 */
object ErrorReporter {
    fun report(tag: String, message: String, error: Throwable) {
        Log.e(tag, message, error)
        // No Firebase app (e.g. a build without a real google-services.json): logging is enough.
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                log("$tag: $message")
                recordException(error)
            }
        }
    }
}

package com.shamela.library.data.local.files

import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Creating folders in shared storage (the library lives in Android/media/<package>).
 *
 * Uninstalling the app deletes its Android/media folder beneath the shared-storage layer, and that
 * layer's cache can keep reporting the old folders as present: after a reinstall `mkdir` fails
 * with "exists" while the folder isn't there, until the phone restarts. Every download then failed
 * at the very end, when the book was moved into the library. [ensure] gets past such an entry.
 */
object StorageDirs {
    private const val TAG = "StorageDirs"

    /**
     * Makes [dir] and its parents exist; false if it still can't. A folder that `mkdir` refuses is
     * made under a temporary name and renamed into place (the cache accepts that); failing that,
     * the parent is rescanned with [context] and `mkdir` tried once more. Call off the main thread.
     */
    fun ensure(dir: File, context: Context? = null): Boolean {
        if (dir.isDirectory) return true
        val parent = dir.parentFile ?: return false
        if (!ensure(parent, context)) return false
        if (dir.mkdir() || dir.isDirectory) return true

        Log.w(TAG, "mkdir failed for ${dir.path}; trying around a stale entry")
        val temp = File(parent, "${dir.name}.new-${System.nanoTime()}")
        if (temp.mkdir()) {
            if (temp.renameTo(dir) && dir.isDirectory) return true
            temp.delete()
        }
        if (context != null) {
            rescan(context, parent)
            if (dir.mkdir() || dir.isDirectory) return true
        }
        Log.e(TAG, "can't create ${dir.path}")
        return false
    }

    private fun rescan(context: Context, folder: File) {
        val done = CountDownLatch(1)
        MediaScannerConnection.scanFile(context, arrayOf(folder.path), null) { _, _ -> done.countDown() }
        done.await(3, TimeUnit.SECONDS)
    }
}

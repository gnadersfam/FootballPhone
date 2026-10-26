package com.lounge.phone

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A small problem log. A problem on a background thread is written down and ignored so the app carries on. */
object Crash {
    private var app: Context? = null

    fun install(c: Context) { app = c.applicationContext }

    private fun file(): File? = app?.let { File(it.filesDir, "problems.log") }

    fun log(what: String, e: Throwable) {
        try {
            val f = file() ?: return
            if (f.exists() && f.length() > 60_000) f.writeText("")
            val stamp = SimpleDateFormat("d MMM HH:mm", Locale.UK).format(Date())
            f.appendText("[$stamp] $what: ${e.javaClass.simpleName}: ${e.message}\n")
        } catch (x: Throwable) {
            // nothing more to do
        }
    }

    fun text(): String = try { file()?.takeIf { it.exists() }?.readText() ?: "" } catch (e: Throwable) { "" }
    fun clear() { try { file()?.writeText("") } catch (e: Throwable) { } }
}

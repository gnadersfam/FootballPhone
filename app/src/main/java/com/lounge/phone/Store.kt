package com.lounge.phone

import android.content.Context
import java.io.File

/** Everything saved on the phone. The football-data.org key lives ONLY here, never in the code. */
object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("football", Context.MODE_PRIVATE)

    fun str(c: Context, key: String, def: String = ""): String = prefs(c).getString(key, def) ?: def
    fun putStr(c: Context, key: String, v: String) = prefs(c).edit().putString(key, v).apply()
    fun long(c: Context, key: String, def: Long = 0L): Long = prefs(c).getLong(key, def)
    fun putLong(c: Context, key: String, v: Long) = prefs(c).edit().putLong(key, v).apply()
    fun bool(c: Context, key: String, def: Boolean = false): Boolean = prefs(c).getBoolean(key, def)
    fun putBool(c: Context, key: String, v: Boolean) = prefs(c).edit().putBoolean(key, v).apply()

    fun fdoKey(c: Context) = str(c, "fdo_key")

    private fun file(c: Context, name: String) = File(c.filesDir, "$name.json")

    fun loadJson(c: Context, name: String): Any? {
        val f = file(c, name)
        if (!f.exists()) return null
        return try { Json.parseOrNull(f.readText()) } catch (e: Exception) { null }
    }

    fun saveJson(c: Context, name: String, v: Any?) {
        try {
            val tmp = File(c.filesDir, "$name.json.tmp")
            tmp.writeText(Json.write(v))
            tmp.renameTo(file(c, name))
        } catch (e: Exception) {
            // saving is best effort; the app keeps working from memory
        }
    }
}

class AndroidPersist(private val c: Context) : Persist {
    override fun load(name: String): Any? = Store.loadJson(c, name)
    override fun save(name: String, v: Any?) = Store.saveJson(c, name, v)
}

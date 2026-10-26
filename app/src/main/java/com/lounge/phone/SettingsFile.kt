package com.lounge.phone

import android.content.Context

/**
 * The settings file, same format as the PC app ("the-lounge-settings"): your football picks and player watch-list move between the
 * PC and the phone in one small file. It never holds the football-data.org key.
 */
object SettingsFile {
    const val FORMAT = "the-lounge-settings"

    fun export(c: Context): String {
        val io = Football.persist()
        val m = linkedMapOf<String, Any?>(
            "format" to FORMAT, "version" to 1, "exported" to Utc.iso(System.currentTimeMillis() / 1000), "app" to "Football Stats (phone)",
            "picks" to io.load(Picks.PICKS).obj(), "watch_list" to io.load(Picks.WATCH).arr()
        )
        return Json.write(m)
    }

    /** Returns a short sentence about what was done. Throws IllegalArgumentException with a plain message when the file is no good. */
    fun import(text: String, replace: Boolean): String {
        val data = Json.parseOrNull(text.removePrefix("﻿")).obj()
        if (data["format"].str() != FORMAT) throw IllegalArgumentException("That is not a Lounge settings file.")
        val io = Football.persist()
        var picksN = -1; var watchN = -1
        if (data.containsKey("picks")) {
            val theirs = data["picks"].obj().filter { it.value is Map<*, *> }
            val mine = io.load(Picks.PICKS).obj()
            val out = LinkedHashMap<String, Any?>()
            if (!replace) out.putAll(mine)
            for ((k, v) in theirs) {
                val old = out[k].obj()
                val nv = v.obj()
                if (!replace && old.containsKey("made") && nv.containsKey("made") && old["made"].str() > nv["made"].str()) continue
                out[k] = v
            }
            io.save(Picks.PICKS, out)
            picksN = out.size
        }
        if (data.containsKey("watch_list")) {
            val theirs = data["watch_list"].arr().filter { it is Map<*, *> && (it as Map<*, *>).containsKey("id") }
            val mine = io.load(Picks.WATCH).arr()
            val out = ArrayList<Any?>()
            if (!replace) out.addAll(mine)
            val have = out.map { it.obj()["id"].str() }.toMutableSet()
            for (t in theirs) {
                val id = t.obj()["id"].str()
                if (replace || have.add(id)) {
                    out.add(linkedMapOf("id" to id, "name" to t.obj()["name"].str(), "team" to t.obj()["team"].str()))
                }
            }
            io.save(Picks.WATCH, out)
            watchN = out.size
        }
        val bits = ArrayList<String>()
        if (picksN >= 0) bits.add("$picksN pick${if (picksN == 1) "" else "s"}")
        if (watchN >= 0) bits.add("$watchN watch-list player${if (watchN == 1) "" else "s"}")
        return if (bits.isEmpty()) "The file had no picks or watch-list in it." else "Loaded. You now have " + bits.joinToString(" and ") + "."
    }
}

package com.lounge.phone

import android.content.Context
import java.text.Normalizer

/**
 * Player photos, the same way the PC app finds them: the Premier League's own photo first (the official fantasy game has one for
 * every Premier League player), then ESPN's. The list from the Premier League is downloaded once, kept for 3 days and read in the
 * background so the screens never wait for it.
 */
object Faces {
    private const val FPL_DATA = "https://fantasy.premierleague.com/api/bootstrap-static/"
    private const val FPL_PHOTO = "https://resources.premierleague.com/premierleague/photos/players/110x140/p%s.png"
    private const val DAYS3 = 3 * 86400L

    private class P(val first: String, val second: String, val web: String, val code: String)

    @Volatile private var data: Map<String, List<P>>? = null
    private var app: Context? = null
    private var started = false
    private val codes = HashMap<String, String?>()

    fun init(c: Context) {
        if (started) return
        started = true
        app = c.applicationContext
        Thread { try { load() } catch (e: Throwable) { Crash.log("Player photo list", e) } }.start()
    }

    fun ready(): Boolean = data != null
    fun count(): Int = data?.values?.sumOf { it.size } ?: 0

    private fun clubKey(n: String): String {
        val k = TeamNames.norm(n).replace(" utd", " united")
        return if (k == "spurs") "tottenham" else k
    }

    private fun words(s: String): List<String> {
        val n = Normalizer.normalize(s, Normalizer.Form.NFKD).filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return n.lowercase().replace("-", " ").replace(Regex("[^a-z ]"), " ").split(" ").filter { it.isNotEmpty() }
    }

    @Synchronized
    private fun load() {
        if (data != null) return
        val c = app ?: return
        val saved = Store.loadJson(c, "fpl_cache").obj()
        val now = System.currentTimeMillis() / 1000
        var raw: Any? = null
        if (saved["d"] != null && now - saved["at"].long() < DAYS3) raw = saved["d"]
        else {
            try {
                val js = Net.json(FPL_DATA, timeoutMs = 40000).obj()
                val tn = HashMap<Int, String>()
                for (t in js["teams"].arr()) tn[t.obj()["id"].int()] = clubKey(t.obj()["name"].str())
                val d = LinkedHashMap<String, MutableList<Any?>>()
                for (e in js["elements"].arr()) {
                    val o = e.obj()
                    val k = tn[o["team"].int()] ?: continue
                    d.getOrPut(k) { ArrayList() }.add(listOf(o["first_name"].str(), o["second_name"].str(), o["web_name"].str(), o["code"].str()))
                }
                if (d.isNotEmpty()) { Store.saveJson(c, "fpl_cache", mapOf("at" to now, "d" to d)); raw = d }
            } catch (e: Throwable) {
                Crash.log("Player photo list download", e)
                raw = saved["d"]
            }
        }
        val out = LinkedHashMap<String, List<P>>()
        for ((k, v) in raw.obj()) out[k] = v.arr().map { val l = it.arr(); P(l[0].str(), l[1].str(), l[2].str(), l[3].str()) }
        if (out.isNotEmpty()) data = out
    }

    private fun codeFor(name: String, team: String): String? {
        val d = data ?: return null
        var k = clubKey(team)
        if (!d.containsKey(k)) {
            val c = d.keys.filter { it.startsWith("$k ") || k.startsWith("$it ") }
            if (c.size != 1) return null
            k = c[0]
        }
        val cands = d.getValue(k)
        val toks = words(name)
        if (toks.isEmpty()) return null
        val full = toks.joinToString(" ")
        for (e in cands) if (words(e.first + " " + e.second).joinToString(" ") == full) return e.code
        for (e in cands) if (words(e.web).joinToString(" ") == full) return e.code
        val last = toks.last()
        val m = cands.filter { (words(it.second).lastOrNull() ?: "") == last || (words(it.web).lastOrNull() ?: "") == last }
        if (m.size == 1) return m[0].code
        val m2 = m.filter { words(it.first).take(1) == toks.take(1) }
        if (m2.size == 1) return m2[0].code
        val m3 = cands.filter { toks.toSet().all { t -> t in words(it.first + " " + it.second + " " + it.web).toSet() } }
        return if (m3.size == 1) m3[0].code else null
    }

    /** Addresses to try for a player's picture, best first. Never waits for the network. */
    fun urls(id: String, name: String, team: String): List<String> {
        val out = ArrayList<String>()
        val key = "$team|$name"
        val code = synchronized(codes) {
            if (codes.containsKey(key)) codes[key] else {
                val cd = if (data != null) codeFor(name, team) else null
                if (data != null) codes[key] = cd
                cd
            }
        }
        if (code != null) out.add(FPL_PHOTO.format(code))
        val e = TeamLogos.faceUrl(id)
        if (e.isNotEmpty()) out.add(e)
        return out
    }
}

package com.lounge.phone

import android.content.Context
import java.util.Calendar
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Last season's player figures, for the "Last season" tab on the match page (same idea as the PC app's lastseason.py).
 * The first time, last season's match lines are downloaded from ESPN quietly in the background, saved as they arrive, and picked up
 * again where they stopped if the app is closed part way through. It never touches this season's data or the predictions.
 */
object LastSeason {
    private const val STORE = "events_prev"
    private const val WORKERS = 3
    private const val SLICE = 40

    class LsRow(val id: String, val pos: String, val st: Int, val fo: Int, val fd: Int, val sh: Int, val so: Int, val yc: Int, val rc: Int)
    class LsEv(val lk: String, val date: String, val h: String, val a: String, val hp: List<LsRow>, val ap: List<LsRow>)
    class LsPlayer(val id: String, val name: String, val apps: Int, val starts: Int, val yc: Int, val club: String, val pg: DoubleArray)

    private class Data(val season: String) {
        val events = LinkedHashMap<String, LsEv>()
        val names = HashMap<String, String>()
        val dates = LinkedHashSet<String>()
    }

    private var data: Data? = null
    private var players: Map<String, LsPlayer>? = null
    @Volatile var busy = false
        private set
    @Volatile var message = ""

    private fun season(): String {
        val c = Calendar.getInstance()
        return Leagues.seasonCode(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, 1)
    }

    private fun rowsTo(l: List<LsRow>): List<Any?> = l.map { listOf(it.id, it.pos, it.st, it.fo, it.fd, it.sh, it.so, it.yc, it.rc) }
    private fun rowsFrom(o: Any?): List<LsRow> = o.arr().map { r ->
        val x = r.arr()
        LsRow(x[0].str(), x[1].str(), x[2].int(), x[3].int(), x[4].int(), x[5].int(), x[6].int(), x[7].int(), x[8].int())
    }

    @Synchronized
    private fun load(c: Context): Data {
        val season = season()
        val d0 = data
        if (d0 != null && d0.season == season) return d0
        val d = Data(season)
        try {
            val o = Store.loadJson(c, STORE).obj()
            if (o["season"].str() == season) {
                for ((id, e) in o["events"].obj()) {
                    val m = e.obj()
                    d.events[id] = LsEv(m["lk"].str(), m["date"].str(), m["h"].str(), m["a"].str(), rowsFrom(m["hp"]), rowsFrom(m["ap"]))
                }
                for ((k, v) in o["names"].obj()) d.names[k] = v.str()
                d.dates.addAll(o["dates"].arr().map { it.str() })
            }
        } catch (e: Throwable) { Crash.log("Last season load", e) }
        data = d
        players = null
        return d
    }

    @Synchronized
    private fun save(c: Context, d: Data) {
        val ev = LinkedHashMap<String, Any?>()
        for ((id, e) in d.events) ev[id] = linkedMapOf("lk" to e.lk, "date" to e.date, "h" to e.h, "a" to e.a, "hp" to rowsTo(e.hp), "ap" to rowsTo(e.ap))
        Store.saveJson(c, STORE, linkedMapOf("season" to d.season, "events" to ev, "names" to d.names, "dates" to d.dates.toList()))
    }

    private fun wanted(prev: Map<String, List<Match>>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (lk in Leagues.ORDER) for (d in (prev[lk] ?: emptyList()).map { it.date }.toSortedSet()) out.add(lk to d)
        return out
    }

    /** Reads the saved figures into memory (call on a background thread) so the Last season tab opens quickly. */
    fun warm(c: Context) { try { players(c.applicationContext) } catch (e: Throwable) { Crash.log("Last season warm-up", e) } }

    /** (match days done, match days in total) */
    fun status(c: Context, prev: Map<String, List<Match>>): Pair<Int, Int> {
        val done = load(c).dates
        val want = wanted(prev)
        return Pair(want.count { "${it.first}|${it.second}" in done }, want.size)
    }

    fun ready(c: Context, prev: Map<String, List<Match>>): Boolean {
        val (n, t) = status(c, prev)
        return t > 0 && n >= t
    }

    private fun rows(side: SideLine?, d: Data): List<LsRow> {
        val out = ArrayList<LsRow>()
        for (p in side?.players ?: emptyList()) {
            if (!p.played) continue
            d.names[p.id] = p.name
            out.add(LsRow(p.id, p.pos, if (p.starter) 1 else 0, p.fouls, p.fouled, p.shots, p.sot, p.yc, p.rc))
        }
        return out
    }

    /** Starts the download on its own thread (when something is missing). Calls [progress] now and then, [done] at the end. */
    fun start(c: Context, prev: Map<String, List<Match>>, progress: (String) -> Unit, done: () -> Unit) {
        if (busy) return
        if (Leagues.ORDER.none { !(prev[it].isNullOrEmpty()) }) return
        val app = c.applicationContext
        if (ready(app, prev)) return
        busy = true
        Thread {
            try {
                val problems = download(app, prev, progress)
                val (n, t) = status(app, prev)
                message = if (n >= t) "ready" else "$n of $t match days so far (will carry on later)"
                if (problems > 0 && n < t) message += " - $problems problem${if (problems == 1) "" else "s"}"
            } catch (e: Throwable) {
                Crash.log("Last season download", e)
                message = "paused (will try again later)"
            }
            busy = false
            try { done() } catch (e: Throwable) { }
        }.start()
    }

    private fun download(c: Context, prev: Map<String, List<Match>>, progress: (String) -> Unit): Int {
        val d = load(c)
        val tns = Leagues.ORDER.associateWith { lk -> TeamNames((prev[lk] ?: emptyList()).flatMap { listOf(it.home, it.away) }.toSet()) }
        val want = wanted(prev)
        val jobs = want.filter { "${it.first}|${it.second}" !in d.dates }
        if (jobs.isEmpty()) return 0
        var problems = 0
        var i = 0
        while (i < jobs.size) {
            val part = jobs.subList(i, Math.min(i + SLICE, jobs.size))
            i += SLICE
            val pool = Executors.newFixedThreadPool(WORKERS)
            try {
                val boards = pool.invokeAll(part.map { job ->
                    Callable<Pair<Pair<String, String>, Any>> {
                        try { job to (Sources.espnScoreboard(job.first, job.second.replace("-", "")) as Any) }
                        catch (e: Throwable) { job to (e as Any) }
                    }
                }).map { it.get() }
                class Item(val lk: String, val day: String, val ev: Sources.Scoreboard)
                val todo = ArrayList<Item>()
                val good = ArrayList<Pair<String, String>>()
                for ((job, res) in boards) {
                    if (res is Throwable) { problems++; continue }
                    good.add(job)
                    @Suppress("UNCHECKED_CAST")
                    for (ev in res as List<Sources.Scoreboard>) if (ev.completed && ev.id !in d.events) todo.add(Item(job.first, job.second, ev))
                }
                val failed = HashSet<Pair<String, String>>()
                val sums = pool.invokeAll(todo.map { it ->
                    Callable<Pair<Item, Any>> {
                        try { it to (Sources.espnSummary(it.lk, it.ev.id) as Any) } catch (e: Throwable) { it to (e as Any) }
                    }
                }).map { it.get() }
                for ((item, res) in sums) {
                    if (res is Throwable) { problems++; failed.add(item.lk to item.day); continue }
                    val s = res as Summary
                    val tn = tns.getValue(item.lk)
                    val hRaw = s.homeSide?.team ?: ""; val aRaw = s.awaySide?.team ?: ""
                    synchronized(this) {
                        d.events[item.ev.id] = LsEv(item.lk, item.day, tn.canon(hRaw) ?: hRaw, tn.canon(aRaw) ?: aRaw, rows(s.homeSide, d), rows(s.awaySide, d))
                    }
                }
                for (job in good) if (job !in failed) d.dates.add("${job.first}|${job.second}")
            } finally { pool.shutdown() }
            save(c, d)
            synchronized(this) { players = null }
            progress("Last season's player stats: ${d.dates.size} of ${want.size} match days")
        }
        return problems
    }

    /** player id -> last season's figures: apps, yellows, main club and per game averages (yellows, fouls, fouled, shots, on target). */
    @Synchronized
    fun players(c: Context): Map<String, LsPlayer> {
        players?.let { return it }
        val d = load(c)
        class Acc(val id: String) {
            var apps = 0; var starts = 0; var yc = 0; var fo = 0; var fd = 0; var sh = 0; var so = 0
            val clubs = HashMap<String, Int>()
        }
        val acc = LinkedHashMap<String, Acc>()
        for (ev in d.events.values) {
            for ((team, list) in listOf(ev.h to ev.hp, ev.a to ev.ap)) for (r in list) {
                val a = acc.getOrPut(r.id) { Acc(r.id) }
                a.apps++; a.starts += r.st; a.yc += r.yc; a.fo += r.fo; a.fd += r.fd; a.sh += r.sh; a.so += r.so
                a.clubs[team] = (a.clubs[team] ?: 0) + 1
            }
        }
        val out = LinkedHashMap<String, LsPlayer>()
        for ((id, a) in acc) {
            val n = a.apps.toDouble()
            out[id] = LsPlayer(id, d.names[id] ?: "?", a.apps, a.starts, a.yc, a.clubs.maxByOrNull { it.value }?.key ?: "",
                doubleArrayOf(a.yc / n, a.fo / n, a.fd / n, a.sh / n, a.so / n))
        }
        players = out
        return out
    }
}

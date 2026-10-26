package com.lounge.phone

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.LinearLayout

/** "Check my setup": one place to see whether everything the app depends on is in order. Nothing here changes any data. */
class SetupPage(act: MainActivity) : ScrollPage(act) {
    private class Line(val colour: Int, val title: String, val detail: String)

    private var online: List<Line>? = null
    private var checking = false

    private fun ageText(ts: Long): String {
        val d = Math.max(0L, System.currentTimeMillis() / 1000 - ts)
        if (d < 90) return "just now"
        for ((size, word) in listOf(86400L to "day", 3600L to "hour", 60L to "minute")) if (d >= size) {
            val n = d / size
            return "$n $word${if (n == 1L) "" else "s"} ago"
        }
        return "just now"
    }

    private fun lines(): List<Line> {
        val out = ArrayList<Line>()
        val st = Football.state
        val now = System.currentTimeMillis() / 1000
        // key
        val key = Store.fdoKey(act)
        out.add(if (key.isEmpty()) Line(Skin.AMBER, "football-data.org key", "No key saved. Only the next few days of fixtures are shown. Paste your free key in Settings.")
        else Line(Skin.GREEN, "football-data.org key", "A key is saved (it gives the whole season's fixtures)."))
        if (st == null || !st.hasData) {
            out.add(Line(Skin.RED, "Football data", "No data yet. Press Refresh now."))
            return out
        }
        // freshness
        val u = Utc.parse(st.lastUpdated())
        out.add(if (u == null) Line(Skin.AMBER, "Last refresh", "Never refreshed.")
        else {
            val age = now - u
            Line(if (age < 3 * 3600) Skin.GREEN else if (age < 2 * 86400) Skin.AMBER else Skin.RED, "Last refresh", ageText(u) + (if (age >= 2 * 86400) ". The app refreshes itself when you open it." else ""))
        })
        @Suppress("UNCHECKED_CAST")
        val src = (st.meta["sources"] as? Map<String, Any?>) ?: emptyMap()
        for ((k, label) in listOf("results" to "Results, xG, cards and referees (football-data.co.uk)", "fixtures" to "Upcoming fixtures (football-data.org)", "players" to "Player stats and lineups (ESPN)")) {
            val t = Utc.parse(src[k].str())
            out.add(if (t == null) Line(Skin.AMBER, label, "Not downloaded yet.") else Line(Skin.GREEN, label, "Updated ${ageText(t)}."))
        }
        val nRes = st.results.values.sumOf { it.size }
        val nFx = st.fixtures.values.sumOf { it.size }
        out.add(Line(if (nRes > 0) Skin.GREEN else Skin.RED, "Matches held", "$nRes results this season, $nFx upcoming fixtures, ${st.events.size} matches with player lines."))
        val (done, total) = LastSeason.status(act, st.prev)
        out.add(when {
            total == 0 -> Line(Skin.AMBER, "Last season's player stats", "Last season's results are not downloaded yet.")
            done >= total -> Line(Skin.GREEN, "Last season's player stats", "Ready ($total match days).")
            else -> Line(Skin.AMBER, "Last season's player stats", "Downloading in the background: $done of $total match days. It carries on by itself.")
        })
        out.add(if (TeamLogos.count() > 0) Line(Skin.GREEN, "Club badges", "${TeamLogos.count()} clubs known.") else Line(Skin.AMBER, "Club badges", "The badge list has not downloaded yet (needs the internet). Initials show until it does."))
        out.add(if (Faces.ready()) Line(Skin.GREEN, "Player photos", "Premier League photo list ready (${Faces.count()} players); ESPN photos are the back-up.") else Line(Skin.AMBER, "Player photos", "The Premier League photo list is not loaded yet; ESPN photos are used meanwhile."))
        val w = st.meta["warnings"].arr().map { it.str() }.filter { !it.startsWith("No football-data.org key") }
        out.add(if (w.isEmpty()) Line(Skin.GREEN, "Last refresh warnings", "None.") else Line(Skin.AMBER, "Last refresh warnings", w.take(4).joinToString("\n")))
        val problems = Crash.text()
        out.add(if (problems.isEmpty()) Line(Skin.GREEN, "Problem log", "Nothing noted.") else Line(Skin.AMBER, "Problem log", "${problems.lines().count { it.isNotBlank() }} line(s) noted (shown below)."))
        return out
    }

    private fun runOnline() {
        if (checking) return
        checking = true
        online = null
        refresh()
        val key = Store.fdoKey(act)
        Bg.run {
            val res = ArrayList<Line>()
            val c = java.util.Calendar.getInstance()
            val season = Leagues.seasonCode(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1)
            fun test(label: String, f: () -> String) {
                res.add(try { Line(Skin.GREEN, label, f()) } catch (x: Throwable) { Line(Skin.RED, label, "Could not reach it: ${x.message}") })
            }
            test("football-data.co.uk (results)") { val t = Net.text("${Sources.FD_CO_UK}/mmz4281/$season/E0.csv"); "OK (${t.lines().size} lines)" }
            if (key.isEmpty()) res.add(Line(Skin.AMBER, "football-data.org (fixtures)", "No key to test with."))
            else test("football-data.org (fixtures)") {
                val js = Net.json("${Sources.FDO}/competitions/PL/matches?status=SCHEDULED", mapOf("X-Auth-Token" to key)).obj()
                "Key works (${js["matches"].arr().size} scheduled matches)"
            }
            test("ESPN (scores and lineups)") { val js = Net.json("${Sources.ESPN}/eng.1/scoreboard").obj(); "OK (${js["events"].arr().size} matches today)" }
            test("Premier League photos list") { Net.bytes("https://fantasy.premierleague.com/api/bootstrap-static/", timeoutMs = 40000); "OK" }
            test("Premier League referee feed") { Net.bytes("${Refresh.PL_API}/v1/competitions/8/seasons/${2000 + season.substring(0, 2).toInt()}/matchweeks/1/matches"); "OK" }
            Bg.ui { online = res; checking = false; refresh() }
        }
    }

    override fun build() {
        col.addView(Ui.h1(act, "Check my setup"))
        col.addView(Ui.note(act, "Green = fine, amber = worth a look, red = needs attention. Nothing here changes your data."))
        val all = lines()
        for (l in all) addLine(l)
        col.addView(Ui.h2(act, "Test the internet sources"))
        col.addView(Ui.note(act, "Tries each website the app uses. It takes a few seconds."))
        col.addView(Ui.button(act, if (checking) "Checking..." else "Run the test", !checking) { runOnline() }, Ui.lp(-2, -2, 4, 8, act))
        online?.let { for (l in it) addLine(l) }
        val problems = Crash.text()
        if (problems.isNotEmpty()) {
            col.addView(Ui.h2(act, "Problems noted"))
            col.addView(Ui.note(act, problems.takeLast(1500)))
        }
        val row = LinearLayout(act)
        row.addView(Ui.button(act, "Copy this report") {
            val text = (all + (online ?: emptyList())).joinToString("\n") { "${it.title}: ${it.detail}" } + (if (problems.isNotEmpty()) "\n\nProblems:\n" + problems.takeLast(1500) else "")
            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Football Stats report", text))
            act.toast("Copied")
        })
        if (problems.isNotEmpty()) row.addView(Ui.button(act, "Clear problem log") { Crash.clear(); refresh() }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(act, 8) })
        col.addView(row, Ui.lp(-2, -2, 12, 0, act))
    }

    private fun addLine(l: Line) {
        val r = LinearLayout(act)
        r.orientation = LinearLayout.VERTICAL
        r.background = Ui.box(act, Skin.PANEL, Skin.LINE, 10)
        r.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8))
        r.addView(Ui.text(act, "●  ${l.title}", 14f, l.colour, true))
        r.addView(Ui.text(act, l.detail, 12.5f, Skin.TEXT))
        col.addView(r, Ui.lp(-1, -2, 0, 6, act))
    }
}

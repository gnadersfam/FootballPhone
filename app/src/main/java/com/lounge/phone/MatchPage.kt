package com.lounge.phone

import android.app.AlertDialog
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The full match page, same as the PC app's match window: predictions, likeliest score, "Close match", bookmaker odds and whether
 * the model agrees, referee, form, suspension warnings, your pick, and every player's chances (Chances / Per game / Last season),
 * or head to head, form and xG trends. The numbers come from the same engine as the PC app.
 */
class MatchPage(act: MainActivity, private val lk: String, private val fx0: Fixture) : ScrollPage(act) {
    private var mode = "players"            // players / insights
    private var tabMode = "chances"         // chances / pergame / last
    private var teamSel = "Both"
    private var grp = "All"
    private var query = ""
    private var starters = false
    private var sortCol = -1
    private var desc = true

    private var eng: Engine? = null
    private var mv: MatchView? = null
    private var rows: List<PL> = emptyList()
    private var tableHolder: LinearLayout? = null
    private var sortHolder: LinearLayout? = null
    private var noteView: TextView? = null

    private class PL(val r: PRow, val extra: Boolean, val banned: Boolean, val sheet: String, val code: String, val role: String)

    private fun cur(): Fixture = Football.state?.fixtures?.get(lk)?.firstOrNull { it.same(fx0) } ?: fx0

    override fun build() {
        val e = engineOrNote() ?: return
        val f = cur()
        if (eng !== e || mv == null) {
            eng = e
            mv = try { e.matchView(lk, f) } catch (x: Throwable) { Crash.log("Match page", x); null }
            rows = mv?.let { players(e, it, f) } ?: emptyList()
        }
        val m = mv
        if (m == null) { col.addView(Ui.note(act, "This match could not be worked out. Press Refresh now.", Skin.AMBER)); return }
        val p = m.pred
        val picks = Football.picks()

        // title
        val top = LinearLayout(act)
        top.gravity = Gravity.CENTER_VERTICAL
        val px = Ui.dp(act, 44)
        top.addView(Crest.badge(act, f.home, 44), LinearLayout.LayoutParams(px, px).apply { rightMargin = Ui.dp(act, 10) })
        top.addView(Ui.text(act, "${f.home} v ${f.away}", 19f, Skin.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(Crest.badge(act, f.away, 44), LinearLayout.LayoutParams(px, px).apply { leftMargin = Ui.dp(act, 10) })
        col.addView(top)
        col.addView(Ui.note(act, "${leagueName(lk)}  -  ${Skin.dayTime(ko(f))}", Skin.MUTED))

        // probability bar
        col.addView(ResultBar(act, doubleArrayOf(p.home, p.draw, p.away), intArrayOf(Skin.HOME, Skin.DRAW, Skin.AWAY)), Ui.lp(-1, Ui.dp(act, 14), 6, 4, act))
        val chances = LinearLayout(act)
        chances.addView(Ui.text(act, "${f.home} ${Ui.pct(p.home)}", 13f, Skin.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        chances.addView(Ui.text(act, "Draw ${Ui.pct(p.draw)}", 13f, Skin.TEXT, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        chances.addView(Ui.text(act, "${f.away} ${Ui.pct(p.away)}", 13f, Skin.TEXT, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(chances)

        col.addView(Ui.text(act, "Expected goals %.2f - %.2f".format(p.lh, p.la), 13f, Skin.TEXT, true).apply { setPadding(0, Ui.dp(act, 8), 0, 0) })
        col.addView(Ui.text(act, "Likely score ${p.scoreH}-${p.scoreA} (${Ui.pct(p.scoreP)})     Both teams to score ${Ui.pct(p.btts)}     Over 2.5 goals ${Ui.pct(p.over25)}", 13f, Skin.TEXT, true))
        col.addView(Ui.note(act, if (p.mkt) "Model blended with bookmaker odds" else "Model only"))
        Model.tightText(p, f.home, f.away).let { if (it.isNotEmpty()) col.addView(Ui.text(act, it, 13f, Skin.AMBER, true)) }

        // referee (tap for their card)
        val ref = m.ref
        val lg = Football.engine?.refs?.league
        val refText = when {
            m.refName.isNotEmpty() && ref != null && lg != null -> {
                val lgYc = lg.getValue("yc")
                val vs = if (lgYc != 0.0) (ref.yc / lgYc - 1.0) * 100 else 0.0
                "Referee: %s  -  %.1f yellows a game (%+d%% vs average %.1f), %.2f reds, %.1f fouls, %d games.  Home sides %.1f, away sides %.1f yellows.".format(
                    ref.name, ref.yc, Math.round(vs).toInt(), lgYc, ref.rc, ref.fouls, ref.n, ref.hyc, ref.ayc)
            }
            m.refName.isNotEmpty() -> "Referee: ${m.refName}  -  no card history found, so no referee adjustment"
            else -> "Referee: not announced yet (no referee adjustment)"
        }
        col.addView(Ui.text(act, refText, 13f, if (m.refName.isNotEmpty()) Skin.ACCENT else Skin.TEXT).apply {
            setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 2))
            if (m.refName.isNotEmpty()) { paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG; setOnClickListener { act.openReferee(m.refName) } }
        })

        // bookmaker odds and whether the model agrees
        val od = f.odds
        if (od != null && od.size >= 3) {
            val imp = doubleArrayOf(1.0 / od[0], 1.0 / od[1], 1.0 / od[2])
            val tot = imp.sum()
            col.addView(Ui.note(act, "Bookmaker odds: home %.2f  draw %.2f  away %.2f  (they imply %s / %s / %s)".format(od[0], od[1], od[2],
                Ui.pct(imp[0] / tot), Ui.pct(imp[1] / tot), Ui.pct(imp[2] / tot)), Skin.TEXT))
            col.addView(Ui.note(act, "Model alone: ${Ui.pct(p.pModel[0])} / ${Ui.pct(p.pModel[1])} / ${Ui.pct(p.pModel[2])}" +
                (f.o25?.let { "     Over 2.5 %.2f  Under 2.5 %.2f".format(it[0], it[1]) } ?: ""), Skin.TEXT))
            agreeSentence(m, f)?.let { col.addView(Ui.note(act, it, Ui.agreeColor(m.agree))) }
        } else col.addView(Ui.note(act, "Bookmaker odds: not available yet (only the next few days of matches have them)"))

        // recent form
        for ((side, team) in listOf("home" to f.home, "away" to f.away)) {
            val fr = m.form.getValue(side)
            val line = LinearLayout(act)
            line.gravity = Gravity.CENTER_VERTICAL
            line.setPadding(0, Ui.dp(act, 8), 0, 0)
            line.addView(Ui.text(act, team, 13f, Skin.TEXT, true).apply { setPadding(0, 0, Ui.dp(act, 8), 0) })
            for (fres in fr) line.addView(Ui.text(act, fres.res, 12f, 0xFF10101A.toInt(), true).apply {
                gravity = Gravity.CENTER
                background = Ui.box(act, Ui.resColor(fres.res), Ui.resColor(fres.res), 5, 0)
            }, LinearLayout.LayoutParams(Ui.dp(act, 22), Ui.dp(act, 22)).apply { rightMargin = Ui.dp(act, 4) })
            if (fr.isEmpty()) line.addView(Ui.text(act, "no results yet", 12f, Skin.MUTED))
            col.addView(line)
            if (fr.isNotEmpty()) col.addView(Ui.text(act, fr.joinToString(", ") { "${it.venue} ${it.opp} ${it.score}" }, 11.5f, Skin.MUTED))
        }

        col.addView(Ui.text(act, "Lineups: ${m.lineupStatus}", 13f, if (m.lineupStatus.startsWith("Confirmed")) Skin.GREEN else Skin.MUTED, true).apply { setPadding(0, Ui.dp(act, 8), 0, 0) })

        val sl = suspLines(m, f)
        if (sl.isNotEmpty()) {
            col.addView(Ui.text(act, "Cards and suspensions (league matches this season only)", 12f, Skin.RED, true).apply { setPadding(0, Ui.dp(act, 8), 0, 2) })
            for (l in sl) col.addView(Ui.text(act, l, 12.5f, Skin.RED))
        }

        // your pick
        val started = ko(f) <= nowSec()
        val chosen = picks.getPick(lk, f)
        col.addView(Ui.text(act, "Your pick", 14f, Skin.TEXT, true).apply { setPadding(0, Ui.dp(act, 14), 0, Ui.dp(act, 2)) })
        col.addView(Ui.chips(act,
            Triple("Home", chosen == "H") { setPick(picks, f, "H", p, started) },
            Triple("Draw", chosen == "D") { setPick(picks, f, "D", p, started) },
            Triple("Away", chosen == "A") { setPick(picks, f, "A", p, started) },
            Triple("Clear", false) { if (!started) { picks.clearPick(lk, f); refresh() } else act.toast("Picks close at kick-off") }))
        val pickMsg = if (chosen != null) "Saved: " + mapOf("H" to f.home, "D" to "a draw", "A" to f.away).getValue(chosen)
            else if (started) "Already kicked off - picks close at kick-off" else ""
        if (pickMsg.isNotEmpty()) col.addView(Ui.note(act, pickMsg))
        col.addView(Ui.button(act, "Refresh this match") {
            act.toast("Checking team sheets, referee and odds...")
            Football.refreshMatch(lk, f) { msg -> act.toast(msg); mv = null; refresh() }
        }, Ui.lp(-2, -2, 6, 0, act))

        // two views of the same match
        col.addView(Ui.chips(act,
            Triple("Players", mode == "players") { mode = "players"; refresh() },
            Triple("Head to head, form and xG trends", mode == "insights") { mode = "insights"; refresh() }), Ui.lp(-1, -2, 12, 0, act))
        if (mode == "insights") insights(e, f) else playersView(e, m, f)
    }

    private fun setPick(picks: Picks, f: Fixture, code: String, p: Pred, started: Boolean) {
        if (started) { act.toast("Picks close at kick-off"); return }
        picks.setPick(lk, f, code, p, nowSec())
        refresh()
    }

    private fun agreeSentence(m: MatchView, f: Fixture): String? {
        val ag = m.agree ?: return null
        val nm = listOf(f.home, "a draw", f.away)
        if (ag.code == "disagree")
            return "Model vs bookmakers: DISAGREE.  Model alone picks ${nm[ag.model]} (${Ui.pct(ag.pm[ag.model])}); the bookmakers pick ${nm[ag.market]} (${Ui.pct(ag.mk[ag.market])}).  " +
                "On past matches the odds beat the model, so treat this as a reason for caution about the model's pick."
        val extra = when (ag.code) {
            "agree" -> "about the same confidence"
            "more" -> "the model is more confident than the bookmakers"
            else -> "the model is less confident than the bookmakers"
        }
        return "Model vs bookmakers: both pick ${nm[ag.model]} (model alone ${Ui.pct(ag.pm[ag.model])}, bookmakers ${Ui.pct(ag.mk[ag.market])}) - $extra."
    }

    private fun suspLines(m: MatchView, f: Fixture): List<String> {
        val out = ArrayList<String>()
        for (code in listOf("out", "risk")) for (side in listOf("home", "away"))
            for (r in m.susp[side] ?: emptyList()) if (r.code == code) out.add("${r.name} (${if (side == "home") f.home else f.away}): ${r.text}")
        return out
    }

    // ------------------------------------------------------------------ head to head, form and xG trends
    private fun insights(e: Engine, f: Fixture) {
        val hh = e.h2h(lk, f.home, f.away, 6)
        col.addView(Ui.h2(act, "Head to head"))
        if (hh.rows.isEmpty()) col.addView(Ui.note(act, "No earlier meetings in the data the app holds (this and last season)."))
        else {
            val n = hh.rows.size
            col.addView(Ui.note(act, "Last $n meeting${if (n == 1) "" else "s"}:  ${f.home} ${hh.hw} win${if (hh.hw == 1) "" else "s"}   Draws ${hh.d}   ${f.away} ${hh.aw} win${if (hh.aw == 1) "" else "s"}   -   %.1f goals a game".format(hh.avgGoals), Skin.TEXT))
            val heads = listOf(Hd("Match", 190, Gravity.START), Hd("Date", 84), Hd("Score", 52), Hd("xG", 84), Hd("Yellows", 64))
            col.addView(PTable.build(act, heads, hh.rows.map { r ->
                listOf(Cv("${r.home} v ${r.away}", bold = true), Cv(r.date, Skin.MUTED), Cv(r.score, Skin.ACCENT, true), Cv(r.xg, Skin.MUTED), Cv(r.yc))
            }, fixed = 1))
        }
        col.addView(Ui.h2(act, "Form table: last 6 league matches"))
        col.addView(Ui.note(act, "Top 3, then both teams (highlighted). The Stats tab has the whole Form table."))
        col.addView(FormTables.table(act, e, lk, 6, setOf(f.home, f.away), true))
        col.addView(Ui.h2(act, "xG over the last 10 matches"))
        for (team in listOf(f.home, f.away)) {
            val t = e.trend(lk, team, 10)
            val chart = TrendChart(act).apply {
                title = "$team: xG over last ${t.rows.size} matches"
                xg = t.rows.map { it.xg }; xga = t.rows.map { it.xga }
                opps = t.rows.map { it.opp.take(3).uppercase() + (if (it.venue == "H") " H" else " A") }
                avgF = t.avgF; avgA = t.avgA
            }
            col.addView(chart, LinearLayout.LayoutParams(-1, Ui.dp(act, 190)).apply { topMargin = Ui.dp(act, 6) })
            col.addView(Ui.note(act, t.verdict))
        }
        col.addView(Ui.note(act, "Dashed lines are the season averages."))
    }

    // ------------------------------------------------------------------ players
    private fun players(e: Engine, m: MatchView, f: Fixture): List<PL> {
        fun sheet(r: PRow, extra: Boolean, banned: Boolean): Pair<String, String> {
            if (extra) return Pair(if (banned) "Suspended" else "Not in squad", "N")
            if (m.lineup.side(r.side).confirmed) return if (r.role == "Starter") Pair("Starting", "S") else Pair("Bench", "B")
            return Pair(if (r.role == "Starter") "Predicted start" else "Predicted bench", "")
        }
        val order = mapOf("Starter" to 0, "Sub" to 1, "Out" to 2)
        val main = m.players.sortedWith(compareBy({ it.side != "home" }, { order[it.role] ?: 2 }, { -it.booked }))
        val out = ArrayList<PL>()
        for (r in main) { val s = sheet(r, false, false); out.add(PL(r, false, false, s.first, s.second, r.role)) }
        // once a side's team sheet is out: this season's players of that club who are in neither the eleven nor the bench
        val extras = ArrayList<Pair<Double, PL>>()
        for (side in listOf("home", "away")) {
            val ln = m.lineup.side(side)
            if (!ln.confirmed) continue
            val team = if (side == "home") f.home else f.away
            val ids = (ln.starters.map { it.id } + ln.bench.map { it.id }).toSet()
            for (a in e.players.values) {
                if (a.team != team || a.id in ids) continue
                val apps = a.start.apps + a.sub.apps
                if (apps < 1) continue
                val yc = a.start.s[S_YC] + a.sub.s[S_YC]
                val r = PRow(a.id, a.name, team, side, a.pos, a.group, "Out", 0.0, apps, 0.0, listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0, 0.0), listOf(0.0, 0.0))
                r.ycSeason = yc
                val banned = a.id in ln.out
                val s = sheet(r, true, banned)
                extras.add(Pair(yc / apps.toDouble(), PL(r, true, banned, s.first, s.second, "Out")))
            }
        }
        val ex = extras.sortedWith(compareBy({ it.second.r.side != "home" }, { -it.first }, { it.second.r.name }))
        for (x in ex) out.add(x.second)
        return out
    }

    private fun matches(pl: PL): Boolean {
        val r = pl.r
        if (teamSel != "Both" && r.team != teamSel) return false
        val g = when (grp) { "Goalkeepers" -> "G"; "Defenders" -> "D"; "Midfielders" -> "M"; "Forwards" -> "F"; else -> null }
        if (g != null && r.group != g) return false
        if (starters && (pl.role == "Sub" || pl.role == "Out")) return false
        if (query.isNotEmpty() && !r.name.lowercase().contains(query.lowercase())) return false
        return true
    }

    private fun playersView(e: Engine, m: MatchView, f: Fixture) {
        if (m.players.isEmpty() && rows.isEmpty()) {
            col.addView(Ui.note(act, "No player data yet (no team sheets or earlier lineups to base the player chances on). Press Refresh now.", Skin.TEXT))
            return
        }
        col.addView(Ui.chips(act,
            Triple("Chances", tabMode == "chances") { tabMode = "chances"; sortCol = -1; refresh() },
            Triple("Per game", tabMode == "pergame") { tabMode = "pergame"; sortCol = -1; refresh() },
            Triple("Last season", tabMode == "last") { tabMode = "last"; sortCol = -1; refresh() }))
        col.addView(Ui.text(act, "Team", 11f, Skin.MUTED).apply { setPadding(0, Ui.dp(act, 6), 0, 0) })
        col.addView(Ui.chips(act,
            Triple("Both", teamSel == "Both") { teamSel = "Both"; refresh() },
            Triple(f.home, teamSel == f.home) { teamSel = f.home; refresh() },
            Triple(f.away, teamSel == f.away) { teamSel = f.away; refresh() }))
        col.addView(Ui.text(act, "Position", 11f, Skin.MUTED))
        col.addView(Ui.chips(act, *listOf("All", "Goalkeepers", "Defenders", "Midfielders", "Forwards").map { g ->
            Triple(g, grp == g) { grp = g; refresh() }
        }.toTypedArray()))
        val bar = LinearLayout(act)
        bar.gravity = Gravity.CENTER_VERTICAL
        val box = EditText(act).apply {
            hint = "Search player"
            setText(query)
            setTextColor(Skin.TEXT); setHintTextColor(Skin.MUTED)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            background = Ui.box(act, Skin.PANEL, Skin.LINE, 16)
            setPadding(Ui.dp(act, 12), Ui.dp(act, 6), Ui.dp(act, 12), Ui.dp(act, 6))
        }
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim() ?: ""; fillTable() }
        })
        bar.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = Ui.dp(act, 8) })
        bar.addView(Ui.chip(act, "Starters only", starters) { starters = !starters; refresh() })
        col.addView(bar, Ui.lp(-1, -2, 6, 4, act))

        col.addView(Ui.text(act, "Sort (tap again to reverse; you can also tap a column heading)", 11f, Skin.MUTED))
        sortHolder = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(sortHolder)
        col.addView(Ui.note(act, "Tap a player for their card and to add them to your watch-list (marked ★).", Skin.MUTED))
        tableHolder = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tableHolder)
        noteView = Ui.note(act, "")
        col.addView(noteView)
        fillTable()
    }

    // column numbers (in the full list of columns) that the sort chips jump to, for each tab
    private fun sortCols(): List<Pair<String, Int>> = when (tabMode) {
        "pergame" -> listOf("Booked" to 7, "Fouls" to 8, "Fouled" to 9, "Shots" to 10, "On target" to 11)
        "last" -> listOf("Booked" to 8, "Fouls" to 9, "Fouled" to 10, "Shots" to 11, "On target" to 12)
        else -> listOf("Booked" to 7, "Fouls" to 8, "Fouled" to 11, "Shots" to 14, "On target" to 17)
    }

    private fun sortBy(c: Int, textCol: Boolean) {
        if (c == sortCol) desc = !desc else { sortCol = c; desc = !textCol }
        fillTable()
    }

    private fun sheetPill(code: String): IntArray? {
        if (Skin.t.key == "classic") return null
        val col = when (code) { "S" -> Skin.GREEN; "B" -> Skin.AMBER; "N" -> Skin.RED; else -> return null }
        return intArrayOf(col, Skin.mix(Skin.PANEL2, col, 0.42f))
    }

    private fun perGame(e: Engine, id: String): DoubleArray? {
        val a = e.players[id] ?: return null
        val g = a.start.apps + a.sub.apps
        if (g == 0) return null
        return intArrayOf(S_YC, S_FOULS, S_FOULED, S_SHOTS, S_SOT).map { (a.start.s[it] + a.sub.s[it]).toDouble() / g }.toDoubleArray()
    }

    private fun fillTable() {
        val th = tableHolder ?: return
        val e = eng ?: return
        val m = mv ?: return
        th.removeAllViews()
        sortHolder?.let { sh ->
            sh.removeAllViews()
            sh.addView(Ui.chips(act, *sortCols().map { (label, c) ->
                Triple(label + (if (sortCol == c) (if (desc) " ▼" else " ▲") else ""), sortCol == c) { sortBy(c, false) }
            }.toTypedArray()))
        }
        val shown = rows.filter { matches(it) }
        val picks = Football.picks()
        val watch = picks.loadWatch().map { it.id }.toSet()
        val means: DoubleArray
        val heads: List<Hd>
        val cells: (PL) -> List<Cv>
        val keys: (PL) -> List<Comparable<*>>
        val note: String
        val allP = m.players
        when (tabMode) {
            "pergame" -> {
                heads = listOf(Hd("Player", 176, Gravity.START), Hd("Team", 100, Gravity.START), Hd("Pos", 46), Hd("Role", 98, Gravity.START), Hd("Team sheet", 110), Hd("Apps", 44),
                    Hd("Yellows", 100), Hd("Yellows / game", 98), Hd("Fouls / game", 90), Hd("Fouled / game", 98), Hd("Shots / game", 92), Hd("On target / game", 108))
                val pg = HashMap<String, DoubleArray?>()
                for (pl in shown) pg[pl.r.id] = perGame(e, pl.r.id)
                val have = shown.filter { !it.extra }.mapNotNull { pg[it.r.id] }
                means = DoubleArray(5) { k -> if (have.isEmpty()) 0.0 else have.sumOf { it[k] } / have.size }
                cells = { pl -> common(pl, watch) + (pg[pl.r.id]?.let { v -> v.mapIndexed { k, x -> Cv("%.2f".format(x), pill = if (pl.extra) null else Skin.relPill(x, means[k])) } } ?: List(5) { Cv("-") }) }
                keys = { pl -> commonKeys(pl) + (pg[pl.r.id]?.toList() ?: List(5) { 0.0 }) }
                note = "Per game = what the player has actually done this season in league matches, divided by his appearances (coming on as a sub counts as one). " +
                    "These are real averages, not model estimates, so they do not change with the opponent or the referee. Green / red mark clearly above / below the average of the players listed."
            }
            "last" -> {
                heads = listOf(Hd("Player", 176, Gravity.START), Hd("Team", 100, Gravity.START), Hd("Pos", 46), Hd("Role", 98, Gravity.START), Hd("Team sheet", 110),
                    Hd("Last season's club", 130, Gravity.START), Hd("Apps", 44), Hd("Yellows", 62), Hd("Yellows / game", 98), Hd("Fouls / game", 90), Hd("Fouled / game", 98),
                    Hd("Shots / game", 92), Hd("On target / game", 108))
                val ls = LastSeason.players(act)
                val have = shown.filter { !it.extra }.mapNotNull { ls[it.r.id] }
                means = DoubleArray(5) { k -> if (have.isEmpty()) 0.0 else have.sumOf { it.pg[k] } / have.size }
                cells = { pl ->
                    val a = ls[pl.r.id]
                    val head = listOf(playerCell(pl, watch), Cv(pl.r.team, crest = pl.r.team), Cv(pl.r.pos.ifEmpty { "-" }), Cv(if (pl.extra) "-" else roleText(pl), Skin.MUTED),
                        Cv(pl.sheet, pill = sheetPill(pl.code)))
                    if (a != null) head + listOf(Cv(a.club), Cv("${a.apps}"), Cv("${a.yc}")) + a.pg.mapIndexed { k, x -> Cv("%.2f".format(x), pill = if (pl.extra) null else Skin.relPill(x, means[k])) }
                    else head + List(8) { Cv("-") }
                }
                keys = { pl ->
                    val a = ls[pl.r.id]
                    commonKeys(pl).take(5) + (if (a != null) listOf<Comparable<*>>(a.club, a.apps, a.yc) + a.pg.toList() else listOf<Comparable<*>>("-", 0, 0) + List(5) { 0.0 })
                }
                val (done, total) = LastSeason.status(act, Football.state?.prev ?: emptyMap())
                note = if (total == 0) "Last season's results have not been downloaded yet. Press Refresh now first."
                else if (done < total) "Last season's player stats are still being downloaded in the background ($done of $total match days so far). The app does this once, then keeps them. Come back to this tab in a few minutes."
                else "Last season = what the player actually did per game in the league games he played last season (Premier League and Championship), divided by his appearances. Real averages, not model estimates. " +
                    "Apps and Yellows are last season's totals; the club is where he played most of his games. Role and Team sheet are for this match. Green / red mark clearly above / below the average of the players listed. A dash means he has no Premier League or Championship games from last season."
            }
            else -> {
                heads = listOf(Hd("Player", 176, Gravity.START), Hd("Team", 100, Gravity.START), Hd("Pos", 46), Hd("Role", 98, Gravity.START), Hd("Team sheet", 110), Hd("Apps", 44),
                    Hd("Yellows", 100), Hd("Booked", 64), Hd("Foul 1+", 62), Hd("Foul 2+", 62), Hd("Foul 3+", 62), Hd("Fouled 1+", 70), Hd("Fouled 2+", 70), Hd("Fouled 3+", 70),
                    Hd("Shots 1+", 66), Hd("Shots 2+", 66), Hd("Shots 3+", 66), Hd("On target 1+", 84), Hd("On target 2+", 84))
                fun vals(r: PRow) = listOf(r.booked) + r.fouls + r.fouled + r.shots + r.sot
                means = DoubleArray(12) { k -> if (allP.isEmpty()) 0.0 else allP.sumOf { vals(it)[k] } / allP.size }
                cells = { pl ->
                    common(pl, watch) + (if (pl.extra) List(12) { Cv("-") } else vals(pl.r).mapIndexed { k, v -> Cv(Ui.pct(v), pill = Skin.relPill(v, means[k])) })
                }
                keys = { pl -> commonKeys(pl) + (if (pl.extra) List(12) { 0.0 } else vals(pl.r)) }
                note = "Percentages are model estimates from season rates, the opponent, the likely matchup and the referee. They are not bookmaker prices. The % in brackets is the chance the player plays at all. " +
                    "With a predicted lineup it comes from the last few matches (nobody is certain to start); once team sheets are out, starters count as certain and subs are weighted by how often they have come on recently. " +
                    "Yellows are this season's league cards; a player who is suspended is left out of a predicted lineup. Team sheet: green Starting, yellow Bench, red Not in squad (or Suspended) once the team sheets are out; grey is only the app's guess. Press Refresh this match to update it."
            }
        }
        var list = shown
        if (sortCol in heads.indices) {
            val c = sortCol
            @Suppress("UNCHECKED_CAST")
            val cmp = Comparator<PL> { a, b -> (keys(a)[c] as Comparable<Any>).compareTo(keys(b)[c] as Any) }
            list = shown.sortedWith(if (desc) cmp.reversed() else cmp)
        }
        val table = PTable.build(act, heads, list.map(cells), fixed = 1, sortCol = sortCol, sortDesc = desc,
            onSort = { c -> sortBy(c, c in 0..4) }, onRow = { i -> playerDialog(list[i], watch) })
        th.addView(table)
        if (list.isEmpty()) th.addView(Ui.note(act, "No players match these choices.", Skin.TEXT))
        noteView?.text = note
    }

    private fun roleText(pl: PL): String = if (pl.r.plays >= 0.995) pl.r.role else "${pl.r.role} (${Ui.pct(pl.r.plays)})"

    private fun playerCell(pl: PL, watch: Set<String>): Cv {
        val r = pl.r
        val colr = if (r.flag == "SUSPENDED" || pl.banned) Skin.RED else if (r.flag.isNotEmpty()) Skin.AMBER else if (pl.extra) Skin.MUTED else Skin.TEXT
        return Cv((if (r.id in watch) "★ " else "") + r.name, colr, true, faceId = r.id, faceName = r.name, faceTeam = r.team)
    }

    private fun common(pl: PL, watch: Set<String>): List<Cv> {
        val r = pl.r
        val role = if (pl.extra) "-" else roleText(pl)
        val ycs = "${r.ycSeason}" + (if (r.flag.isNotEmpty()) "  ${r.flag}" else "")
        return listOf(playerCell(pl, watch), Cv(r.team, crest = r.team), Cv(r.pos.ifEmpty { "-" }), Cv(role, Skin.MUTED), Cv(pl.sheet, pill = sheetPill(pl.code)),
            Cv("${r.apps}"), Cv(ycs, if (r.flag.isNotEmpty()) Skin.AMBER else Skin.TEXT))
    }

    /** What each column sorts on: name, team, position, role, team sheet, apps, yellows (then the figures). */
    private fun commonKeys(pl: PL): List<Comparable<*>> {
        val r = pl.r
        val order = when (pl.role) { "Starter" -> 0; "Sub" -> 1; else -> 2 }
        return listOf(r.name, r.team, r.pos.ifEmpty { "-" }, order, pl.sheet, r.apps, r.ycSeason)
    }

    private fun playerDialog(pl: PL, watch: Set<String>) {
        val r = pl.r
        val on = r.id in watch
        val sb = StringBuilder()
        sb.append(r.team).append("   ").append(r.pos.ifEmpty { "" }).append("\n")
        sb.append(pl.sheet).append(if (pl.extra) "" else "   (plays: ${Ui.pct(r.plays)})").append("\n")
        if (!pl.extra) {
            sb.append("\nBooked ${Ui.pct(r.booked)}\n")
            sb.append("Fouls 1+/2+/3+  ${r.fouls.joinToString(" / ") { Ui.pct(it) }}\n")
            sb.append("Fouled 1+/2+/3+  ${r.fouled.joinToString(" / ") { Ui.pct(it) }}\n")
            sb.append("Shots 1+/2+/3+  ${r.shots.joinToString(" / ") { Ui.pct(it) }}\n")
            sb.append("On target 1+/2+  ${r.sot.joinToString(" / ") { Ui.pct(it) }}\n")
        }
        if (r.flag.isNotEmpty()) sb.append("\n${r.flag}\n")
        AlertDialog.Builder(act).setTitle(r.name).setMessage(sb.toString())
            .setPositiveButton(if (on) "Remove from watch-list" else "Add to watch-list") { _, _ ->
                val now = Football.picks().toggleWatch(r.id, r.name, r.team)
                act.toast(if (now) "${r.name} added to your watch-list" else "${r.name} removed from your watch-list")
                fillTable()
            }
            .setNeutralButton("Player card") { _, _ -> act.openPlayer(r.id) }
            .setNegativeButton("Close", null).show()
    }
}

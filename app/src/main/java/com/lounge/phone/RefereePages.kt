package com.lounge.phone

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

private fun spanText(which: String) = when (which) { "this" -> "this season"; "last" -> "last season"; else -> "this and last season" }

/** The Referees tab: every referee with their card figures. Green = gives more bookings than average, red = fewer. Tap a name for their card. */
class RefereesPage(act: MainActivity) : ScrollPage(act) {
    private var which = Store.str(act, "ref_which", "both")
    private var sortCol = 1
    private var desc = true

    private class R(val r: RefStat, val vs: Double)

    override fun build() {
        col.addView(Ui.h1(act, "Referees"))
        col.addView(Ui.chips(act,
            Triple("Both seasons", which == "both") { which = "both"; Store.putStr(act, "ref_which", which); refresh() },
            Triple("This season", which == "this") { which = "this"; Store.putStr(act, "ref_which", which); refresh() },
            Triple("Last season", which == "last") { which = "last"; Store.putStr(act, "ref_which", which); refresh() }))
        val e = engineOrNote() ?: return
        val data = e.refsTabs[which] ?: e.refs
        val lg = data.league
        col.addView(Ui.note(act, "League average: %.2f yellows, %.2f reds, %.1f fouls per game (both leagues, %s). Averages from only a few games can swing a lot, so check the Games column.".format(
            lg.getValue("yc"), lg.getValue("rc"), lg.getValue("fouls"), spanText(which))))
        col.addView(Ui.note(act, "Green = gives MORE bookings than average, red = FEWER. Tap a heading to sort, tap a referee for their card and last 5 games.", Skin.TEXT))
        val list = data.refs.values.map { R(it, if (lg.getValue("yc") != 0.0) it.yc / lg.getValue("yc") - 1.0 else 0.0) }
        if (list.isEmpty()) { col.addView(Ui.note(act, "No referee figures yet. Press Refresh now.", Skin.TEXT)); return }
        val sorted = when (sortCol) {
            0 -> list.sortedBy { it.r.name }
            1 -> list.sortedBy { it.r.n }
            2 -> list.sortedBy { it.r.yc }
            3 -> list.sortedBy { it.r.rc }
            4 -> list.sortedBy { it.r.fouls }
            5 -> list.sortedBy { it.vs }
            6 -> list.sortedBy { it.r.hyc }
            else -> list.sortedBy { it.r.ayc }
        }.let { if (desc) it.reversed() else it }
        val heads = listOf(Hd("Referee", 140, Gravity.START), Hd("Games", 56), Hd("Yellows / game", 84), Hd("Reds / game", 76), Hd("Fouls / game", 76),
            Hd("Cards vs average", 90), Hd("Home yellows", 78), Hd("Away yellows", 78))
        val rows = sorted.map { x ->
            val r = x.r
            val pill = if (Math.abs(x.vs) >= 0.05) Skin.pill(x.vs > 0, (Math.abs(x.vs) - 0.05) / 0.25) else null
            listOf(Cv(r.name, bold = true), Cv("${r.n}"), Cv("%.2f".format(r.yc)), Cv("%.2f".format(r.rc)), Cv("%.1f".format(r.fouls)),
                Cv("%+d%%".format(Math.round(x.vs * 100).toInt()), pill = pill), Cv("%.2f".format(r.hyc)), Cv("%.2f".format(r.ayc)))
        }
        col.addView(PTable.build(act, heads, rows, fixed = 1, sortCol = sortCol, sortDesc = desc,
            onSort = { c -> if (c == sortCol) desc = !desc else { sortCol = c; desc = c != 0 }; refresh() },
            onRow = { i -> act.openReferee(sorted[i].r.name) }), Ui.lp(-1, -2, 6, 0, act))

        // referees named for upcoming matches (the Premier League publishes them about 4 days ahead)
        col.addView(Ui.h2(act, "Referees for the next matches"))
        val now = nowSec()
        val up = ArrayList<Triple<Long, String, Fixture>>()
        for (l in Leagues.ORDER) for (fx in (Football.state?.fixtures?.get(l) ?: emptyList<Fixture>())) {
            val k = ko(fx)
            if (k == 0L || k < now - 3 * 3600 || k > now + 10 * 86400) continue
            up.add(Triple(k, l, fx))
        }
        up.sortBy { it.first }
        if (up.isEmpty()) col.addView(Ui.note(act, "No matches in the next 10 days."))
        for ((k, l, fx) in up.take(30)) {
            val row = LinearLayout(act)
            row.orientation = LinearLayout.VERTICAL
            row.background = Ui.box(act, Skin.PANEL, Skin.LINE, 10)
            row.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8))
            row.addView(Ui.text(act, "${fx.home} v ${fx.away}", 14f, Skin.TEXT, true))
            row.addView(Ui.text(act, Skin.dayTime(k) + "  -  " + leagueName(l), 12f, Skin.MUTED))
            if (fx.ref.isEmpty()) row.addView(Ui.text(act, "Referee not announced yet", 12f, Skin.MUTED))
            else row.addView(Ui.refLink(act, fx.ref, 13f, "Referee: "))
            row.setOnClickListener { act.openMatch(l, fx) }
            col.addView(row, Ui.lp(-1, -2, 0, 6, act))
        }
    }
}

/** One referee's card: the same figures as the Referees tab for just them, and their last 5 games. */
class RefereeCard(act: MainActivity, private val name: String) : ScrollPage(act) {
    private var which = "both"

    override fun build() {
        col.addView(Ui.h1(act, name))
        col.addView(Ui.chips(act,
            Triple("Both seasons", which == "both") { which = "both"; refresh() },
            Triple("This season", which == "this") { which = "this"; refresh() },
            Triple("Last season", which == "last") { which = "last"; refresh() }))
        val e = engineOrNote() ?: return
        val data = e.refsTabs[which] ?: e.refs
        val lg = data.league
        val r = data.refs[Model.refKey(name)]
        if (r == null) {
            col.addView(Ui.note(act, "No games found for $name in ${spanText(which)}.", Skin.TEXT))
            return
        }
        val grid = LinearLayout(act)
        grid.orientation = LinearLayout.VERTICAL
        val cells = listOf("Games" to "${r.n}", "Yellows / game" to "%.2f".format(r.yc), "Reds / game" to "%.2f".format(r.rc),
            "Fouls / game" to "%.1f".format(r.fouls), "Home yellows" to "%.2f".format(r.hyc), "Away yellows" to "%.2f".format(r.ayc))
        for (i in cells.indices step 3) {
            val line = LinearLayout(act)
            for (k in i until Math.min(i + 3, cells.size)) {
                val cell = LinearLayout(act)
                cell.orientation = LinearLayout.VERTICAL
                cell.addView(Ui.text(act, cells[k].first, 12f, Skin.MUTED))
                cell.addView(Ui.text(act, cells[k].second, 20f, Skin.TEXT, true))
                line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
            grid.addView(line, Ui.lp(-1, -2, 8, 0, act))
        }
        col.addView(grid)
        val lgYc = lg.getValue("yc")
        val vs = if (lgYc != 0.0) r.yc / lgYc - 1.0 else 0.0
        val colour = if (vs >= 0.05) Skin.GREEN else if (vs <= -0.05) Skin.RED else Skin.MUTED
        col.addView(Ui.note(act, "Cards vs average: %+d%%   (league average %.2f yellows, %.2f reds, %.1f fouls a game, %s)".format(
            Math.round(vs * 100).toInt(), lgYc, lg.getValue("rc"), lg.getValue("fouls"), spanText(which)), colour).apply { setPadding(0, Ui.dp(act, 8), 0, 0) })
        col.addView(Ui.note(act, "Green = gives more bookings than average, red = fewer.", Skin.MUTED))
        val last = r.matches.take(5)
        col.addView(Ui.h2(act, "Last ${last.size} game${if (last.size == 1) "" else "s"} (of ${r.n})"))
        fun g(v: Int?) = v?.toString() ?: "-"
        val heads = listOf(Hd("Match", 190, Gravity.START), Hd("Date", 84), Hd("Score", 54), Hd("Yellows (H-A)", 92), Hd("Reds (H-A)", 80), Hd("Fouls (H-A)", 82))
        val rows = last.map { m ->
            listOf(Cv("${m.home} v ${m.away}", bold = true), Cv(m.date, Skin.MUTED), Cv("${m.hg}-${m.ag}", Skin.ACCENT, true),
                Cv("${g(m.hy)}-${g(m.ay)}"), Cv("${g(m.hr)}-${g(m.ar)}"), Cv("${g(m.hf)}-${g(m.af)}"))
        }
        col.addView(PTable.build(act, heads, rows, fixed = 1), Ui.lp(-1, -2, 4, 0, act))
    }
}

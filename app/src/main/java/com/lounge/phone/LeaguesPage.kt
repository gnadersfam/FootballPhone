package com.lounge.phone

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Leagues tab: Premier League and Championship (table, upcoming matches) and the Match Day view. Tap a team for its log, a match for the full page. */
class LeaguesPage(act: MainActivity) : ScrollPage(act) {
    private var lk = Store.str(act, "lg_league", "PL")
    private var mode = Store.str(act, "lg_view", "table")      // table / up / day
    private var dayMode = Store.str(act, "lg_day", "Today")

    private fun set(l: String, v: String) {
        lk = l; mode = v
        Store.putStr(act, "lg_league", l); Store.putStr(act, "lg_view", v)
        refresh()
    }

    override fun build() {
        col.addView(Ui.h1(act, "Leagues"))
        col.addView(Ui.chips(act,
            Triple("Premier League", mode != "day" && lk == "PL") { set("PL", if (mode == "day") "table" else mode) },
            Triple("Championship", mode != "day" && lk == "ELC") { set("ELC", if (mode == "day") "table" else mode) },
            Triple("Match Day", mode == "day") { set(lk, "day") }))
        if (mode != "day") {
            col.addView(Ui.chips(act,
                Triple("Table", mode == "table") { set(lk, "table") },
                Triple("Upcoming matches", mode == "up") { set(lk, "up") }))
        }
        val e = engineOrNote() ?: return
        when (mode) {
            "table" -> table(e)
            "up" -> upcoming(e)
            else -> matchDay(e)
        }
    }

    private fun table(e: Engine) {
        val t = e.league.getValue(lk).table
        if (t.isEmpty()) { col.addView(Ui.note(act, "No results yet for this season.")); return }
        val heads = listOf(Hd("#", 32), Hd("Team", 140, Gravity.START), Hd("P", 38), Hd("W", 38), Hd("D", 38), Hd("L", 38), Hd("GF", 42), Hd("GA", 42),
            Hd("GD", 46), Hd("Pts", 46), Hd("xGF", 52), Hd("xGA", 52), Hd("xGD", 54))
        val rows = t.map { r ->
            listOf(Cv("${r.pos}", Skin.MUTED), Cv(r.team, bold = true, crest = r.team), Cv("${r.P}"), Cv("${r.W}"), Cv("${r.D}"), Cv("${r.L}"),
                Cv("${r.GF}"), Cv("${r.GA}"), Cv(Ui.sign(r.GD.toDouble(), 0)), Cv("${r.Pts}", Skin.ACCENT, true),
                Cv("%.1f".format(r.xGF), Skin.MUTED), Cv("%.1f".format(r.xGA), Skin.MUTED), Cv(Ui.sign(r.xGD), Skin.MUTED))
        }
        col.addView(PTable.build(act, heads, rows, fixed = 2, onRow = { i -> act.openTeam(lk, t[i].team) }), Ui.lp(-1, -2, 6, 0, act))
        col.addView(Ui.note(act, "Tap a team for its match log. Slide the table sideways for more columns. xG columns are expected goals for, against and the difference."))
    }

    private fun upcoming(e: Engine) {
        val fxs = e.fixtures(lk)
        if (fxs.isEmpty()) col.addView(Ui.note(act, "No upcoming matches saved. Press Refresh now (a football-data.org key in Settings shows the whole season)."))
        val limit = nowSec() - 3 * 3600
        for (fv in fxs.filter { ko(it.fx) >= limit }.take(40))
            col.addView(FxRow.card(act, fv, false) { act.openMatch(fv.league, fv.fx) }, Ui.lp(-1, -2, 6, 0, act))
        col.addView(Ui.note(act, "Chances are home / draw / away. Tap a match for the full page. Odds are only known for the next few days."))
    }

    private fun matchDay(e: Engine) {
        col.addView(Ui.chips(act, *listOf("Today", "Next 3 days", "Next 7 days", "All").map { m ->
            Triple(m, dayMode == m) { dayMode = m; Store.putStr(act, "lg_day", m); refresh() }
        }.toTypedArray()))
        val now = nowSec()
        val limit: Long? = when (dayMode) {
            "Today" -> java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis / 1000
            "Next 3 days" -> now + 3 * 86400
            "Next 7 days" -> now + 7 * 86400
            else -> null
        }
        val rows = ArrayList<Pair<Long, FixtureView>>()
        for (l in Leagues.ORDER) for (fv in e.fixtures(l)) {
            val k = ko(fv.fx)
            if (k == 0L || k < now - 3 * 3600) continue
            if (limit != null && k > limit) continue
            rows.add(k to fv)
        }
        rows.sortBy { it.first }
        if (rows.isEmpty()) col.addView(Ui.note(act, "No matches in this time. Try a longer choice above.", Skin.TEXT))
        for ((_, fv) in rows.take(60)) col.addView(FxRow.card(act, fv, true) { act.openMatch(fv.league, fv.fx) }, Ui.lp(-1, -2, 6, 0, act))
        col.addView(Ui.note(act, "Tap a match for the predictions, odds, referee, form and every player's chances."))
    }
}

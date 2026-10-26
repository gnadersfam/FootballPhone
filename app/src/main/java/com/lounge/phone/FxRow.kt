package com.lounge.phone

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One upcoming match as a tappable card (used by the league's Upcoming list and the Match Day view). Same numbers as the PC app. */
object FxRow {
    fun pickName(p: Pred, home: String, away: String): String {
        val v = doubleArrayOf(p.home, p.draw, p.away)
        var i = 0
        for (k in 1..2) if (v[k] > v[i]) i = k
        return listOf(home, "Draw", away)[i] + " " + Ui.pct(v[i])
    }

    /** "Confirmed", "Partly confirmed" or "Predicted" for a fixture's team sheets. */
    fun lineupConf(st: FootState, lk: String, fx: Fixture): String {
        val up = st.upcoming[fx.key(lk)] ?: return "Predicted"
        val n = listOf("home", "away").count { sd -> (up.side(sd)?.players?.count { it.starter } ?: 0) == 11 }
        return if (n == 2) "Confirmed" else if (n == 1) "Partly confirmed" else "Predicted"
    }

    fun card(act: MainActivity, fv: FixtureView, showLeague: Boolean, onOpen: () -> Unit): View {
        val c = act
        val fx = fv.fx
        val p = fv.pred
        val ko = Utc.parse(fx.utc) ?: 0L
        val card = Ui.card(c)
        val top = LinearLayout(c)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Ui.text(c, SimpleDateFormat("EEE d MMM HH:mm", Locale.UK).format(Date(ko * 1000)) +
            (if (showLeague) "  -  " + Leagues.ALL.getValue(fv.league).name else ""), 13f, Skin.MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        if (Model.tight(p) != null)
            top.addView(Ui.text(c, " Close match ", 11f, 0xFF2A1700.toInt(), true).apply { background = Ui.box(c, Skin.AMBER, Skin.AMBER, 6, 0) })
        card.addView(top)
        val teams = LinearLayout(c)
        teams.gravity = Gravity.CENTER_VERTICAL
        teams.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 6))
        val px = Ui.dp(c, 26)
        teams.addView(Crest.badge(c, fx.home, 26), LinearLayout.LayoutParams(px, px).apply { rightMargin = Ui.dp(c, 8) })
        teams.addView(Ui.text(c, "${fx.home} v ${fx.away}", 15f, Skin.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        teams.addView(Crest.badge(c, fx.away, 26), LinearLayout.LayoutParams(px, px).apply { leftMargin = Ui.dp(c, 8) })
        card.addView(teams)
        card.addView(ResultBar(c, doubleArrayOf(p.home, p.draw, p.away), intArrayOf(Skin.HOME, Skin.DRAW, Skin.AWAY)), LinearLayout.LayoutParams(-1, Ui.dp(c, 8)))
        val line = LinearLayout(c)
        line.setPadding(0, Ui.dp(c, 6), 0, 0)
        line.addView(Ui.text(c, pickName(p, fx.home, fx.away), 13f, Skin.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(Ui.text(c, "${Ui.pct(p.home)}  ${Ui.pct(p.draw)}  ${Ui.pct(p.away)}", 13f, Skin.MUTED))
        card.addView(line)
        val extra = LinearLayout(c)
        extra.setPadding(0, Ui.dp(c, 3), 0, 0)
        val st = Football.state
        val lu = if (st != null) lineupConf(st, fv.league, fx) else ""
        extra.addView(Ui.text(c, "Likeliest ${p.scoreH}-${p.scoreA}" + (if (lu.isNotEmpty()) "  -  Lineups: $lu" else ""), 12f, Skin.MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        val a = Model.agreement(p)
        if (a != null) extra.addView(Ui.text(c, a.label, 12f, Ui.agreeColor(a), true))
        card.addView(extra)
        val o = fx.odds
        if (o != null) card.addView(Ui.text(c, "Odds ${Ui.odds(o)}" + (fx.o25?.let { "   Over/under 2.5: ${Ui.odds(it)}" } ?: ""), 12f, Skin.MUTED).apply { setPadding(0, Ui.dp(c, 2), 0, 0) })
        if (fx.ref.isNotEmpty()) card.addView(Ui.refLink(act, fx.ref, 12f, "Referee: ").apply { setPadding(0, Ui.dp(c, 2), 0, 0) })
        card.setOnClickListener { onOpen() }
        return card
    }
}

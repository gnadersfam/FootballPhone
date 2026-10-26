package com.lounge.phone

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The home page: matches today and tomorrow as cards, then "Next up". Same numbers as the PC app's Tonight page. */
class TonightPage(private val act: MainActivity) : Page {
    private val scroll = ScrollView(act)
    private val col = LinearLayout(act)
    override val view: View get() = scroll

    private class Row(val fv: FixtureView, val ko: Long)

    init {
        col.orientation = LinearLayout.VERTICAL
        val p = Ui.dp(act, 16)
        col.setPadding(p, Ui.dp(act, 12), p, Ui.dp(act, 24))
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        refresh()
    }

    private fun pct(x: Double) = "%d%%".format(Math.round(x * 100).toInt())

    private fun pickName(p: Pred, home: String, away: String): String {
        val v = doubleArrayOf(p.home, p.draw, p.away)
        var i = 0
        for (k in 1..2) if (v[k] > v[i]) i = k
        return listOf(home, "Draw", away)[i] + " " + pct(v[i])
    }

    private fun upcoming(e: Engine): Pair<List<Row>, List<Row>> {
        val now = System.currentTimeMillis() / 1000
        val rows = ArrayList<Row>()
        for (lk in Leagues.ORDER) for (fv in e.fixtures(lk)) {
            val ko = Utc.parse(fv.fx.utc) ?: continue
            if (ko < now - 2 * 3600) continue
            rows.add(Row(fv, ko))
        }
        rows.sortBy { it.ko }
        val end = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 2)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000
        return Pair(rows.filter { it.ko < end }, rows)
    }

    override fun refresh() {
        col.removeAllViews()
        val c = act
        col.addView(Ui.text(c, SimpleDateFormat("EEEE d MMMM", Locale.UK).format(Date()), 18f, Skin.ACCENT, true))
        col.addView(Ui.note(c, "Everything for tonight in one place"))
        val e = Football.engine
        if (e == null) {
            if (Football.loading || Football.refreshing) {
                col.addView(Ui.note(c, "Getting the football data ready. The first time takes a few minutes.", Skin.TEXT))
            } else {
                col.addView(Ui.note(c, "No football data yet.", Skin.TEXT))
                col.addView(Ui.button(c, "Refresh now", true) { Football.refresh(c, false) }, Ui.lp(-2, -2, 8, 0, c))
            }
            return
        }
        if (Store.fdoKey(c).isEmpty())
            col.addView(Ui.note(c, "No football-data.org key set, so only the next few days of fixtures are shown. Paste it in Settings.", Skin.AMBER))
        val pair: Pair<List<Row>, List<Row>> = try { upcoming(e) } catch (x: Throwable) { Crash.log("Tonight", x); Pair(ArrayList<Row>(), ArrayList<Row>()) }
        val soon = pair.first
        val all = pair.second
        val shown = (if (soon.isNotEmpty()) soon else all).take(MAX_CARDS)
        col.addView(Ui.h2(c, if (soon.isNotEmpty()) "Matches today and tomorrow" else "Next matches"))
        if (shown.isEmpty()) {
            col.addView(Ui.note(c, "No fixtures yet. Press Refresh now (top right) to download them.", Skin.TEXT))
            return
        }
        for (r in shown) col.addView(card(r), Ui.lp(-1, -2, 0, 10, c))
        val more = all.drop(MAX_CARDS).take(6)
        if (more.isNotEmpty()) {
            col.addView(Ui.h2(c, "Next up"))
            for (r in more) col.addView(smallRow(r), Ui.lp(-1, -2, 0, 6, c))
            col.addView(Ui.button(c, "See all ${all.size} matches") { act.openTab(1) }, Ui.lp(-2, -2, 6, 0, c))
        }
    }

    private fun team(name: String): View {
        val c = act
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.gravity = Gravity.CENTER_HORIZONTAL
        val size = Ui.dp(c, 46)
        l.addView(Crest.badge(c, name, 46), LinearLayout.LayoutParams(size, size))
        l.addView(Ui.text(c, name, 13f, Skin.TEXT, true).apply { gravity = Gravity.CENTER; setPadding(0, Ui.dp(c, 4), 0, 0) },
            LinearLayout.LayoutParams(-1, -2))
        return l
    }

    private fun agreeColor(a: Agreement?) = when (a?.code) { "agree" -> Skin.GREEN; "disagree" -> Skin.RED; null -> Skin.MUTED; else -> Skin.AMBER }

    private fun card(r: Row): View {
        val c = act
        val fx = r.fv.fx
        val p = r.fv.pred
        val card = Ui.card(c)
        val top = LinearLayout(c)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(Ui.text(c, SimpleDateFormat("EEE HH:mm", Locale.UK).format(Date(r.ko * 1000)), 13f, Skin.MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        if (Model.tight(p) != null)
            top.addView(Ui.text(c, " Close match ", 11f, 0xFF2A1700.toInt(), true).apply {
                background = Ui.box(c, Skin.AMBER, Skin.AMBER, 6, 0)
            })
        card.addView(top)
        val teams = LinearLayout(c)
        teams.gravity = Gravity.CENTER_VERTICAL
        teams.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 8))
        teams.addView(team(fx.home), LinearLayout.LayoutParams(0, -2, 1f))
        teams.addView(Ui.text(c, "v", 14f, Skin.MUTED), LinearLayout.LayoutParams(-2, -2))
        teams.addView(team(fx.away), LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(teams)
        card.addView(ResultBar(c, doubleArrayOf(p.home, p.draw, p.away), intArrayOf(Skin.HOME, Skin.DRAW, Skin.AWAY)),
            LinearLayout.LayoutParams(-1, Ui.dp(c, 8)))
        val line = LinearLayout(c)
        line.setPadding(0, Ui.dp(c, 6), 0, 0)
        line.addView(Ui.text(c, pickName(p, fx.home, fx.away), 13f, Skin.TEXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(Ui.text(c, "${pct(p.home)}  ${pct(p.draw)}  ${pct(p.away)}", 13f, Skin.MUTED))
        card.addView(line)
        val extra = LinearLayout(c)
        extra.setPadding(0, Ui.dp(c, 4), 0, 0)
        extra.addView(Ui.text(c, "Likeliest ${p.scoreH}-${p.scoreA}", 12f, Skin.MUTED), LinearLayout.LayoutParams(0, -2, 1f))
        val a = Model.agreement(p)
        if (a != null) extra.addView(Ui.text(c, a.label, 12f, agreeColor(a), true))
        card.addView(extra)
        if (fx.ref.isNotEmpty()) card.addView(Ui.text(c, "Referee: ${fx.ref}", 12f, Skin.MUTED).apply { setPadding(0, Ui.dp(c, 2), 0, 0) })
        card.setOnClickListener { details(r) }
        return card
    }

    private fun smallRow(r: Row): View {
        val c = act
        val fx = r.fv.fx
        val row = LinearLayout(c)
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = Ui.box(c, Skin.PANEL, Skin.LINE, 12)
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10))
        row.addView(Ui.text(c, SimpleDateFormat("EEE HH:mm", Locale.UK).format(Date(r.ko * 1000)), 12f, Skin.MUTED),
            LinearLayout.LayoutParams(Ui.dp(c, 78), -2))
        val mid = LinearLayout(c)
        mid.orientation = LinearLayout.VERTICAL
        mid.addView(Ui.text(c, "${fx.home} v ${fx.away}", 14f, Skin.TEXT, true))
        mid.addView(Ui.text(c, pickName(r.fv.pred, fx.home, fx.away), 12f, Skin.MUTED))
        row.addView(mid, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener { details(r) }
        return row
    }

    /** Until the full match page is built (stage 3), a tap shows the numbers for the match in a box. */
    private fun details(r: Row) {
        val fx = r.fv.fx
        val p = r.fv.pred
        val sb = StringBuilder()
        sb.append(Leagues.ALL.getValue(r.fv.league).name).append("\n")
        sb.append(SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK).format(Date(r.ko * 1000))).append("\n\n")
        sb.append("${fx.home} win ${pct(p.home)}\nDraw ${pct(p.draw)}\n${fx.away} win ${pct(p.away)}\n\n")
        sb.append("Likeliest score ${p.scoreH}-${p.scoreA} (${pct(p.scoreP)})\n")
        sb.append("Both teams score ${pct(p.btts)}\nOver 2.5 goals ${pct(p.over25)}\n")
        sb.append("Expected goals %.1f - %.1f\n".format(p.lh, p.la))
        val tt = Model.tightText(p, fx.home, fx.away)
        if (tt.isNotEmpty()) sb.append("\n").append(tt).append("\n")
        val a = Model.agreement(p)
        if (a != null) sb.append("\nModel v bookmakers: ").append(a.label).append("\n")
        if (fx.ref.isNotEmpty()) sb.append("Referee: ").append(fx.ref).append("\n")
        sb.append("\nThe full match page (players, form, head to head) arrives in Stage 3.")
        AlertDialog.Builder(act).setTitle("${fx.home} v ${fx.away}").setMessage(sb.toString()).setPositiveButton("Close", null).show()
    }

    companion object { const val MAX_CARDS = 4 }
}

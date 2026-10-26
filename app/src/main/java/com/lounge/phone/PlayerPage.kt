package com.lounge.phone

import android.view.Gravity
import android.widget.LinearLayout

/** A small card for one player: who, where, the model's usual rates per appearance, and what he really averages. */
class PlayerPage(act: MainActivity, private val id: String) : ScrollPage(act) {
    override fun build() {
        val e = engineOrNote() ?: return
        val a = e.players[id]
        if (a == null) { col.addView(Ui.note(act, "No data for this player yet.", Skin.TEXT)); return }
        val top = LinearLayout(act)
        top.gravity = Gravity.CENTER_VERTICAL
        val px = Ui.dp(act, 72)
        top.addView(Crest.face(act, a.name, a.id, a.team, 72), LinearLayout.LayoutParams(px, px).apply { rightMargin = Ui.dp(act, 14) })
        val info = LinearLayout(act)
        info.orientation = LinearLayout.VERTICAL
        info.addView(Ui.h1(act, a.name))
        val pos = mapOf("G" to "Goalkeeper", "D" to "Defender", "M" to "Midfielder", "F" to "Forward", "X" to "Forward")[a.group] ?: a.pos
        info.addView(Ui.text(act, "${a.team}   $pos", 14f, Skin.TEXT))
        info.addView(Ui.text(act, "Starts ${a.start.apps}   Sub appearances ${a.sub.apps}   Red cards ${a.rc}", 12f, Skin.MUTED))
        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(top)

        val rates = Model.playerRates(a, e.pri)
        col.addView(Ui.h2(act, "Per appearance (the model's rates)"))
        val heads = listOf(Hd("", 120, Gravity.START), Hd("Fouls", 62), Hd("Fouled", 66), Hd("Shots", 62), Hd("On target", 76), Hd("Yellow card", 86))
        fun line(label: String, r: Rates) = listOf(Cv(label, bold = true), Cv("%.2f".format(r.v[S_FOULS])), Cv("%.2f".format(r.v[S_FOULED])), Cv("%.2f".format(r.v[S_SHOTS])),
            Cv("%.2f".format(r.v[S_SOT])), Cv(Ui.pct(Math.min(r.v[S_YC], 0.99))))
        col.addView(PTable.build(act, heads, listOf(line("As a starter", rates[0]), line("As a substitute", rates[1])), fixed = 1))
        col.addView(Ui.note(act, "Rates are shrunk towards the usual figure for the position until there are enough games."))

        val g = a.start.apps + a.sub.apps
        if (g > 0) {
            col.addView(Ui.h2(act, "This season, per game (real averages, $g appearances)"))
            fun pg(k: Int) = (a.start.s[k] + a.sub.s[k]).toDouble() / g
            col.addView(PTable.build(act, listOf(Hd("", 120, Gravity.START), Hd("Yellows", 66), Hd("Fouls", 62), Hd("Fouled", 66), Hd("Shots", 62), Hd("On target", 76)),
                listOf(listOf(Cv("This season", bold = true), Cv("%.2f".format(pg(S_YC))), Cv("%.2f".format(pg(S_FOULS))), Cv("%.2f".format(pg(S_FOULED))),
                    Cv("%.2f".format(pg(S_SHOTS))), Cv("%.2f".format(pg(S_SOT))))), fixed = 1))
        }
        val ls = LastSeason.players(act)[id]
        if (ls != null) {
            col.addView(Ui.h2(act, "Last season, per game (${ls.apps} appearances, ${ls.club})"))
            col.addView(PTable.build(act, listOf(Hd("", 120, Gravity.START), Hd("Yellows", 66), Hd("Fouls", 62), Hd("Fouled", 66), Hd("Shots", 62), Hd("On target", 76)),
                listOf(listOf(Cv("Last season", bold = true)) + ls.pg.map { Cv("%.2f".format(it)) }), fixed = 1))
        }
        val picks = Football.picks()
        val on = picks.inWatch(id)
        col.addView(Ui.button(act, if (on) "Remove from watch-list" else "Add to watch-list", !on) {
            val now = picks.toggleWatch(id, a.name, a.team)
            act.toast(if (now) "${a.name} added to your watch-list" else "${a.name} removed from your watch-list")
            refresh()
        }, Ui.lp(-2, -2, 16, 0, act))
    }
}

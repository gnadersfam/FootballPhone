package com.lounge.phone

import android.view.Gravity
import android.widget.LinearLayout

/** A team's match log this season, newest first. */
class TeamPage(act: MainActivity, private val lk: String, private val team: String) : ScrollPage(act) {
    override fun build() {
        val e = engineOrNote() ?: return
        val top = LinearLayout(act)
        top.gravity = Gravity.CENTER_VERTICAL
        val px = Ui.dp(act, 40)
        top.addView(Crest.badge(act, team, 40), LinearLayout.LayoutParams(px, px).apply { rightMargin = Ui.dp(act, 12) })
        top.addView(Ui.h1(act, team))
        col.addView(top)
        col.addView(Ui.note(act, leagueName(lk) + " - every match this season, newest first"))
        val log = e.teamView(lk, team)
        if (log.isEmpty()) { col.addView(Ui.note(act, "No matches in the data yet.", Skin.TEXT)); return }
        val heads = listOf(Hd("Opponent", 150, Gravity.START), Hd("Date", 84), Hd("Score", 56), Hd("xG", 48), Hd("xGA", 48), Hd("Fouls", 52), Hd("Yel", 44), Hd("Referee", 140, Gravity.START))
        val rows = log.map { r ->
            listOf(Cv("${r.venue}  ${r.opp}", bold = true, crest = r.opp), Cv(r.date, Skin.MUTED), Cv(r.score, Skin.ACCENT, true),
                Cv("%.1f".format(r.xg)), Cv("%.1f".format(r.xga)), Cv(r.fouls?.toString() ?: "-"), Cv(r.yc?.toString() ?: "-"),
                Cv(r.ref.ifEmpty { "-" }, Skin.MUTED, click = if (r.ref.isNotEmpty()) ({ act.openReferee(r.ref) }) else null))
        }
        col.addView(PTable.build(act, heads, rows, fixed = 1), Ui.lp(-1, -2, 6, 0, act))
        col.addView(Ui.note(act, "H = home, A = away. Tap a referee's name for their card."))
    }
}

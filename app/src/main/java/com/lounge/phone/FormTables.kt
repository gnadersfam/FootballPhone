package com.lounge.phone

import android.content.Context
import android.view.Gravity
import android.view.View

/** The form table (last N league results per team), used by the Stats > Form page and by the match page. */
object FormTables {
    fun table(act: MainActivity, e: Engine, lk: String, n: Int, mark: Set<String>, topOnly: Boolean): View {
        val list = e.formTable(lk, n)
        val heads = listOf(Hd("#", 32), Hd("Team", 140, Gravity.START), Hd("Last $n", 120, Gravity.START), Hd("P", 34), Hd("Pts", 40), Hd("GF", 38), Hd("GA", 38),
            Hd("GD", 42), Hd("xGF", 50), Hd("xGA", 50), Hd("xGD", 54))
        val idx = ArrayList<Int>()
        for ((i, r) in list.withIndex()) {
            if (topOnly && i >= 3 && r.team !in mark) continue
            idx.add(i)
        }
        val rows = idx.map { i ->
            val r = list[i]
            val hl = r.team in mark
            val tc = if (hl) Skin.ACCENT else Skin.TEXT
            listOf(Cv("${i + 1}", Skin.MUTED), Cv(r.team, tc, true, crest = r.team), Cv(r.seq.joinToString(" "), Skin.MUTED), Cv("${r.p}"), Cv("${r.pts}", Skin.ACCENT, true),
                Cv("${r.gf}"), Cv("${r.ga}"), Cv(Ui.sign((r.gf - r.ga).toDouble(), 0)), Cv("%.1f".format(r.xgf), Skin.MUTED), Cv("%.1f".format(r.xga), Skin.MUTED),
                Cv(Ui.sign(r.xgd), if (r.xgd >= 0) Skin.GREEN else Skin.RED))
        }
        return PTable.build(act, heads, rows, fixed = 2, onRow = { k -> act.openTeam(lk, list[idx[k]].team) })
    }
}

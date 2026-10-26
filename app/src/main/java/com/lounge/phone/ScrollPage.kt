package com.lounge.phone

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

/** A page that is one scrolling column. Rebuilding it keeps your place on the page. Subclasses fill [col] in [build]. */
abstract class ScrollPage(protected val act: MainActivity) : Page {
    protected val scroll = ScrollView(act)
    protected val col = LinearLayout(act)
    override val view: View get() = scroll

    init {
        col.orientation = LinearLayout.VERTICAL
        val p = Ui.dp(act, 16)
        col.setPadding(p, Ui.dp(act, 12), p, Ui.dp(act, 28))
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
    }

    protected abstract fun build()

    override fun refresh() {
        val y = scroll.scrollY
        col.removeAllViews()
        try {
            build()
        } catch (e: Throwable) {
            Crash.log("Page ${javaClass.simpleName}", e)
            col.addView(Ui.note(act, "Something went wrong drawing this page (${e.javaClass.simpleName}). Press Refresh now, or go back and try again.", Skin.AMBER))
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    /** The football numbers, or null (and a short message on the page) while there are none yet. */
    protected fun engineOrNote(): Engine? {
        val e = Football.engine
        if (e == null) {
            col.addView(Ui.note(act, if (Football.loading || Football.refreshing) "Getting the football data ready. The first time takes a few minutes." else "No football data yet. Press Refresh now (top right).", Skin.TEXT))
        }
        return e
    }

    protected fun nowSec() = System.currentTimeMillis() / 1000
    protected fun ko(fx: Fixture): Long = Utc.parse(fx.utc) ?: 0L
    protected fun leagueName(lk: String) = Leagues.ALL.getValue(lk).name
}

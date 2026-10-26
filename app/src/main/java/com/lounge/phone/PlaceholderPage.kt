package com.lounge.phone

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** A tab that is built in a later stage. It says so, and lists what will be on it. */
class PlaceholderPage(private val act: MainActivity, private val title: String, private val lines: List<String>) : Page {
    private val root = LinearLayout(act)
    override val view: View get() = root

    init { refresh() }

    override fun refresh() {
        root.removeAllViews()
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        val p = Ui.dp(act, 24)
        root.setPadding(p, p, p, p)
        root.addView(Ui.h1(act, title).apply { gravity = Gravity.CENTER })
        root.addView(Ui.note(act, "This tab is built in a later stage.", Skin.AMBER).apply { gravity = Gravity.CENTER })
        for (l in lines) root.addView(Ui.note(act, l).apply { gravity = Gravity.CENTER })
    }
}

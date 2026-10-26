package com.lounge.phone

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale

/** Settings: theme, the football-data.org key (type or paste it), Refresh now and a short "how it works". */
class SettingsPage(private val act: MainActivity) : Page {
    private val scroll = ScrollView(act)
    private val col = LinearLayout(act)
    override val view: View get() = scroll
    private var keyBox: EditText? = null

    init {
        col.orientation = LinearLayout.VERTICAL
        val p = Ui.dp(act, 16)
        col.setPadding(p, Ui.dp(act, 12), p, Ui.dp(act, 32))
        scroll.addView(col, FrameLayout.LayoutParams(-1, -2))
        refresh()
    }

    override fun refresh() {
        // keep what has been typed in the key box while the page redraws
        val typed = keyBox?.text?.toString()
        col.removeAllViews()
        val c = act
        col.addView(Ui.h1(c, "Settings"))

        col.addView(Ui.h2(c, "Appearance"))
        col.addView(Ui.note(c, "Tap a theme to switch to it. It changes straight away and is remembered."))
        val grid = LinearLayout(c)
        grid.orientation = LinearLayout.VERTICAL
        var rowL: LinearLayout? = null
        for ((i, th) in Skin.THEMES.withIndex()) {
            if (i % 2 == 0) {
                rowL = LinearLayout(c)
                grid.addView(rowL, Ui.lp(-1, -2, 0, 8, c))
            }
            val r = rowL!!
            r.addView(tile(th), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = Ui.dp(c, 8) })
            if (i == Skin.THEMES.size - 1 && Skin.THEMES.size % 2 == 1)
                r.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f).apply { rightMargin = Ui.dp(c, 8) })
        }
        col.addView(grid)

        col.addView(Ui.h2(c, "football-data.org key"))
        col.addView(Ui.note(c, "Gives the full list of upcoming matches. It is free at football-data.org (Register). Without it only the next few days of fixtures are shown. Type it or paste it here, then press Save."))
        val box = EditText(c).apply {
            setText(typed ?: Store.fdoKey(c))
            hint = "Paste your key"
            setTextColor(Skin.TEXT)
            setHintTextColor(Skin.MUTED)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            background = Ui.box(c, Skin.PANEL, Skin.LINE, 10)
            setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10))
        }
        keyBox = box
        col.addView(box, Ui.lp(-1, -2, 6, 6, c))
        val msg = Ui.note(c, if (Store.fdoKey(c).isEmpty()) "No key saved yet." else "A key is saved.")
        val btns = LinearLayout(c)
        btns.addView(Ui.button(c, "Save", true) {
            Store.putStr(c, "fdo_key", box.text.toString().trim())
            msg.text = "Saved. Press Refresh now to use it."
        })
        btns.addView(Ui.button(c, "Refresh now") { Football.refresh(c, false) }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(c, 8) })
        col.addView(btns)
        col.addView(msg)

        col.addView(Ui.h2(c, "Football data"))
        val u = Football.state?.lastUpdated().orEmpty()
        val t = if (u.isEmpty()) null else Utc.parse(u)
        col.addView(Ui.note(c, if (t == null) "Never updated" else "Last updated " + SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK).format(java.util.Date(t * 1000)), Skin.TEXT))

        col.addView(Ui.h2(c, "How it works"))
        col.addView(Ui.note(c,
            "  - Results, xG, shots, fouls, cards and referees: football-data.co.uk\n" +
            "  - Upcoming fixtures: football-data.org\n" +
            "  - Bookmaker odds for the next few days (blended into the result and over 2.5 figures): football-data.co.uk\n" +
            "  - Player stats and lineups: ESPN public match data\n\n" +
            "The app refreshes itself when you open it and then every hour while it is open. You can also press Refresh now (top right) any time. " +
            "Finished scores also come from ESPN, so tables update the same day.\n\n" +
            "All percentages are model estimates, not bookmaker prices or guarantees. Player figures are per appearance (minutes played are not available; " +
            "who plays is judged from recent matches). The data sources are free and unofficial, so they can change without notice."))

        val problems = Crash.text()
        if (problems.isNotEmpty()) {
            col.addView(Ui.h2(c, "Problems noted"))
            col.addView(Ui.note(c, problems.takeLast(1500)))
            col.addView(Ui.button(c, "Clear") { Crash.clear(); refresh() })
        }
    }

    private fun tile(th: Theme): View {
        val c = act
        val selected = Skin.t.key == th.key
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.background = Ui.box(c, th.bg, if (selected) th.accent else th.line, 12, if (selected) 3 else 1)
        l.setPadding(Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10), Ui.dp(c, 10))
        val strip = View(c)
        strip.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            (if (th.strip.size >= 2) th.strip else listOf(th.strip[0], th.strip[0])).toIntArray())
        l.addView(strip, LinearLayout.LayoutParams(-1, Ui.dp(c, 4)))
        val panel = View(c)
        panel.background = Ui.box(c, th.panel, th.line, 6)
        l.addView(panel, Ui.lp(-1, Ui.dp(c, 26), 8, 0, c).apply { height = Ui.dp(c, 26) })
        l.addView(Ui.text(c, th.label.replace(" (older)", "").replace(" (plain)", ""), 13f, th.text, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(c, 8), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        l.setOnClickListener {
            Store.putStr(c, "theme", th.key)
            Skin.apply(th.key)
            act.rebuild()
        }
        return l
    }
}

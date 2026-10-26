package com.lounge.phone

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** The phone app: a header with Refresh now, the page, and tabs along the bottom. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val pages = ArrayList<Page>()
    private val tabViews = ArrayList<TextView>()
    private lateinit var content: FrameLayout
    private lateinit var statusView: TextView
    private lateinit var barView: View
    private var tab = 0

    private val hourly = object : Runnable {
        override fun run() {
            Football.refresh(this@MainActivity, true)
            handler.postDelayed(this, HOUR)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Crash.install(this)
        Skin.load(this)
        Football.init(this)
        TeamLogos.ensure(this)
        tab = savedInstanceState?.getInt("tab") ?: 0
        build()
        Football.start(this, true)         // read what is saved, then refresh once as the app opens
        handler.postDelayed(hourly, HOUR)   // and then once an hour while it is open
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onResume() {
        super.onResume()
        Football.onChange = { progressOnly -> onFootballChange(progressOnly) }
        onFootballChange(false)
    }

    override fun onDestroy() {
        handler.removeCallbacks(hourly)
        Football.onChange = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (tab != 0) openTab(0) else super.onBackPressed()
    }

    fun rebuild() { build() }

    fun openTab(i: Int) {
        tab = i
        show()
    }

    private fun build() {
        val c = this
        pages.clear(); tabViews.clear()
        pages.add(TonightPage(c))
        pages.add(PlaceholderPage(c, "Leagues", listOf("Premier League and Championship tables", "Upcoming matches", "The full match page")))
        pages.add(PlaceholderPage(c, "Referees", listOf("Every referee with green and red colouring", "Tap a name for their card and last 5 games")))
        pages.add(PlaceholderPage(c, "Stats", listOf("Top 5, Form, Expected table", "Scorecard, My Picks and Watch-list")))
        pages.add(SettingsPage(c))

        window.statusBarColor = Skin.BG
        window.navigationBarColor = Skin.PANEL
        window.decorView.systemUiVisibility = if (Skin.t.dark) 0 else (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or 0)

        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Skin.BG)
        root.fitsSystemWindows = true

        // header
        val head = LinearLayout(c)
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8))
        val titles = LinearLayout(c)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(Ui.text(c, "Football Stats", 18f, Skin.TEXT, true))
        statusView = Ui.text(c, "", 12f, Skin.MUTED)
        titles.addView(statusView)
        head.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(Ui.button(c, "Refresh now", false) { Football.refresh(c, false) })
        root.addView(head, LinearLayout.LayoutParams(-1, -2))

        // thin colour line, like the PC app
        val strip = View(c)
        strip.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            (if (Skin.t.strip.size >= 2) Skin.t.strip else listOf(Skin.ACCENT, Skin.ACCENT)).toIntArray())
        root.addView(strip, LinearLayout.LayoutParams(-1, Ui.dp(c, 3)))

        barView = View(c)
        barView.setBackgroundColor(Skin.ACCENT)
        root.addView(barView, LinearLayout.LayoutParams(0, Ui.dp(c, 2)))

        content = FrameLayout(c)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        // tabs along the bottom
        val nav = LinearLayout(c)
        nav.setBackgroundColor(Skin.PANEL)
        val names = listOf("Tonight" to "🏠", "Leagues" to "⚽", "Referees" to "🟨", "Stats" to "📊", "Settings" to "⚙")
        for ((i, n) in names.withIndex()) {
            val t = TextView(c)
            t.text = n.second + "\n" + n.first
            t.gravity = Gravity.CENTER
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            t.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8))
            t.setOnClickListener { openTab(i) }
            tabViews.add(t)
            nav.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root, ViewGroup.LayoutParams(-1, -1))
        show()
        onFootballChange(false)
    }

    private fun show() {
        content.removeAllViews()
        val p = pages[tab]
        p.refresh()
        content.addView(p.view, FrameLayout.LayoutParams(-1, -1))
        for ((i, t) in tabViews.withIndex()) t.setTextColor(if (i == tab) Skin.ACCENT else Skin.MUTED)
    }

    private fun onFootballChange(progressOnly: Boolean) {
        statusView.text = when {
            Football.refreshing -> Football.status
            Football.loading -> "Loading..."
            Football.message.isNotEmpty() -> Football.message
            else -> {
                val u = Football.state?.lastUpdated().orEmpty()
                val t = if (u.isEmpty()) null else Utc.parse(u)
                if (t == null) "Never updated" else "Last updated " + java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.UK).format(java.util.Date(t * 1000))
            }
        }
        val w = content.width.coerceAtLeast(resources.displayMetrics.widthPixels)
        barView.layoutParams = (barView.layoutParams as LinearLayout.LayoutParams).apply {
            width = if (Football.refreshing) (w * Football.progress).toInt() else 0
        }
        if (!progressOnly) pages[tab].let { if (tab != 4 || !Football.refreshing) it.refresh() }
    }

    companion object { const val HOUR = 60 * 60 * 1000L }
}

package com.lounge.phone

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The phone app: a header with Refresh now, a search box, the scoreboard ticker, the page, and tabs along the bottom. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val pages = ArrayList<Page>()
    private val tabViews = ArrayList<TextView>()
    private val stack = ArrayList<Page>()
    private val stackTitles = ArrayList<String>()
    private lateinit var content: FrameLayout
    private lateinit var holder: FrameLayout
    private lateinit var statusView: TextView
    private lateinit var barView: View
    private lateinit var ticker: TextView
    private lateinit var searchBox: EditText
    private lateinit var results: ScrollView
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
        Faces.init(this)
        tab = savedInstanceState?.getInt("tab") ?: Store.str(this, "last_tab", "0").toIntOrNull() ?: 0
        if (tab !in 0..4) tab = 0
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
        when {
            results.visibility == View.VISIBLE -> closeSearch()
            stack.isNotEmpty() -> pop()
            tab != 0 -> openTab(0)
            else -> super.onBackPressed()
        }
    }

    fun rebuild() { stack.clear(); stackTitles.clear(); build() }

    fun openTab(i: Int) {
        tab = i
        Store.putStr(this, "last_tab", i.toString())
        stack.clear(); stackTitles.clear()
        closeSearch()
        show()
    }

    // ---------------------------------------------------------------- pages on top of a tab (match, team, referee, player...)
    fun push(p: Page, title: String) {
        closeSearch()
        stack.add(p); stackTitles.add(title)
        show()
    }

    fun pop() {
        if (stack.isEmpty()) return
        stack.removeAt(stack.size - 1); stackTitles.removeAt(stackTitles.size - 1)
        show()
    }

    fun openMatch(lk: String, fx: Fixture) = push(MatchPage(this, lk, fx), "${fx.home} v ${fx.away}")
    fun openTeam(lk: String, team: String) = push(TeamPage(this, lk, team), team)
    fun openReferee(name: String) { if (name.isNotBlank()) push(RefereeCard(this, name), name) }
    fun openPlayer(id: String) = push(PlayerPage(this, id), "Player")
    fun openSetup() = push(SetupPage(this), "Check my setup")

    fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    // ---------------------------------------------------------------- settings file (open / save through the phone's file picker)
    private var fileDone: ((String) -> Unit)? = null

    fun loadSettingsFile(done: (String) -> Unit) {
        fileDone = done
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, REQ_LOAD)
        } catch (e: Throwable) { done("Could not open the file picker: ${e.message}") }
    }

    fun saveSettingsFile(done: (String) -> Unit) {
        fileDone = done
        try {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/json"
            i.putExtra(Intent.EXTRA_TITLE, "lounge-settings.json")
            startActivityForResult(i, REQ_SAVE)
        } catch (e: Throwable) { done("Could not open the file picker: ${e.message}") }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val done = fileDone ?: return
        fileDone = null
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            if (requestCode == REQ_SAVE) {
                contentResolver.openOutputStream(uri)?.use { it.write(SettingsFile.export(this).toByteArray(Charsets.UTF_8)) }
                done("Settings file saved (your picks and watch-list).")
            } else if (requestCode == REQ_LOAD) {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                AlertDialog.Builder(this).setTitle("Load settings file")
                    .setMessage("Add the picks and watch-list in this file to the ones on this phone, or replace the ones on this phone with the file's?")
                    .setPositiveButton("Add to mine") { _, _ -> finishLoad(text, false, done) }
                    .setNegativeButton("Replace mine") { _, _ -> finishLoad(text, true, done) }
                    .setNeutralButton("Cancel", null).show()
            }
        } catch (e: Throwable) {
            Crash.log("Settings file", e)
            done("That did not work: ${e.message}")
        }
    }

    private fun finishLoad(text: String, replace: Boolean, done: (String) -> Unit) {
        try { done(SettingsFile.import(text, replace)); refreshPage() }
        catch (e: IllegalArgumentException) { done(e.message ?: "That file could not be read.") }
        catch (e: Throwable) { Crash.log("Settings file load", e); done("That file could not be read (${e.message}).") }
    }

    // ---------------------------------------------------------------- building the screen
    private fun build() {
        val c = this
        pages.clear(); tabViews.clear()
        pages.add(TonightPage(c))
        pages.add(LeaguesPage(c))
        pages.add(RefereesPage(c))
        pages.add(StatsPage(c))
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
        statusView.maxLines = 2
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

        // one search box for teams and players
        searchBox = EditText(c).apply {
            hint = "Search teams and players"
            setTextColor(Skin.TEXT)
            setHintTextColor(Skin.MUTED)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            background = Ui.box(c, Skin.PANEL, Skin.LINE, 18)
            setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8))
        }
        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c1: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c1: Int) {}
            override fun afterTextChanged(s: Editable?) { runSearch(s?.toString() ?: "") }
        })
        val sb = LinearLayout(c)
        sb.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 4))
        sb.addView(searchBox, LinearLayout.LayoutParams(-1, -2))
        root.addView(sb, LinearLayout.LayoutParams(-1, -2))

        // scoreboard ticker (can be switched off in Settings)
        ticker = TextView(c).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(Skin.t.tickFg)
            setBackgroundColor(Skin.t.tickBg)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 6))
        }
        root.addView(ticker, LinearLayout.LayoutParams(-1, -2))

        content = FrameLayout(c)
        results = ScrollView(c).apply { setBackgroundColor(Skin.BG); visibility = View.GONE }
        holder = FrameLayout(c)
        holder.addView(content, FrameLayout.LayoutParams(-1, -1))
        holder.addView(results, FrameLayout.LayoutParams(-1, -1))
        root.addView(holder, LinearLayout.LayoutParams(-1, 0, 1f))

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
        if (stack.isEmpty()) {
            val p = pages[tab]
            p.refresh()
            (p.view.parent as? ViewGroup)?.removeView(p.view)
            content.addView(p.view, FrameLayout.LayoutParams(-1, -1))
        } else {
            val p = stack.last()
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val bar = LinearLayout(this)
            bar.gravity = Gravity.CENTER_VERTICAL
            bar.setBackgroundColor(Skin.PANEL)
            bar.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 12), 0)
            bar.addView(Ui.text(this, "‹ Back", 15f, Skin.ACCENT, true).apply {
                setPadding(Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 14), Ui.dp(this@MainActivity, 12))
                setOnClickListener { pop() }
            })
            bar.addView(Ui.text(this, stackTitles.last(), 14f, Skin.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(0, -2, 1f))
            col.addView(bar, LinearLayout.LayoutParams(-1, -2))
            p.refresh()
            (p.view.parent as? ViewGroup)?.removeView(p.view)
            col.addView(p.view, LinearLayout.LayoutParams(-1, 0, 1f))
            content.addView(col, FrameLayout.LayoutParams(-1, -1))
        }
        for ((i, t) in tabViews.withIndex()) t.setTextColor(if (i == tab) Skin.ACCENT else Skin.MUTED)
    }

    /** Redraws whatever is on the screen (after picks or settings changed). */
    fun refreshPage() {
        if (stack.isNotEmpty()) stack.last().refresh() else pages[tab].refresh()
    }

    // ---------------------------------------------------------------- search
    private fun closeSearch() {
        if (!::results.isInitialized) return
        results.visibility = View.GONE
        if (searchBox.text.isNotEmpty()) searchBox.setText("")
        searchBox.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBox.windowToken, 0)
    }

    private fun score(name: String, words: List<String>, q: String): Int? {
        val low = name.lowercase()
        if (!words.all { it in low }) return null
        if (low.startsWith(q)) return 0
        if ((" " + low).contains(" " + words[0])) return 1
        return 2
    }

    private fun runSearch(text: String) {
        val q = text.trim().lowercase()
        if (q.length < 2) { results.visibility = View.GONE; return }
        val e = Football.engine
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 24))
        val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var any = false
        if (e != null) {
            class Hit(val key: Int, val len: Int, val title: String, val sub: String, val act: () -> Unit)
            val teams = ArrayList<Hit>()
            for (lk in Leagues.ORDER) for (r in e.league.getValue(lk).table) {
                val s = score(r.team, words, q) ?: continue
                teams.add(Hit(s, r.team.length, r.team, "Team  -  " + Leagues.ALL.getValue(lk).name) { openTeam(lk, r.team) })
            }
            val players = ArrayList<Hit>()
            for (a in e.players.values) {
                val s = score(a.name, words, q) ?: continue
                val apps = a.start.apps + a.sub.apps
                players.add(Hit(s * 1000 - apps, a.name.length, a.name, "Player  -  ${a.team}") { openPlayer(a.id) })
            }
            for ((label, list) in listOf("TEAMS" to teams.sortedWith(compareBy({ it.key }, { it.len })).take(6),
                "PLAYERS" to players.sortedWith(compareBy({ it.key }, { it.len })).take(8))) {
                if (list.isEmpty()) continue
                any = true
                col.addView(Ui.text(this, label, 11f, Skin.ACCENT, true).apply { setPadding(0, Ui.dp(this@MainActivity, 10), 0, Ui.dp(this@MainActivity, 4)) })
                for (h in list) {
                    val row = LinearLayout(this)
                    row.orientation = LinearLayout.VERTICAL
                    row.background = Ui.box(this, Skin.PANEL, Skin.LINE, 10)
                    row.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8))
                    row.addView(Ui.text(this, h.title, 15f, Skin.TEXT, true))
                    row.addView(Ui.text(this, h.sub, 12f, Skin.MUTED))
                    row.setOnClickListener { closeSearch(); h.act() }
                    col.addView(row, Ui.lp(-1, -2, 0, 6, this))
                }
            }
        }
        if (!any) col.addView(Ui.note(this, if (e == null) "The football data is not ready yet." else "Nothing found for \"$text\".", Skin.TEXT))
        results.removeAllViews()
        results.addView(col, FrameLayout.LayoutParams(-1, -2))
        results.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- following the football data
    private fun tickerText(): String {
        val e = Football.engine ?: return ""
        val now = System.currentTimeMillis() / 1000
        val rows = ArrayList<Pair<Long, FixtureView>>()
        for (lk in Leagues.ORDER) for (fv in e.fixtures(lk)) {
            val ko = Utc.parse(fv.fx.utc) ?: continue
            if (ko < now - 2 * 3600 || ko > now + 7 * 86400) continue
            rows.add(ko to fv)
        }
        rows.sortBy { it.first }
        return rows.take(14).joinToString("      •      ") { (ko, fv) ->
            val p = fv.pred
            "${fv.fx.home.uppercase()} v ${fv.fx.away.uppercase()}  ${SimpleDateFormat("EEE HH:mm", Locale.UK).format(Date(ko * 1000))}   " +
                "${Ui.pct(p.home)} / ${Ui.pct(p.draw)} / ${Ui.pct(p.away)}"
        }
    }

    private fun updateTicker() {
        val on = Store.bool(this, "ticker", true)
        val t = if (on) try { tickerText() } catch (x: Throwable) { Crash.log("Ticker", x); "" } else ""
        ticker.visibility = if (t.isEmpty()) View.GONE else View.VISIBLE
        if (t.isNotEmpty() && ticker.text.toString() != t) { ticker.text = t; ticker.isSelected = false; ticker.isSelected = true }
    }

    fun tickerChanged() { updateTicker() }

    private fun onFootballChange(progressOnly: Boolean) {
        statusView.text = when {
            Football.refreshing -> Football.status
            Football.loading -> "Loading..."
            Football.lsMessage.isNotEmpty() -> Football.lsMessage
            Football.message.isNotEmpty() -> Football.message
            else -> {
                val u = Football.state?.lastUpdated().orEmpty()
                val t = if (u.isEmpty()) null else Utc.parse(u)
                if (t == null) "Never updated" else "Last updated " + SimpleDateFormat("EEE d MMM, HH:mm", Locale.UK).format(Date(t * 1000))
            }
        }
        val w = content.width.coerceAtLeast(resources.displayMetrics.widthPixels)
        barView.layoutParams = (barView.layoutParams as LinearLayout.LayoutParams).apply {
            width = if (Football.refreshing) (w * Football.progress).toInt() else 0
        }
        if (!progressOnly) {
            updateTicker()
            if (stack.isNotEmpty()) stack.last().refresh()
            else pages[tab].let { if (tab != 4 || !Football.refreshing) it.refresh() }
        }
    }

    companion object {
        const val HOUR = 60 * 60 * 1000L
        const val REQ_LOAD = 41
        const val REQ_SAVE = 42
    }
}

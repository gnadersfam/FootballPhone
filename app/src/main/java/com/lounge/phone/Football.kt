package com.lounge.phone

import android.content.Context
import java.util.Calendar

/**
 * The football data and numbers, shared by every screen. Loads the saved data, refreshes it (once when the app opens,
 * then every hour while it is open, or when you press Refresh now) and builds the engine that gives the same
 * percentages as the PC app.
 */
object Football {
    private var io: Persist? = null
    var state: FootState? = null
        private set
    @Volatile var engine: Engine? = null
        private set
    @Volatile var loading = false
        private set
    @Volatile var refreshing = false
        private set
    @Volatile var status = ""
    @Volatile var progress = 0.0
    @Volatile var message = ""

    /** Called on the screen thread. The flag is true when only the progress text changed. */
    var onChange: ((Boolean) -> Unit)? = null

    private var dataLoaded = false

    fun init(c: Context) {
        if (io != null) return
        val p = AndroidPersist(c.applicationContext)
        io = p
        state = FootState(p)
    }

    fun persist(): Persist = io ?: throw IllegalStateException("football not started")

    private fun newEngine(st: FootState): Engine {
        val c = Calendar.getInstance()
        return Engine(st, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private fun tell(progressOnly: Boolean) { Bg.ui { onChange?.invoke(progressOnly) } }

    /** Reads what is saved on the phone, then (when asked) refreshes straight away. */
    fun start(c: Context, thenRefresh: Boolean) {
        init(c)
        if (loading || refreshing) return
        loading = true
        status = "Loading football data..."
        tell(false)
        Bg.run {
            try {
                val st = state!!
                st.load()
                dataLoaded = true
                engine = newEngine(st)
            } catch (e: Throwable) {
                Crash.log("Football load", e)
                engine = null
            }
            loading = false
            status = ""
            tell(false)
            if (thenRefresh) refresh(c, true)
        }
    }

    fun refresh(c: Context, auto: Boolean) {
        init(c)
        if (refreshing) return
        refreshing = true
        progress = 0.0
        status = "Starting..."
        message = ""
        tell(false)
        val token = Store.fdoKey(c)
        val st = state!!
        Bg.run {
            var warnings: List<String> = emptyList()
            var failure: String? = null
            try {
                if (!dataLoaded) { st.load(); dataLoaded = true }
                warnings = Refresh.refresh(st, token, { m, f -> status = m; progress = f; tell(true) })
                status = "Working out the numbers..."
                progress = 0.96
                tell(true)
                val e = newEngine(st)
                engine = e
                val now = System.currentTimeMillis() / 1000
                try {
                    val sc = Scorecard.load(persist())
                    if (Scorecard.update(e, sc, now)) Scorecard.save(persist(), sc)
                    val pc = Playercard.load(persist())
                    if (Playercard.update(e, pc, now)) Playercard.save(persist(), pc)
                } catch (x: Throwable) {
                    warnings = warnings + "Scorecards: ${x.message}"
                }
            } catch (x: Throwable) {
                Crash.log("Football refresh", x)
                failure = x.message ?: x.javaClass.simpleName
            }
            refreshing = false
            status = ""
            val extra = warnings.filter { !it.startsWith("No football-data.org key") }
            message = when {
                failure != null && auto -> "Auto refresh failed (will try again in an hour). Press Refresh now to retry."
                failure != null -> "The refresh stopped: $failure"
                extra.isEmpty() -> "Football data updated"
                else -> "Updated, with ${extra.size} warning${if (extra.size == 1) "" else "s"}: ${extra.first()}"
            }
            tell(false)
        }
    }

    fun hasData(): Boolean = state?.hasData == true
}

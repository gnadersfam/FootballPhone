package com.lounge.phone

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Stats tab: Top 5, Form, Expected table, Scorecard, My Picks and Watch-list. Same figures as the PC app. */
class StatsPage(act: MainActivity) : ScrollPage(act) {
    private var sel = Store.str(act, "stats_view", "top5")
    private var formLeague = "PL"
    private var formN = 6
    private var expLeague = "PL"
    private var cardMode = "match"
    private var cardLeague = "all"
    private var cardSrc = "all"

    // Top 5
    private class Group(val first: Long, val items: MutableList<Pair<String, Fixture>>, val times: MutableList<String>) { var label = "" }
    private class Pick(val r: PRow, val fx: Fixture, val avg: Double?)
    private class T5(val matches: String, val booked: List<Pick>, val fouls: List<Pick>, val planned: Int, val confirmed: Int, val hadRows: Boolean)
    private var t5Sel = 0
    private var groups: List<Group> = emptyList()
    private val t5Cache = HashMap<String, T5>()
    private val t5Busy = HashSet<String>()
    private var t5Built = ""

    private fun go(v: String) { sel = v; Store.putStr(act, "stats_view", v); refresh() }

    override fun build() {
        col.addView(Ui.h1(act, "Stats"))
        col.addView(Ui.chips(act,
            Triple("Top 5", sel == "top5") { go("top5") },
            Triple("Form", sel == "form") { go("form") },
            Triple("Expected", sel == "exp") { go("exp") },
            Triple("Scorecard", sel == "card") { go("card") },
            Triple("My Picks", sel == "picks") { go("picks") },
            Triple("Watch-list", sel == "watch") { go("watch") }))
        val e = engineOrNote() ?: return
        when (sel) {
            "form" -> form(e)
            "exp" -> expected(e)
            "card" -> scorecard(e)
            "picks" -> picksView(e)
            "watch" -> watchView(e)
            else -> top5(e)
        }
    }

    // ------------------------------------------------------------------ Top 5
    private fun buildGroups(st: FootState) {
        val now = nowSec()
        val rows = ArrayList<Triple<Long, String, Fixture>>()
        for (lk in Leagues.ORDER) for (fx in (st.fixtures[lk] ?: emptyList<Fixture>())) {
            val t = ko(fx)
            if (t == 0L || t < now - 3 * 3600 || t > now + 7 * 86400) continue
            rows.add(Triple(t, lk, fx))
        }
        rows.sortBy { it.first }
        val out = ArrayList<Group>()
        for ((t, lk, fx) in rows) {
            val g = out.lastOrNull()
            if (g != null && Skin.dayDate(g.first) == Skin.dayDate(t) && t - g.first <= 30 * 60) { g.items.add(lk to fx); g.times.add(Skin.hhmm(t)) }
            else out.add(Group(t, arrayListOf(lk to fx), arrayListOf(Skin.hhmm(t))))
        }
        for (g in out) g.label = Skin.dayDate(g.first) + " " + g.times.distinct().sorted().joinToString("/")
        groups = out
    }

    private fun perGame(e: Engine, id: String, stat: Int): Double? {
        val a = e.players[id] ?: return null
        val g = a.start.apps + a.sub.apps
        if (g == 0) return null
        return (a.start.s[stat] + a.sub.s[stat]).toDouble() / g
    }

    private fun work(e: Engine, g: Group): T5 {
        val all = ArrayList<Pair<PRow, Fixture>>()
        var planned = 0; var confirmed = 0
        for ((lk, fx) in g.items) {
            val mv = try { e.matchView(lk, fx) } catch (x: Exception) { Crash.log("Top 5 page", x); continue }
            planned++
            if (mv.lineupStatus == "Confirmed lineups") confirmed++
            for (r in mv.players) {
                if (r.flag == "SUSPENDED") continue
                val a = e.players[r.id]
                val games = if (a != null) a.start.apps + a.sub.apps else 0
                val starts = a?.start?.apps ?: 0
                if (games < MIN_GAMES || starts < MIN_STARTS) continue
                all.add(r to fx)
            }
        }
        val booked = all.sortedByDescending { it.first.booked }.take(5).map { Pick(it.first, it.second, perGame(e, it.first.id, S_YC)) }
        val fouls = all.sortedByDescending { it.first.fouls[0] }.take(5).map { Pick(it.first, it.second, perGame(e, it.first.id, S_FOULS)) }
        return T5(g.items.joinToString(",  ") { "${it.second.home} v ${it.second.away}" }, booked, fouls, planned, confirmed, all.isNotEmpty())
    }

    private fun top5(e: Engine) {
        val st = Football.state ?: return
        col.addView(Ui.note(act, "Pick a kick-off time. Chance = this match; average = this season, per game. Two players from the same match are fine."))
        val sig = (st.fixtures.values.sumOf { it.size }).toString() + "|" + e.players.size + "|" + System.identityHashCode(e)
        if (sig != t5Built) { t5Cache.clear(); t5Built = sig; t5Sel = 0 }
        buildGroups(st)
        if (groups.isEmpty()) { col.addView(Ui.note(act, "No upcoming matches in the next 7 days. Press Refresh now to download fixtures.", Skin.TEXT)); return }
        if (t5Sel >= groups.size) t5Sel = 0
        col.addView(Ui.chips(act, *groups.mapIndexed { i, g -> Triple(g.label, i == t5Sel) { t5Sel = i; refresh() } }.toTypedArray()))
        val g = groups[t5Sel]
        val res = t5Cache[g.label]
        if (res == null) {
            col.addView(Ui.note(act, "Working out the lists for ${g.label}...", Skin.TEXT))
            if (t5Busy.add(g.label)) {
                val label = g.label
                Bg.run {
                    val r = try { work(e, g) } catch (x: Throwable) { Crash.log("Top 5 page", x); null }
                    Bg.ui { t5Busy.remove(label); if (r != null) t5Cache[label] = r; if (sel == "top5") refresh() }
                }
            }
            return
        }
        col.addView(Ui.note(act, "Matches: ${res.matches}"))
        t5Table("Top 5 most likely to be booked", "Chance booked", "Bookings per game", res.booked) { it.r.booked }
        t5Table("Top 5 most likely to commit a foul", "Chance of a foul", "Fouls per game", res.fouls) { it.r.fouls[0] }
        val left = " Players with under $MIN_GAMES games, or no starts yet, this season are left out."
        col.addView(Ui.note(act, when {
            !res.hadRows -> "No player data for these matches yet. Press Refresh now."
            res.confirmed == res.planned -> "Lineups confirmed for all ${res.planned} match${if (res.planned == 1) "" else "es"}.$left"
            else -> "Lineups confirmed for ${res.confirmed} of ${res.planned} match${if (res.planned == 1) "" else "es"}; the rest use predicted lineups, so these lists can change when team sheets come out.$left"
        }))
    }

    private fun t5Table(title: String, chanceHead: String, avgHead: String, list: List<Pick>, chance: (Pick) -> Double) {
        col.addView(Ui.h2(act, title))
        val heads = listOf(Hd("#", 30), Hd("Player", 170, Gravity.START), Hd("Team", 120, Gravity.START), Hd("Match", 200, Gravity.START), Hd(chanceHead, 100), Hd(avgHead, 110))
        val rows = list.mapIndexed { i, p ->
            listOf(Cv("${i + 1}", Skin.MUTED), Cv(p.r.name, bold = true, faceId = p.r.id, faceName = p.r.name, faceTeam = p.r.team), Cv(p.r.team, crest = p.r.team),
                Cv("${p.fx.home} v ${p.fx.away}", Skin.MUTED), Cv(Ui.pct(chance(p)), Skin.ACCENT, true), Cv(p.avg?.let { "%.2f".format(it) } ?: "-"))
        }
        col.addView(PTable.build(act, heads, rows, fixed = 2, onRow = { i -> val lk = Leagues.ORDER.firstOrNull { l -> Football.state?.fixtures?.get(l)?.any { it.same(list[i].fx) } == true }; if (lk != null) act.openMatch(lk, list[i].fx) }))
    }

    // ------------------------------------------------------------------ Form and Expected
    private fun leagueChips(cur: String, set: (String) -> Unit) = Ui.chips(act,
        Triple("Premier League", cur == "PL") { set("PL") }, Triple("Championship", cur == "ELC") { set("ELC") })

    private fun form(e: Engine) {
        col.addView(Ui.note(act, "Every team ranked on its last few league results. Tap a team for its match log."))
        col.addView(leagueChips(formLeague) { formLeague = it; refresh() })
        col.addView(Ui.chips(act, *listOf(5, 6, 8, 10).map { n -> Triple("Last $n", formN == n) { formN = n; refresh() } }.toTypedArray()))
        if (e.formTable(formLeague, formN).isEmpty()) { col.addView(Ui.note(act, "No results yet. Press Refresh now.", Skin.TEXT)); return }
        col.addView(FormTables.table(act, e, formLeague, formN, emptySet(), false), Ui.lp(-1, -2, 6, 0, act))
        col.addView(Ui.note(act, "xG is the quality of chances created (xGF) and allowed (xGA); xGD is the difference."))
    }

    private fun expected(e: Engine) {
        col.addView(Ui.note(act, "The league table the xG says the teams deserve, beside the real one. Amber = more points than the chances earned; green = fewer, could climb."))
        col.addView(leagueChips(expLeague) { expLeague = it; refresh() })
        val list = e.expectedTable(expLeague)
        if (list.isEmpty()) { col.addView(Ui.note(act, "No results yet. Press Refresh now.", Skin.TEXT)); return }
        val heads = listOf(Hd("#", 32), Hd("Team", 140, Gravity.START), Hd("P", 36), Hd("Pts", 44), Hd("xPts", 56), Hd("Pts gap", 64), Hd("xG pos", 60), Hd("Places", 60))
        val rows = list.map { r ->
            val gapColor = if (r.ptsGap > 1.5) Skin.AMBER else if (r.ptsGap < -1.5) Skin.GREEN else Skin.TEXT
            listOf(Cv("${r.pos}", Skin.MUTED), Cv(r.team, bold = true, crest = r.team), Cv("${r.P}"), Cv("${r.Pts}", Skin.ACCENT, true), Cv("%.1f".format(r.xPts), Skin.MUTED),
                Cv(Ui.sign(r.ptsGap), gapColor, true), Cv("${r.xpos}", Skin.MUTED), Cv(Ui.sign(r.posGap.toDouble(), 0), gapColor))
        }
        col.addView(PTable.build(act, heads, rows, fixed = 2, onRow = { i -> act.openTeam(expLeague, list[i].team) }), Ui.lp(-1, -2, 6, 0, act))
        col.addView(Ui.note(act, "Places: + means the team sits that many places higher than the chances deserve. Matches without xG data count real goals."))
    }

    // ------------------------------------------------------------------ Scorecard
    private fun scorecard(e: Engine) {
        val io = Football.persist()
        col.addView(Ui.chips(act,
            Triple("Match predictions", cardMode == "match") { cardMode = "match"; refresh() },
            Triple("Player figures", cardMode == "player") { cardMode = "player"; refresh() }))
        col.addView(Ui.chips(act,
            Triple("All leagues", cardLeague == "all") { cardLeague = "all"; refresh() },
            Triple("Premier League", cardLeague == "PL") { cardLeague = "PL"; refresh() },
            Triple("Championship", cardLeague == "ELC") { cardLeague = "ELC"; refresh() }))
        col.addView(Ui.chips(act,
            Triple("Live + replayed", cardSrc == "all") { cardSrc = "all"; refresh() },
            Triple("Live only", cardSrc == "live") { cardSrc = "live"; refresh() },
            Triple("Replayed only", cardSrc == "replay") { cardSrc = "replay"; refresh() }))
        val lk = if (cardLeague == "all") null else cardLeague
        val src = if (cardSrc == "all") null else cardSrc
        if (cardMode == "match") {
            val recs = Scorecard.load(io)
            val s = Scorecard.summarise(Scorecard.scored(recs, lk, src))
            col.addView(Ui.note(act, "Live = predicted before kick-off (the honest test). Replayed = rebuilt for matches already played, using only earlier results. Waiting for results: ${Scorecard.pendingCount(recs)}."))
            if (s.n == 0) { col.addView(Ui.note(act, "Nothing to score yet. Press Refresh now.", Skin.TEXT)); return }
            col.addView(Ui.h2(act, "${s.n} matches scored"))
            val h1 = listOf(Hd("Market", 190, Gravity.START), Hd("Matches", 66), Hd("Right", 60), Hd("Confidence", 82), Hd("Better than guessing", 130))
            col.addView(PTable.build(act, h1, s.markets.map { m ->
                listOf(Cv(m.label, bold = true), Cv("${m.n}"), Cv(Ui.pct(m.hit), Skin.ACCENT, true), Cv(m.conf?.let { Ui.pct(it) } ?: "-", Skin.MUTED),
                    Cv(m.skill?.let { Ui.sign(it) + "%" } ?: "-", if ((m.skill ?: 0.0) >= 0) Skin.GREEN else Skin.RED))
            }, fixed = 1))
            col.addView(Ui.h2(act, "Is the confidence trustworthy?"))
            val h2 = listOf(Hd("Result pick when confidence is...", 190, Gravity.START), Hd("Matches", 66), Hd("Said", 60), Hd("Happened", 82))
            col.addView(PTable.build(act, h2, s.bands.map { b ->
                listOf(Cv(b.label.removePrefix("Result pick when confidence is "), bold = true), Cv("${b.n}"), Cv(Ui.pct(b.conf), Skin.MUTED), Cv(Ui.pct(b.hit), Skin.ACCENT, true))
            }, fixed = 1))
            col.addView(Ui.note(act, "\"Better than guessing\" compares with just using how often home / draw / away happened before; above 0 is better."))
        } else {
            val recs = Playercard.load(io)
            val s = Playercard.summarise(Playercard.scored(recs, lk, src))
            col.addView(Ui.note(act, "Each player percentage marked against what the players really did, starters and substitutes separately. Waiting for results: ${Playercard.pendingCount(recs)}."))
            if (s.figures.isEmpty()) { col.addView(Ui.note(act, "Nothing to score yet. Press Refresh now.", Skin.TEXT)); return }
            val h1 = listOf(Hd("Figure", 180, Gravity.START), Hd("Who", 90, Gravity.START), Hd("Players", 66), Hd("Said", 60), Hd("Happened", 82), Hd("Better than flat", 110))
            col.addView(PTable.build(act, h1, s.figures.map { f ->
                listOf(Cv(f.label, bold = true), Cv(f.role, Skin.MUTED), Cv("${f.n}"), Cv(Ui.pct(f.pred), Skin.MUTED), Cv(Ui.pct(f.act), Skin.ACCENT, true),
                    Cv(f.skill?.let { Ui.sign(it) + "%" } ?: "-", if ((f.skill ?: 0.0) >= 0) Skin.GREEN else Skin.RED))
            }, fixed = 1))
            col.addView(Ui.h2(act, "By the chance the model gave"))
            val h2 = listOf(Hd("Who", 90, Gravity.START), Hd("Chance given", 120, Gravity.START), Hd("Players", 66), Hd("Said", 60), Hd("Happened", 82))
            col.addView(PTable.build(act, h2, s.bands.map { b ->
                listOf(Cv(b.role, Skin.MUTED), Cv(b.band, bold = true), Cv("${b.n}"), Cv(Ui.pct(b.pred), Skin.MUTED), Cv(Ui.pct(b.act), Skin.ACCENT, true))
            }, fixed = 1))
            col.addView(Ui.note(act, "A 30% chance should happen about 30% of the time. \"Better than flat\" compares with giving everyone the overall average."))
        }
    }

    // ------------------------------------------------------------------ My Picks
    private fun picksView(e: Engine) {
        val picks = Football.picks()
        val rows = picks.scoredRows(picks.loadPicks(), e)
        if (rows.isEmpty()) {
            col.addView(Ui.note(act, "No picks yet. Open a match (Leagues tab) and choose Home, Draw or Away under \"Your pick\" before kick-off.", Skin.TEXT))
            return
        }
        val s = picks.summarise(rows)
        col.addView(Ui.note(act, "Picks made: ${s.made}   Played: ${s.scored}" + if (s.scored > 0) "   You got ${s.you} right, the model ${s.model}, both ${s.both}." else "", Skin.TEXT))
        val heads = listOf(Hd("Match", 200, Gravity.START), Hd("Date", 84), Hd("You", 64), Hd("Model", 64), Hd("Model chances H / D / A", 140), Hd("Result", 70))
        col.addView(PTable.build(act, heads, rows.map { r ->
            val you = if (r.youOk == null) Skin.TEXT else if (r.youOk) Skin.GREEN else Skin.RED
            val mod = if (r.modelOk == null) Skin.TEXT else if (r.modelOk) Skin.GREEN else Skin.RED
            listOf(Cv("${r.p.home} v ${r.p.away}", bold = true), Cv(r.p.date, Skin.MUTED), Cv(Picks.NAMES.getValue(r.p.pick), you, true), Cv(Picks.NAMES.getValue(r.model), mod),
                Cv(r.p.mp.joinToString(" / ") { Ui.pct(it) }, Skin.MUTED), Cv(r.res?.let { "${it[0]}-${it[1]}" } ?: "waiting", Skin.ACCENT))
        }, fixed = 1), Ui.lp(-1, -2, 4, 0, act))
        if (s.differ.isNotEmpty()) col.addView(Ui.note(act, "Where you and the model differed: you were right ${s.differ.count { it.youOk == true }} of ${s.differ.size} times."))
    }

    // ------------------------------------------------------------------ Watch-list
    private fun watchView(e: Engine) {
        val picks = Football.picks()
        val list = picks.loadWatch()
        if (list.isEmpty()) {
            col.addView(Ui.note(act, "Nobody on your watch-list yet. Open a match, tap a player in the table and choose \"Add to watch-list\".", Skin.TEXT))
            return
        }
        col.addView(Ui.note(act, "Each player's chances in their team's next three matches. Tap a name for their card."))
        val st = Football.state ?: return
        for (w in list) {
            val head = LinearLayout(act)
            head.gravity = Gravity.CENTER_VERTICAL
            val px = Ui.dp(act, 34)
            head.addView(Crest.face(act, w.name, w.id, w.team, 34), LinearLayout.LayoutParams(px, px).apply { rightMargin = Ui.dp(act, 10) })
            val names = LinearLayout(act)
            names.orientation = LinearLayout.VERTICAL
            names.addView(Ui.text(act, "★ ${w.name}", 15f, Skin.ACCENT, true))
            names.addView(Ui.text(act, w.team, 12f, Skin.MUTED))
            names.setOnClickListener { act.openPlayer(w.id) }
            head.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
            head.addView(Ui.button(act, "Remove") { picks.toggleWatch(w.id, w.name, w.team); act.toast("${w.name} removed"); refresh() })
            col.addView(head, Ui.lp(-1, -2, 12, 4, act))
            val rows = ArrayList<List<Cv>>()
            for (lk in Leagues.ORDER) {
                val next = (st.fixtures[lk] ?: emptyList<Fixture>()).filter { (it.home == w.team || it.away == w.team) && ko(it) > nowSec() - 3 * 3600 }.sortedBy { it.utc }.take(3)
                for (fx in next) {
                    val mv = try { e.matchView(lk, fx) } catch (x: Exception) { null }
                    val r = mv?.players?.firstOrNull { it.id == w.id }
                    val opp = if (fx.home == w.team) "v ${fx.away}" else "at ${fx.home}"
                    rows.add(if (r == null) listOf(Cv(opp, bold = true), Cv(Skin.dayDate(ko(fx)), Skin.MUTED), Cv("not in the expected squad", Skin.MUTED), Cv(""), Cv(""), Cv(""), Cv(""))
                    else listOf(Cv(opp, bold = true), Cv(Skin.dayDate(ko(fx)), Skin.MUTED), Cv(Ui.pct(r.booked), Skin.AMBER, true), Cv(Ui.pct(r.fouls[0])), Cv(Ui.pct(r.fouled[0])), Cv(Ui.pct(r.shots[0])), Cv(Ui.pct(r.sot[0]))))
                }
            }
            if (rows.isEmpty()) col.addView(Ui.note(act, "No upcoming matches saved for ${w.team}."))
            else col.addView(PTable.build(act, listOf(Hd("Match", 150, Gravity.START), Hd("Date", 84), Hd("Booked", 64), Hd("Fouls", 56), Hd("Fouled", 60), Hd("Shots", 56), Hd("On target", 74)), rows, fixed = 1))
        }
    }

    companion object {
        const val MIN_GAMES = 3
        const val MIN_STARTS = 1
    }
}

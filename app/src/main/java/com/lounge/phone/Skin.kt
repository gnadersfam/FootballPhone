package com.lounge.phone

import android.content.Context
import android.graphics.Color

/** One look: the seven themes of the PC app, with the same colours. */
class Theme(
    val key: String, val label: String, val dark: Boolean,
    val bg: Int, val panel: Int, val panel2: Int, val line: Int, val text: Int, val muted: Int,
    val accent: Int, val accent2: Int, val amber: Int, val red: Int, val green: Int, val grey: Int,
    val home: Int, val draw: Int, val away: Int, val tickBg: Int, val tickFg: Int, val strip: List<Int>, val sel: Int
)

object Skin {
    private fun c(hex: String) = Color.parseColor(hex)

    fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
        Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
        Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t))

    private fun dark(key: String, label: String, bg: String, panel: String, panel2: String, line: String, text: String,
                     muted: String, accent: String, accent2: String, sel: String, tickBg: String, tickFg: String,
                     strip: List<String>, home: String? = null, away: String? = null): Theme {
        val grey = mix(c(muted), c(bg), 0.2f)
        return Theme(key, label, true, c(bg), c(panel), c(panel2), c(line), c(text), c(muted), c(accent), c(accent2),
            c("#ffc857"), c("#ff7f93"), c("#5cf0b0"), grey, c(home ?: accent), grey, c(away ?: accent2),
            c(tickBg), c(tickFg), strip.map { c(it) }, c(sel))
    }

    val THEMES: List<Theme> = listOf(
        dark("hologram", "Hologram", "#0a0a14", "#10101c", "#1a1a2e", "#2a2444", "#f4f0ff", "#9a90bd", "#a259ff", "#00f0ff",
            "#2b2252", "#07070f", "#cbb8ff", listOf("#00f0ff", "#a259ff", "#ff4fa3", "#ffd166", "#00f0ff"), "#00f0ff", "#ff4fa3"),
        dark("carbon", "Carbon", "#0e0e0e", "#171717", "#222222", "#2c2c2c", "#ffffff", "#9a9a9a", "#ff3d00", "#ff9a6a",
            "#3a2018", "#0b0b0b", "#ff6a3d", listOf("#ff3d00", "#ff3d00"), "#ff6a3d", "#e8e8e8"),
        dark("synthwave", "Synthwave", "#150829", "#1d0c38", "#2a1248", "#4a2478", "#fdf4ff", "#c1a6e0", "#00f0ff", "#ff4fa3",
            "#3a1a66", "#14062a", "#00f0ff", listOf("#ff4fa3", "#00f0ff"), "#00f0ff", "#ff4fa3"),
        dark("floodlights", "Floodlights", "#08101f", "#0e1a33", "#142748", "#1f3358", "#f2f7ff", "#8aa3c8", "#4da3ff", "#bcd8ff",
            "#1d3a6b", "#060c19", "#bcd8ff", listOf("#4da3ff", "#ffffff", "#4da3ff"), "#4da3ff", "#ffd479"),
        dark("nebula", "Nebula", "#08050f", "#120a22", "#1c1030", "#3a1d4a", "#fbefe8", "#b49bb8", "#ff6a2a", "#c24bff",
            "#3a1a4a", "#08040f", "#ffb08a", listOf("#36e0ff", "#c24bff", "#ff3d9a", "#ff6a2a"), "#36e0ff", "#ff6a2a"),
        dark("neon", "Neon (older)", "#0b0d26", "#14173a", "#1c2050", "#2c3270", "#eef0ff", "#a9afd6", "#3be8ff", "#ff5cf0",
            "#27337a", "#080a1d", "#3be8ff", listOf("#3be8ff", "#ff5cf0")),
        Theme("classic", "Classic (plain)", false, c("#f0f0f0"), c("#ffffff"), c("#e8e8e8"), c("#dddddd"), c("#222222"), c("#777777"),
            c("#0078d7"), c("#0078d7"), c("#b26a00"), c("#c62828"), c("#2e7d32"), c("#9e9e9e"),
            c("#2e7d32"), c("#9e9e9e"), c("#c62828"), c("#e0e0e0"), c("#444444"), listOf(c("#777777"), c("#777777")), c("#cfe3f7"))
    )

    var t: Theme = THEMES[0]
        private set

    val BG get() = t.bg
    val PANEL get() = t.panel
    val PANEL2 get() = t.panel2
    val LINE get() = t.line
    val TEXT get() = t.text
    val MUTED get() = t.muted
    val ACCENT get() = t.accent
    val ACCENT2 get() = t.accent2
    val AMBER get() = t.amber
    val RED get() = t.red
    val GREEN get() = t.green
    val GREY get() = t.grey
    val HOME get() = t.home
    val DRAW get() = t.draw
    val AWAY get() = t.away
    val SEL get() = t.sel

    fun load(ctx: Context) = apply(Store.str(ctx, "theme", "hologram"))

    fun apply(key: String) {
        t = THEMES.firstOrNull { it.key == key } ?: THEMES[0]
    }

    /** (text colour, cell colour) for a green (good) or red cell; strength 0..1. Same maths as the PC app. Null on the plain Classic theme. */
    fun pill(good: Boolean, strength: Double): IntArray? {
        if (t.key == "classic") return null
        val col = if (good) GREEN else RED
        val k = 0.20f + 0.22f * Math.max(0.0, Math.min(1.0, strength)).toFloat()
        return intArrayOf(col, mix(PANEL2, col, k))
    }

    /** Colour a number against the average of its column: green if clearly above, red if clearly below. */
    fun relPill(v: Double, mean: Double, band: Double = 0.08, strong: Double = 0.5): IntArray? {
        if (mean == 0.0) return null
        val d = v / mean - 1.0
        if (Math.abs(d) < band) return null
        return pill(d > 0, (Math.abs(d) - band) / strong)
    }

    fun dayDate(ko: Long): String = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.UK).format(java.util.Date(ko * 1000))
    fun hhmm(ko: Long): String = java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(ko * 1000))
    fun dayTime(ko: Long): String = java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.UK).format(java.util.Date(ko * 1000))
}

package com.lounge.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers so every screen looks the same. Sizes are real density-independent units (they suit a phone). */
object Ui {
    fun dp(c: Context, v: Int): Int = Math.round(v * c.resources.displayMetrics.density)
    fun dpf(c: Context, v: Float): Float = v * c.resources.displayMetrics.density

    fun text(c: Context, s: String, sp: Float = 14f, color: Int = Skin.TEXT, bold: Boolean = false): TextView =
        TextView(c).apply {
            text = s
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    fun box(c: Context, fill: Int, stroke: Int, radiusDp: Int = 14, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dpf(c, radiusDp.toFloat())
            setStroke(dp(c, strokeDp), stroke)
        }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = box(c, Skin.PANEL, Skin.LINE)
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
    }

    fun h1(c: Context, s: String) = text(c, s, 22f, Skin.TEXT, true)
    fun h2(c: Context, s: String) = text(c, s, 16f, Skin.TEXT, true).apply { setPadding(0, dp(c, 18), 0, dp(c, 8)) }
    fun note(c: Context, s: String, color: Int = Skin.MUTED) = text(c, s, 13f, color).apply { setPadding(0, dp(c, 2), 0, dp(c, 4)) }

    fun button(c: Context, label: String, filled: Boolean = false, onClick: () -> Unit): Button =
        Button(c).apply {
            text = label
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(if (filled) (if (Skin.t.dark) 0xFF0A0614.toInt() else 0xFFFFFFFF.toInt()) else Skin.TEXT)
            background = if (filled) box(c, Skin.ACCENT, Skin.ACCENT, 12) else box(c, Skin.PANEL2, Skin.LINE, 12)
            minHeight = dp(c, 44)
            minimumHeight = dp(c, 44)
            setPadding(dp(c, 16), 0, dp(c, 16), 0)
            setOnClickListener { onClick() }
        }

    fun lp(w: Int, h: Int, top: Int = 0, bottom: Int = 0, c: Context? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h).apply {
            if (c != null) { topMargin = dp(c, top); bottomMargin = dp(c, bottom) }
        }

    fun center(): Int = Gravity.CENTER_VERTICAL

    /** One chip (a small rounded button that is filled when it is the chosen one). */
    fun chip(c: Context, label: String, on: Boolean, onClick: () -> Unit): TextView =
        TextView(c).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, if (on) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (on) (if (Skin.t.dark) 0xFF0A0614.toInt() else 0xFFFFFFFF.toInt()) else Skin.TEXT)
            background = if (on) box(c, Skin.ACCENT, Skin.ACCENT, 18) else box(c, Skin.PANEL2, Skin.LINE, 18)
            setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }

    /** A row of chips that scrolls sideways when it is wider than the screen. */
    fun chips(c: Context, vararg items: Triple<String, Boolean, () -> Unit>): View {
        val sv = android.widget.HorizontalScrollView(c)
        sv.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, dp(c, 4), 0, dp(c, 4))
        for ((label, on, act) in items)
            row.addView(chip(c, label, on, act), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(c, 8) })
        sv.addView(row, android.widget.FrameLayout.LayoutParams(-2, -2))
        return sv
    }

    /** A referee's name that opens that referee's card when tapped. */
    fun refLink(act: MainActivity, name: String, sp: Float = 13f, prefix: String = ""): TextView =
        text(act, prefix + name, sp, Skin.ACCENT, true).apply {
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            if (name.isNotEmpty() && !name.equals("TBC", true)) setOnClickListener { act.openReferee(name) }
        }

    fun pct(p: Double) = "%d%%".format(Math.round(p * 100).toInt())
    fun sign(v: Double, d: Int = 1) = (if (v > 0) "+" else "") + "%.${d}f".format(v)
    fun odds(o: DoubleArray?): String = o?.joinToString(" / ") { "%.2f".format(it) } ?: ""
    fun resColor(r: String) = when (r) { "W" -> Skin.GREEN; "D" -> Skin.AMBER; else -> Skin.RED }
    fun agreeColor(a: Agreement?) = when (a?.code) { "agree" -> Skin.GREEN; "disagree" -> Skin.RED; null -> Skin.MUTED; else -> Skin.AMBER }
}

/** The three result chances (home, draw, away) as one coloured bar. */
class ResultBar(c: Context, private val vals: DoubleArray, private val cols: IntArray) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val path = Path()
        path.addRoundRect(0f, 0f, w, h, h / 2f, h / 2f, Path.Direction.CW)
        canvas.clipPath(path)
        var x = 0f
        for (i in vals.indices) {
            paint.color = cols[i]
            val seg = (w * vals[i]).toFloat()
            canvas.drawRect(x, 0f, x + seg, h, paint)
            x += seg
        }
    }
}

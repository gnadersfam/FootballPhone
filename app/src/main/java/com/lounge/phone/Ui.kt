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

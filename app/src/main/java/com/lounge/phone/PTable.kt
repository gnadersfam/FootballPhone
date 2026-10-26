package com.lounge.phone

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** One cell of a table. [pill] = (text colour, cell colour) for the green / red marking. */
class Cv(
    val text: String, val color: Int = Skin.TEXT, val bold: Boolean = false, val pill: IntArray? = null,
    val crest: String? = null, val faceId: String? = null, val faceName: String = "", val faceTeam: String = "",
    /** when set, tapping just this cell does this (for example open a referee's card) instead of the row's action */
    val click: (() -> Unit)? = null
)

/** One column heading. [widthDp] is the column's width. */
class Hd(val title: String, val widthDp: Int, val align: Int = Gravity.CENTER)

/**
 * A table that suits a phone: the first column(s) stay fixed on the left while the rest scroll sideways. Tap a heading to sort
 * (the page does the sorting and redraws). Rows all have the same height so the two halves line up.
 */
object PTable {
    private const val ROW = 42
    private const val HEAD = 40

    fun build(
        c: Context, heads: List<Hd>, rows: List<List<Cv>>, fixed: Int = 1,
        sortCol: Int = -1, sortDesc: Boolean = true, onSort: ((Int) -> Unit)? = null, onRow: ((Int) -> Unit)? = null,
        size: Float = 13.5f
    ): View {
        val outer = LinearLayout(c)
        outer.orientation = LinearLayout.HORIZONTAL
        val nFixed = Math.min(fixed, heads.size)
        val left = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        val right = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        fun cell(h: Hd, v: Cv?, col: Int, head: Boolean): View {
            val w = Ui.dp(c, h.widthDp)
            val holder = LinearLayout(c)
            holder.orientation = LinearLayout.HORIZONTAL
            holder.gravity = Gravity.CENTER_VERTICAL or (if (h.align == Gravity.CENTER) Gravity.CENTER_HORIZONTAL else Gravity.START)
            holder.setPadding(Ui.dp(c, 6), 0, Ui.dp(c, 6), 0)
            val tv = TextView(c)
            tv.maxLines = 1
            tv.ellipsize = android.text.TextUtils.TruncateAt.END
            if (head) {
                val arrow = if (col == sortCol) (if (sortDesc) " ▼" else " ▲") else ""
                tv.text = h.title + arrow
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f)
                tv.setTextColor(if (col == sortCol) Skin.ACCENT else Skin.MUTED)
                tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
                tv.gravity = Gravity.CENTER_VERTICAL or (if (h.align == Gravity.CENTER) Gravity.CENTER_HORIZONTAL else Gravity.START)
                if (onSort != null) holder.setOnClickListener { onSort(col) }
            } else {
                v!!
                if (v.crest != null) {
                    val px = Ui.dp(c, 24)
                    holder.addView(Crest.badge(c, v.crest, 24), LinearLayout.LayoutParams(px, px).apply { rightMargin = Ui.dp(c, 6) })
                }
                if (v.faceId != null) {
                    val px = Ui.dp(c, 30)
                    holder.addView(Crest.face(c, v.faceName, v.faceId, v.faceTeam, 30), LinearLayout.LayoutParams(px, px).apply { rightMargin = Ui.dp(c, 6) })
                }
                tv.text = v.text
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size)
                tv.setTextColor(v.pill?.get(0) ?: v.color)
                if (v.bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
                if (v.click != null) {
                    tv.setTextColor(Skin.ACCENT)
                    tv.paintFlags = tv.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                    tv.setOnClickListener { v.click.invoke() }
                }
                tv.gravity = Gravity.CENTER_VERTICAL or (if (h.align == Gravity.CENTER) Gravity.CENTER_HORIZONTAL else if (h.align == Gravity.END) Gravity.END else Gravity.START)
                if (v.pill != null) {
                    tv.background = Ui.box(c, v.pill[1], v.pill[1], 8, 0)
                    tv.setPadding(Ui.dp(c, 6), Ui.dp(c, 3), Ui.dp(c, 6), Ui.dp(c, 3))
                    tv.gravity = Gravity.CENTER
                }
            }
            val hasIcon = holder.childCount > 0
            holder.addView(tv, if (hasIcon) LinearLayout.LayoutParams(0, -2, 1f) else LinearLayout.LayoutParams(if (v?.pill != null) -2 else -1, -2))
            holder.layoutParams = LinearLayout.LayoutParams(w, -1)
            return holder
        }

        fun rowOf(parent: LinearLayout, cols: IntRange, vals: List<Cv>?, heightDp: Int, index: Int): LinearLayout {
            val r = LinearLayout(c)
            r.orientation = LinearLayout.HORIZONTAL
            for (k in cols) r.addView(cell(heads[k], vals?.get(k), k, vals == null))
            if (vals == null) r.setBackgroundColor(Skin.PANEL)
            else {
                r.setBackgroundColor(if (index % 2 == 0) Skin.BG else Skin.PANEL)
                if (onRow != null) r.setOnClickListener { onRow(index) }
            }
            parent.addView(r, LinearLayout.LayoutParams(-2, Ui.dp(c, heightDp)))
            return r
        }

        val fixedCols = 0 until nFixed
        val scrollCols = nFixed until heads.size
        if (nFixed > 0) rowOf(left, fixedCols, null, HEAD, -1)
        if (heads.size > nFixed) rowOf(right, scrollCols, null, HEAD, -1)
        for ((i, r) in rows.withIndex()) {
            if (nFixed > 0) rowOf(left, fixedCols, r, ROW, i)
            if (heads.size > nFixed) rowOf(right, scrollCols, r, ROW, i)
        }
        if (nFixed > 0) outer.addView(left, LinearLayout.LayoutParams(-2, -2))
        if (heads.size > nFixed) {
            val sv = HorizontalScrollView(c)
            sv.isHorizontalScrollBarEnabled = false
            sv.addView(right, android.widget.FrameLayout.LayoutParams(-2, -2))
            outer.addView(sv, LinearLayout.LayoutParams(0, -2, 1f))
        }
        return outer
    }

    /** Total width of the fixed part plus the scrolling part, used when a table should be wide enough to fill the screen. */
    fun width(heads: List<Hd>): Int = heads.sumOf { it.widthDp }
}

package com.lounge.phone

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Line chart of a team's xG (chances created, solid) and xGA (chances conceded, solid) with dashed season averages. */
class TrendChart(c: Context) : View(c) {
    var title = ""
    var xg: List<Double> = emptyList()
    var xga: List<Double> = emptyList()
    var opps: List<String> = emptyList()
    var avgF: Double? = null
    var avgA: Double? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val padL = Ui.dpf(context, 30f); val padB = Ui.dpf(context, 20f); val padT = Ui.dpf(context, 26f)
        p.style = Paint.Style.FILL
        p.color = Skin.TEXT; p.textSize = Ui.dpf(context, 13f)
        canvas.drawText(title, 2f, Ui.dpf(context, 14f), p)
        p.color = Skin.HOME; p.textSize = Ui.dpf(context, 11f)
        canvas.drawText("xG (chances created)", 2f, Ui.dpf(context, 26f) - 2f, p)
        p.color = Skin.AWAY
        canvas.drawText("xGA (conceded)", Ui.dpf(context, 130f), Ui.dpf(context, 26f) - 2f, p)
        if (xg.isEmpty()) {
            p.color = Skin.MUTED
            canvas.drawText("Not enough matches yet", padL, h / 2, p)
            return
        }
        val all = xg + xga + listOfNotNull(avgF, avgA)
        val top = Math.max(2.0, Math.ceil(all.max() * 2) / 2.0).toFloat()
        val padT2 = padT + Ui.dpf(context, 6f)
        fun y(v: Double) = padT2 + (h - padT2 - padB) * (1f - (v.toFloat() / top))
        fun x(i: Int, n: Int) = padL + (w - padL - Ui.dpf(context, 8f)) * (if (n <= 1) 0.5f else i / (n - 1f))
        p.strokeWidth = 1f
        var g = 0.0
        val step = if (top <= 4f) 1.0 else 2.0
        while (g <= top + 0.001) {
            p.color = Skin.LINE
            canvas.drawLine(padL, y(g), w, y(g), p)
            p.color = Skin.MUTED; p.textSize = Ui.dpf(context, 10f)
            canvas.drawText("%d".format(g.toInt()), 2f, y(g) + 4f, p)
            g += step
        }
        fun dashed(v: Double?, color: Int) {
            if (v == null) return
            p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dpf(context, 1.5f); p.color = color
            p.pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
            canvas.drawLine(padL, y(v), w, y(v), p)
            p.pathEffect = null; p.style = Paint.Style.FILL
        }
        fun line(vals: List<Double>, color: Int) {
            val path = Path()
            p.style = Paint.Style.STROKE; p.strokeWidth = Ui.dpf(context, 2.5f); p.color = color; p.pathEffect = null
            vals.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i, vals.size), y(v)) else path.lineTo(x(i, vals.size), y(v)) }
            canvas.drawPath(path, p)
            p.style = Paint.Style.FILL
            vals.forEachIndexed { i, v -> canvas.drawCircle(x(i, vals.size), y(v), Ui.dpf(context, 3.5f), p) }
        }
        dashed(avgF, Skin.HOME); dashed(avgA, Skin.AWAY)
        line(xg, Skin.HOME); line(xga, Skin.AWAY)
        p.style = Paint.Style.FILL; p.color = Skin.MUTED; p.textSize = Ui.dpf(context, 9f)
        val n = xg.size
        for (i in 0 until n) if (i % Math.max(1, n / 5) == 0 || i == n - 1) {
            val label = if (i < opps.size) opps[i] else ""
            canvas.drawText(label, Math.max(0f, x(i, n) - Ui.dpf(context, 12f)), h - 4f, p)
        }
    }
}

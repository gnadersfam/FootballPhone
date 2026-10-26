package com.lounge.phone

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** Round club badges: the real badge when we have its picture, otherwise initials in the club's colour (same colours as the PC app). */
object Crest {
    private val CLUB = linkedMapOf(
        "arsenal" to 0xEF0107, "aston villa" to 0x7A1F45, "bournemouth" to 0xDA291C, "brentford" to 0xE30613,
        "brighton" to 0x0057B8, "burnley" to 0x6C1D45, "chelsea" to 0x034694, "crystal palace" to 0x1B458F,
        "everton" to 0x003399, "fulham" to 0x555555, "leeds" to 0x1D428A, "liverpool" to 0xC8102E,
        "manchester city" to 0x5B9BD0, "manchester united" to 0xDA291C, "newcastle" to 0x444444,
        "nottingham forest" to 0xDD0000, "sunderland" to 0xEB172B, "tottenham" to 0x1B2A6B,
        "west ham" to 0x7A263A, "wolverhampton" to 0xC28A00,
        "man city" to 0x5B9BD0, "man united" to 0xDA291C, "wolves" to 0xC28A00, "nott" to 0xDD0000, "spurs" to 0x1B2A6B,
        "sheffield united" to 0xC8102E, "sheffield utd" to 0xC8102E, "southampton" to 0xD71920, "leicester" to 0x0053A0,
        "ipswich" to 0x3A64A3, "norwich" to 0x00A650, "west brom" to 0x122F67, "sheffield wed" to 0x1B3F94
    )

    fun initials(name: String): String {
        val words = name.trim().split(Regex("[\\s\\-]+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> (words[0].take(1) + words[words.size - 1].take(1)).uppercase()
        }
    }

    fun colour(team: String): Int {
        val k = team.lowercase()
        for ((n, col) in CLUB) if (k.contains(n)) return 0xFF000000.toInt() or col
        val h = (Math.abs(k.hashCode()) % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(h, 0.55f, 0.45f))
    }

    fun badge(c: Context, team: String, sizeDp: Int): View {
        val f = FrameLayout(c)
        val url = TeamLogos.url(team)
        if (url.isEmpty()) {
            f.addView(TextView(c).apply {
                text = initials(team)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeDp * 0.36f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(colour(team)) }
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            f.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFF2F2F7.toInt()) }
            val iv = ImageView(c).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val p = Ui.dp(c, sizeDp / 8 + 1)
                setPadding(p, p, p, p)
            }
            f.addView(iv, FrameLayout.LayoutParams(-1, -1))
            Img.load(iv, url, 160, 160)
        }
        return f
    }
}

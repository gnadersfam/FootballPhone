package com.lounge.phone

import android.view.View

/** One tab of the app. */
interface Page {
    val view: View
    /** Build (or rebuild) what the page shows. Called when the page opens and when the data changes. */
    fun refresh()
}

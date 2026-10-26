package com.lounge.phone

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.util.concurrent.Executors

/** Loads poster pictures from the web into an ImageView, small and cached, so a long grid stays light. */
object Img {
    private val pool = Executors.newFixedThreadPool(4)
    private val cache = object : LruCache<String, Bitmap>(14 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val failed = HashSet<String>()
    private val loads = java.util.concurrent.atomic.AtomicInteger()

    fun clear() {
        cache.evictAll()
    }

    fun load(view: ImageView, url: String, maxW: Int, maxH: Int) {
        view.tag = url
        if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            view.setImageDrawable(null)
            return
        }
        val hit = cache.get(url)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        view.setImageDrawable(null)
        if (synchronized(failed) { url in failed }) return
        pool.execute {
            val bmp = try {
                fetch(url, maxW, maxH)
            } catch (e: Throwable) {
                null
            }
            if (bmp != null) {
                // Pictures live outside the normal app memory, and the system only tidies them up when it gets round to it.
                // Asking now and then keeps the total from creeping up while you scroll a long list.
                if (loads.incrementAndGet() % 40 == 0) System.gc()
                cache.put(url, bmp)
                Bg.ui { if (view.tag == url) view.setImageBitmap(bmp) }
            } else {
                synchronized(failed) { failed.add(url) }
            }
        }
    }

    /** Like load, but tries each address in turn until one gives a picture (first the Premier League photo, then ESPN). */
    fun loadAny(view: ImageView, urls: List<String>, maxW: Int, maxH: Int) {
        val list = urls.filter { it.startsWith("http://") || it.startsWith("https://") }
        val key = list.firstOrNull() ?: ""
        view.tag = key
        if (list.isEmpty()) { view.setImageDrawable(null); return }
        for (u in list) { val hit = cache.get(u); if (hit != null) { view.setImageBitmap(hit); return } }
        view.setImageDrawable(null)
        pool.execute {
            var bmp: Bitmap? = null
            for (u in list) {
                if (synchronized(failed) { u in failed }) continue
                val b = try { fetch(u, maxW, maxH) } catch (e: Throwable) { null }
                if (b != null) { cache.put(u, b); bmp = b; break } else synchronized(failed) { failed.add(u) }
            }
            if (bmp != null) {
                if (loads.incrementAndGet() % 40 == 0) System.gc()
                val got = bmp
                Bg.ui { if (view.tag == key) view.setImageBitmap(got) }
            }
        }
    }

    private fun fetch(url: String, maxW: Int, maxH: Int): Bitmap? {
        val data = Net.bytes(url, timeoutMs = 15000)
        if (data.size > 6_000_000) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxW && o.outHeight / (sample * 2) >= maxH) sample *= 2
        val o2 = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(data, 0, data.size, o2)
    }
}

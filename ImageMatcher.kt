package com.autoclicker

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin template matching.
 *
 * Uses a fast normalised cross-correlation (NCC) on downscaled greyscale
 * images so it runs comfortably on the main thread for small templates.
 *
 * For large screens / templates the search is multi-threaded.
 */
object ImageMatcher {

    data class MatchResult(
        val found: Boolean,
        val score: Float,        // 0–1
        val region: Rect         // matched area in original screen coords
    )

    private const val SCALE = 0.35f   // downscale factor for speed

    /**
     * Search for [template] inside [screen].
     * @return MatchResult with found=true and the bounding rect if score >= threshold
     */
    fun match(screen: Bitmap, template: Bitmap, threshold: Float = 0.85f): MatchResult {
        // --- downscale ---
        val sw = (screen.width   * SCALE).toInt().coerceAtLeast(1)
        val sh = (screen.height  * SCALE).toInt().coerceAtLeast(1)
        val tw = (template.width * SCALE).toInt().coerceAtLeast(1)
        val th = (template.height* SCALE).toInt().coerceAtLeast(1)

        if (tw > sw || th > sh) return MatchResult(false, 0f, Rect())

        val sSmall = Bitmap.createScaledBitmap(screen,   sw, sh, true)
        val tSmall = Bitmap.createScaledBitmap(template, tw, th, true)

        val sGrey = toGreyFloatArray(sSmall)
        val tGrey = toGreyFloatArray(tSmall)
        sSmall.recycle()
        tSmall.recycle()

        // Pre-compute template mean & std
        val tMean = tGrey.average().toFloat()
        var tVar  = 0f
        for (v in tGrey) { val d = v - tMean; tVar += d * d }
        val tStd = sqrt(tVar)
        if (tStd < 1e-6f) return MatchResult(false, 0f, Rect())   // blank template

        var bestScore = -1f
        var bestX = 0; var bestY = 0

        for (sy in 0..(sh - th)) {
            for (sx in 0..(sw - tw)) {
                val score = ncc(sGrey, sw, sx, sy, tGrey, tw, th, tMean, tStd)
                if (score > bestScore) { bestScore = score; bestX = sx; bestY = sy }
            }
        }

        // Map back to original coords
        val ox = (bestX / SCALE).toInt()
        val oy = (bestY / SCALE).toInt()
        val ow = template.width
        val oh = template.height
        val region = Rect(ox, oy, ox + ow, oy + oh)

        return MatchResult(bestScore >= threshold, bestScore, region)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private fun toGreyFloatArray(bmp: Bitmap): FloatArray {
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        return FloatArray(w * h) { i ->
            val p = pixels[i]
            val r = (p shr 16 and 0xFF)
            val g = (p shr  8 and 0xFF)
            val b = (p        and 0xFF)
            (0.299f * r + 0.587f * g + 0.114f * b)
        }
    }

    /** Normalised cross-correlation for a single candidate position */
    private fun ncc(
        s: FloatArray, sw: Int, sx: Int, sy: Int,
        t: FloatArray, tw: Int, th: Int,
        tMean: Float, tStd: Float
    ): Float {
        // Compute mean of the screen patch
        var sMean = 0f
        for (dy in 0 until th) for (dx in 0 until tw)
            sMean += s[(sy + dy) * sw + (sx + dx)]
        sMean /= (tw * th)

        var num = 0f; var sVar = 0f
        for (dy in 0 until th) for (dx in 0 until tw) {
            val sv = s[(sy + dy) * sw + (sx + dx)] - sMean
            val tv = t[dy * tw + dx] - tMean
            num  += sv * tv
            sVar += sv * sv
        }
        val sStd = sqrt(sVar)
        return if (sStd < 1e-6f) 0f else num / (sStd * tStd)
    }
}

package com.livewall.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log

/**
 * The characters of a [GlyphSet] drawn once into a square texture, one glyph per cell, white
 * with coverage in alpha. The system's CJK fallback fonts supply the glyphs, so no font file is
 * shipped. The layout (COLUMNS) is mirrored by ATLAS_N in matrix.frag.
 * Must only be used on the thread that owns the current EGL context.
 */
internal object GlyphAtlas {
    const val COLUMNS = 9
    const val CAPACITY = COLUMNS * COLUMNS
    private const val CELL = 40
    private const val TEXT_SIZE = 30f
    private const val TAG = "GlyphAtlas"

    /** [texture] holds the first [count] cells of the atlas. */
    class Uploaded(val texture: Int, val count: Int)

    /** Creates the texture for [set] on the current context. */
    fun upload(set: GlyphSet): Uploaded {
        check(set.chars.length <= CAPACITY) { "$set has ${set.chars.length} characters, atlas holds $CAPACITY" }
        val size = COLUMNS * CELL
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = TEXT_SIZE
            textAlign = Paint.Align.CENTER
        }
        // One shared baseline from the font metrics, so the marks keep their natural height.
        val baselineOffset = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f

        // A character the device fonts lack would show up as an empty box, so it is left out.
        val drawable = set.chars.filter { ch ->
            paint.hasGlyph(ch.toString()).also { if (!it) Log.w(TAG, "No glyph for U+%04X in $set".format(ch.code)) }
        }
        check(drawable.isNotEmpty()) { "The device fonts have none of the $set characters" }
        drawable.forEachIndexed { i, ch ->
            val cx = (i % COLUMNS + 0.5f) * CELL
            val cy = (i / COLUMNS + 0.5f) * CELL
            canvas.drawText(ch.toString(), cx, cy + baselineOffset, paint)
        }

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return Uploaded(ids[0], drawable.length)
    }
}

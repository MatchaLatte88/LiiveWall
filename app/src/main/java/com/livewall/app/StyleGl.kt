package com.livewall.app

import android.graphics.Color
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Two-pass renderer. Pass 1 runs the style's fragment shader into a small offscreen texture
 * (the expensive part, at the render scale of the screen size). Pass 2 stretches that texture
 * over the real surface with bilinear filtering and adds a little dither against banding.
 * Style programs are compiled on first use and kept until [release].
 * Must only be used on the thread that owns the current EGL context.
 *
 * [loadShader] returns the fragment source for a style's shader file name.
 */
internal class StyleGl(private val loadShader: (String) -> String) {

    private class StyleProgram(val id: Int) {
        val aspect = GLES20.glGetUniformLocation(id, "uAspect")
        val time = GLES20.glGetUniformLocation(id, "uTime")
        val intensity = GLES20.glGetUniformLocation(id, "uIntensity")
        val particles = GLES20.glGetUniformLocation(id, "uParticles")
        val colDeep = GLES20.glGetUniformLocation(id, "uColDeep")
        val colMid = GLES20.glGetUniformLocation(id, "uColMid")
        val colShallow = GLES20.glGetUniformLocation(id, "uColShallow")
        val colGlow = GLES20.glGetUniformLocation(id, "uColGlow")
        val colA = GLES20.glGetUniformLocation(id, "uColA")
        val colB = GLES20.glGetUniformLocation(id, "uColB")
        val colC = GLES20.glGetUniformLocation(id, "uColC")
        val multi = GLES20.glGetUniformLocation(id, "uMulti")
        val fill = GLES20.glGetUniformLocation(id, "uFill")
        val touch = GLES20.glGetUniformLocation(id, "uTouch")
        val ripple = GLES20.glGetUniformLocation(id, "uRipple")
        val glyphs = GLES20.glGetUniformLocation(id, "uGlyphs")
        val glyphCount = GLES20.glGetUniformLocation(id, "uGlyphCount")
    }

    private val styleCache = HashMap<WallpaperStyle, StyleProgram>()
    private var blitProgram = 0
    private var blitTexture = 0
    private var texture = 0
    private var glyphTexture = 0
    private var glyphCount = 0
    private var glyphSet: GlyphSet? = null
    private var framebuffer = 0
    private var targetWidth = 0
    private var targetHeight = 0
    private var targetScale = 0f

    // One oversized triangle covers the whole screen without an index buffer.
    private val triangle: FloatBuffer = ByteBuffer.allocateDirect(6 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
            position(0)
        }

    fun init() {
        blitProgram = buildProgram(VERTEX_SOURCE, BLIT_FRAGMENT_SOURCE)
        blitTexture = GLES20.glGetUniformLocation(blitProgram, "uTex")
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun draw(
        surfaceWidth: Int,
        surfaceHeight: Int,
        time: Float,
        settings: AuroraSettings,
        colors: PaletteColors,
        renderScale: Float,
        touch: TouchState,
    ) {
        val program = styleCache.getOrPut(settings.style) {
            StyleProgram(buildProgram(VERTEX_SOURCE, loadShader(settings.style.shader)))
        }
        ensureTarget(surfaceWidth, surfaceHeight, renderScale)

        // Pass 1: the style into the small target. Uniforms a shader does not declare have
        // location -1, which GL silently ignores.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glViewport(0, 0, targetWidth, targetHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program.id)
        GLES20.glUniform1f(program.aspect, targetWidth.toFloat() / targetHeight)
        GLES20.glUniform1f(program.time, time)
        GLES20.glUniform1f(program.intensity, settings.intensity)
        GLES20.glUniform1f(program.particles, settings.particles)
        setColor(program.colDeep, colors.deep)
        setColor(program.colMid, colors.mid)
        setColor(program.colShallow, colors.shallow)
        setColor(program.colGlow, colors.glow)
        setColor(program.colA, colors.primary)
        setColor(program.colB, colors.secondary)
        setColor(program.colC, colors.tertiary)
        GLES20.glUniform1f(program.multi, settings.multicolorAmount)
        GLES20.glUniform1f(program.fill, settings.fillAmount)
        GLES20.glUniform3f(program.touch, touch.x, touch.y, touch.hold)
        GLES20.glUniform3f(program.ripple, touch.rippleX, touch.rippleY, touch.rippleAge)
        if (program.glyphs >= 0) {
            if (glyphSet != settings.glyphSet) {
                deleteGlyphs()
                val atlas = GlyphAtlas.upload(settings.glyphSet)
                glyphTexture = atlas.texture
                glyphCount = atlas.count
                glyphSet = settings.glyphSet
            }
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glyphTexture)
            GLES20.glUniform1i(program.glyphs, 0)
            GLES20.glUniform1f(program.glyphCount, glyphCount.toFloat())
        }
        drawTriangle()

        // Pass 2: stretch to the real surface.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(blitProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(blitTexture, 0)
        drawTriangle()
    }

    fun release() {
        deleteTarget()
        styleCache.values.forEach { GLES20.glDeleteProgram(it.id) }
        styleCache.clear()
        deleteGlyphs()
        if (blitProgram != 0) GLES20.glDeleteProgram(blitProgram)
        blitProgram = 0
    }

    private fun ensureTarget(surfaceWidth: Int, surfaceHeight: Int, scale: Float) {
        val w = (surfaceWidth * scale).toInt().coerceAtLeast(1)
        val h = (surfaceHeight * scale).toInt().coerceAtLeast(1)
        if (texture != 0 && w == targetWidth && h == targetHeight && scale == targetScale) return

        deleteTarget()
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glGenFramebuffers(1, ids, 0)
        framebuffer = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) {
            "Offscreen framebuffer incomplete: 0x${status.toString(16)} (${w}x$h)"
        }
        targetWidth = w
        targetHeight = h
        targetScale = scale
    }

    private fun deleteGlyphs() {
        if (glyphTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(glyphTexture), 0)
        glyphTexture = 0
        glyphCount = 0
        glyphSet = null
    }

    private fun deleteTarget() {
        if (texture != 0) GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        if (framebuffer != 0) GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
        texture = 0
        framebuffer = 0
        targetWidth = 0
        targetHeight = 0
        targetScale = 0f
    }

    private fun drawTriangle() {
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, triangle)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    private fun setColor(location: Int, argb: Int) {
        GLES20.glUniform3f(location, Color.red(argb) / 255f, Color.green(argb) / 255f, Color.blue(argb) / 255f)
    }

    private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        check(program != 0) { "glCreateProgram failed" }
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glBindAttribLocation(program, 0, "aPos")
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("Shader program link failed: $log")
        }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        check(shader != 0) { "glCreateShader failed" }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("Shader compile failed: $log")
        }
        return shader
    }

    private companion object {
        const val VERTEX_SOURCE = """
            attribute vec2 aPos;
            varying vec2 vUv;
            void main() {
                vUv = aPos * 0.5 + 0.5;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """

        const val BLIT_FRAGMENT_SOURCE = """
            precision highp float;
            varying vec2 vUv;
            uniform sampler2D uTex;
            float hash12(vec2 p) {
                vec3 p3 = fract(vec3(p.xyx) * 0.1031);
                p3 += dot(p3, p3.yzx + 33.33);
                return fract((p3.x + p3.y) * p3.z);
            }
            void main() {
                vec3 c = texture2D(uTex, vUv).rgb;
                // static dither at full resolution hides 8-bit banding in the slow gradients
                c += (hash12(gl_FragCoord.xy) - 0.5) * (1.5 / 255.0);
                gl_FragColor = vec4(c, 1.0);
            }
        """
    }
}

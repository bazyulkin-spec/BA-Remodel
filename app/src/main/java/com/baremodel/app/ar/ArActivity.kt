package com.baremodel.app.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.baremodel.app.R
import com.baremodel.app.data.UiPrefs
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Живой AR: камера как фон, раскладка пола лежит на настоящем полу в масштабе 1:1.
 * Все состояния и ошибки выводятся строкой на экран — приложение тестируется удалённо,
 * и одного взгляда на статус должно хватать для диагноза.
 */
class ArActivity : Activity(), GLSurfaceView.Renderer {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(UiPrefs.wrap(newBase))
    }

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var statusView: TextView

    private var session: Session? = null
    private var installRequested = false
    private var errored = false

    @Volatile private var queuedTap: FloatArray? = null
    private var anchor: Anchor? = null

    // GL: фон камеры
    private var bgProgram = 0
    private var bgTexId = 0
    private var bgPosBuf: FloatBuffer = fb(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private var bgTexBuf: FloatBuffer = fb(FloatArray(8))

    // GL: пол
    private var floorProgram = 0
    private var floorTexId = 0
    @Volatile private var variantIdx = 0
    @Volatile private var variantPending = false
    @Volatile private var measureMode = false
    private var measureA: Anchor? = null
    private var measureB: Anchor? = null
    private lateinit var infoView: TextView
    private var floorBuf: FloatBuffer = fb(FloatArray(20))

    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val model = FloatArray(16)
    private val mv = FloatArray(16)
    private val mvp = FloatArray(16)

    @Volatile private var viewportChanged = false
    private var vw = 1
    private var vh = 1
    private var lastStatus = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUi()
        } catch (e: Throwable) {
            // даже жёсткий сбой при старте покажет причину и модель на экране
            val tv = TextView(this).apply {
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.BLACK)
                textSize = 13f
                setPadding(44, 90, 44, 44)
                text = getString(R.string.ar_error) + e.javaClass.simpleName +
                    (e.message?.let { ": $it" } ?: "") + "\n" + Build.MODEL
            }
            setContentView(tv)
        }
    }

    private fun buildUi() {
        surfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(this@ArActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { v, e ->
                if (e.action == MotionEvent.ACTION_DOWN) {
                    queuedTap = floatArrayOf(e.x, e.y)
                    v.performClick()
                }
                true
            }
        }

        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setBackgroundColor(Color.argb(150, 7, 9, 13))
            setPadding(28, 20, 28, 20)
            text = getString(R.string.ar_checking) + "  ·  " + Build.MODEL
        }

        infoView = TextView(this).apply {
            setTextColor(Color.rgb(207, 224, 255))
            textSize = 12f
            setBackgroundColor(Color.argb(120, 7, 9, 13))
            setPadding(28, 8, 28, 12)
            text = ArBridge.info
            visibility = if (ArBridge.info.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        }

        val measureBtn = Button(this).apply {
            text = getString(R.string.ar_measure)
            setOnClickListener {
                measureMode = !measureMode
                measureA?.detach()
                measureB?.detach()
                measureA = null
                measureB = null
                lastStatus = 0
                statusView.text = if (measureMode) {
                    getString(R.string.ar_measure_1)
                } else {
                    getString(R.string.ar_tap)
                }
            }
        }

        val variantBtn = Button(this).apply {
            text = ArBridge.variants.getOrNull(0)?.first ?: ""
            visibility = if (ArBridge.variants.size > 1) android.view.View.VISIBLE else android.view.View.GONE
            setOnClickListener {
                val list = ArBridge.variants
                if (list.size > 1) {
                    variantIdx = (variantIdx + 1) % list.size
                    variantPending = true
                    text = list[variantIdx].first
                }
            }
        }
        val resetBtn = Button(this).apply {
            text = getString(R.string.ar_reset)
            setOnClickListener {
                anchor?.detach()
                anchor = null
                status(R.string.ar_tap)
            }
        }
        val closeBtn = Button(this).apply {
            text = getString(R.string.ar_close)
            setOnClickListener { finish() }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(measureBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(variantBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(resetBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(closeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val root = FrameLayout(this)
        root.addView(surfaceView)
        val topBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                statusView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                infoView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            topBox,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP,
            ),
        )
        root.addView(
            buttons,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM,
            ),
        )
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (errored) return
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        status(R.string.ar_install)
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> Unit
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
                    status(R.string.ar_no_camera)
                    return
                }
                val s = Session(this)
                val config = Config(s).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    focusMode = Config.FocusMode.AUTO
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                s.configure(config)
                session = s
            } catch (e: UnavailableDeviceNotCompatibleException) {
                status(R.string.ar_unsupported)
                errored = true
                return
            } catch (e: Exception) {
                showError(e)
                return
            }
        }
        try {
            session?.resume()
            surfaceView.onResume()
            status(R.string.ar_move)
        } catch (e: CameraNotAvailableException) {
            showError(e)
            session = null
        } catch (e: Exception) {
            showError(e)
        }
    }

    override fun onPause() {
        super.onPause()
        surfaceView.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        anchor?.detach()
        measureA?.detach()
        measureB?.detach()
        session?.close()
        session = null
        ArBridge.clear()
    }

    // ---------- GLSurfaceView.Renderer ----------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        bgTexId = makeTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        bgProgram = makeProgram(BG_VERT, BG_FRAG)

        floorTexId = makeTexture(GLES20.GL_TEXTURE_2D)
        val bmp = ArBridge.floorBitmap
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, floorTexId)
        if (bmp != null && !bmp.isRecycled) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        } else {
            val one = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            one.put(byteArrayOf(0, 0, 0, 0)).position(0)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, one,
            )
        }
        floorProgram = makeProgram(FLOOR_VERT, FLOOR_FRAG)

        val w = ArBridge.widthM
        val h = ArBridge.heightM
        floorBuf = fb(
            floatArrayOf(
                -w / 2f, 0f, -h / 2f, 0f, 0f,
                w / 2f, 0f, -h / 2f, 1f, 0f,
                -w / 2f, 0f, h / 2f, 0f, 1f,
                w / 2f, 0f, h / 2f, 1f, 1f,
            ),
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        vw = width
        vh = height
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (errored) return
        try {
            if (viewportChanged) {
                @Suppress("DEPRECATION")
                s.setDisplayGeometry(windowManager.defaultDisplay.rotation, vw, vh)
                viewportChanged = false
            }
            if (variantPending) {
                variantPending = false
                ArBridge.variants.getOrNull(variantIdx)?.second?.let { vb ->
                    if (!vb.isRecycled) {
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, floorTexId)
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, vb, 0)
                    }
                }
            }
            s.setCameraTextureName(bgTexId)
            val frame = s.update()
            if (frame.hasDisplayGeometryChanged()) {
                bgPosBuf.position(0)
                bgTexBuf.position(0)
                frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, bgPosBuf,
                    Coordinates2d.TEXTURE_NORMALIZED, bgTexBuf,
                )
            }
            drawBackground()

            val cam = frame.camera
            if (cam.trackingState != TrackingState.TRACKING) {
                status(R.string.ar_move)
                return
            }

            queuedTap?.let { tap ->
                queuedTap = null
                for (hit in frame.hitTest(tap[0], tap[1])) {
                    val trackable = hit.trackable
                    if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                        if (measureMode) {
                            if (measureA == null) {
                                measureA = hit.createAnchor()
                                runOnUiThread { statusView.text = getString(R.string.ar_measure_2) }
                            } else {
                                measureB?.detach()
                                measureB = hit.createAnchor()
                                val a = measureA!!.pose
                                val b = measureB!!.pose
                                val dx = a.tx() - b.tx()
                                val dy = a.ty() - b.ty()
                                val dz = a.tz() - b.tz()
                                val dist = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                                val text = getString(R.string.ar_dist) + ": " +
                                    String.format(java.util.Locale.getDefault(), "%.2f", dist) + " " +
                                    getString(R.string.unit_m)
                                runOnUiThread { statusView.text = text }
                                // следующая пара измеряется от этой точки
                                measureA?.detach()
                                measureA = measureB
                                measureB = null
                            }
                        } else {
                            anchor?.detach()
                            anchor = hit.createAnchor()
                            status(R.string.ar_placed)
                        }
                        break
                    }
                }
            }

            if (measureMode) {
                drawFloorIfPlaced(cam)
                return
            }
            val a = anchor
            if (a == null) {
                val hasPlane = s.getAllTrackables(Plane::class.java)
                    .any { it.trackingState == TrackingState.TRACKING }
                status(if (hasPlane) R.string.ar_tap else R.string.ar_move)
                return
            }
            if (a.trackingState != TrackingState.TRACKING) return

            cam.getProjectionMatrix(proj, 0, 0.1f, 100f)
            cam.getViewMatrix(view, 0)
            a.pose.toMatrix(model, 0)
            Matrix.multiplyMM(mv, 0, view, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0)
            drawFloor()
        } catch (e: Throwable) {
            showError(e)
        }
    }

    /** Пол рисуется и в режиме измерения, если он уже поставлен. */
    private fun drawFloorIfPlaced(cam: com.google.ar.core.Camera) {
        val a = anchor ?: return
        if (a.trackingState != TrackingState.TRACKING) return
        if (cam.trackingState != TrackingState.TRACKING) return
        cam.getProjectionMatrix(proj, 0, 0.1f, 100f)
        cam.getViewMatrix(view, 0)
        a.pose.toMatrix(model, 0)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0)
        drawFloor()
    }

    // ---------- отрисовка ----------

    private fun drawBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(bgProgram)
        val aPos = GLES20.glGetAttribLocation(bgProgram, "aPos")
        val aTex = GLES20.glGetAttribLocation(bgProgram, "aTex")
        bgPosBuf.position(0)
        bgTexBuf.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, bgPosBuf)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, bgTexBuf)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, bgTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(bgProgram, "sTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glDepthMask(true)
    }

    private fun drawFloor() {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(floorProgram)
        val aPos = GLES20.glGetAttribLocation(floorProgram, "aPos")
        val aTex = GLES20.glGetAttribLocation(floorProgram, "aTex")
        floorBuf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 20, floorBuf)
        floorBuf.position(3)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 20, floorBuf)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(floorProgram, "uMvp"), 1, false, mvp, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(floorProgram, "uAlpha"), 0.92f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, floorTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(floorProgram, "sTex"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    // ---------- вспомогательное ----------

    private fun status(res: Int) {
        if (lastStatus == res) return
        lastStatus = res
        runOnUiThread { statusView.text = getString(res) + "  ·  " + Build.MODEL }
    }

    private fun showError(e: Throwable) {
        errored = true
        runOnUiThread {
            statusView.text = getString(R.string.ar_error) +
                e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "") +
                "  ·  " + Build.MODEL
        }
    }

    private fun makeTexture(target: Int): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(target, ids[0])
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun makeShader(type: Int, src: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, src)
        GLES20.glCompileShader(id)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(id)
            GLES20.glDeleteShader(id)
            throw RuntimeException("shader: $log")
        }
        return id
    }

    private fun makeProgram(vert: String, frag: String): Int {
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, makeShader(GLES20.GL_VERTEX_SHADER, vert))
        GLES20.glAttachShader(p, makeShader(GLES20.GL_FRAGMENT_SHADER, frag))
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("link: " + GLES20.glGetProgramInfoLog(p))
        return p
    }

    private companion object {
        fun fb(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().put(data).apply { position(0) }

        const val BG_VERT = """
            attribute vec4 aPos;
            attribute vec2 aTex;
            varying vec2 vTex;
            void main() { gl_Position = aPos; vTex = aTex; }
        """

        const val BG_FRAG = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTex;
            uniform samplerExternalOES sTex;
            void main() { gl_FragColor = texture2D(sTex, vTex); }
        """

        const val FLOOR_VERT = """
            uniform mat4 uMvp;
            attribute vec4 aPos;
            attribute vec2 aTex;
            varying vec2 vTex;
            void main() { gl_Position = uMvp * aPos; vTex = aTex; }
        """

        const val FLOOR_FRAG = """
            precision mediump float;
            varying vec2 vTex;
            uniform sampler2D sTex;
            uniform float uAlpha;
            void main() {
                vec4 c = texture2D(sTex, vTex);
                gl_FragColor = vec4(c.rgb * uAlpha, c.a * uAlpha);
            }
        """
    }
}

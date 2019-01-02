package com.pierbezuhoff.dodeca

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.graphics.withMatrix
import org.apache.commons.math3.complex.Complex
import java.io.File
import kotlin.concurrent.fixedRateTimer
import kotlin.properties.Delegates
import kotlin.reflect.KMutableProperty0
import kotlin.system.measureTimeMillis

class DodecaView(context: Context, attributeSet: AttributeSet? = null) : View(context, attributeSet) {
    // dummy default, actual from init(), because I cannot use lateinit here
    var ddu: DDU by Delegates.observable(DDU(circles = emptyList()))
        { _, _, value ->
            circles = value.circles.toMutableList()
            motion.value.reset()
            drawTrace = value.drawTrace ?: defaultDrawTrace
            redrawTraceOnce = drawTrace
            updating = defaultUpdating
            value.file?.let { file ->
                editing { putString("recent_ddu", file.name) }
            }
            clearMinorSharedPreferences()
            nUpdates = 0
            last20NUpdates = nUpdates
            lastUpdateTime = System.currentTimeMillis()
            last20UpdateTime = lastUpdateTime
            centerize(value)
            invalidate()
        }
    private lateinit var circles: MutableList<CircleFigure>
    val sharedPreferences: SharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    var nUpdatesView: TextView? = null
    var time20UpdatesView: TextView? = null
    var drawTrace: Boolean by Delegates.observable(defaultDrawTrace) { _, _, _ ->
        if (width > 0) retrace()
    }

    private var redrawTraceOnce: Boolean = defaultDrawTrace
    var updating = defaultUpdating
    private var lastUpdateTime: Long = 0L
    private var updateOnce = false
    private val paint = Paint(defaultPaint)
    private val trace: Trace = Trace(Paint(defaultPaint))
    private var nUpdates: Long = 0L

    var showStat = true // MainActivity should set up this whenever SharedPreference/show_stat changes
    private var last20NUpdates: Long = 0L
    private var last20UpdateTime: Long = 0L

    // ddu:r -> motion -> visible:r
    private val motion = object : SharedPreference<Matrix>(Matrix()) {
        override fun peek(sharedPreferences: SharedPreferences): Matrix {
            with(sharedPreferences) {
                val dx = getFloat("dx", 0f)
                val dy = getFloat("dy", 0f)
                val scale = getFloat("scale", 1f)
                return Matrix().apply { postTranslate(dx, dy); postScale(scale, scale) }
            }
        }
        override fun put(editor: SharedPreferences.Editor) {
            with(editor) {
                putFloat("dx", value.dx)
                putFloat("dy", value.dy)
                putFloat("scale", value.sx) // sx == sy
            }
        }
    }
    val dx get() = motion.value.dx
    val dy get() = motion.value.dy
    val scale get() = motion.value.sx
    var pickedColor: Int? = null

    val centerX: Float get() = x + width / 2
    val centerY: Float get() = y + height / 2

    init {
        loadSharedPreferences()
        try {
            val recentDDU by lazy { sharedPreferences.getString("recent_ddu", null) }
            val filename = if (preferRecentDDU.value && recentDDU != null) recentDDU else "290305_z1_erot2.ddu"
            val dduFile = File(File(context.filesDir, "ddu"), filename)
            this.ddu = DDU.readFile(dduFile)
        } catch (e: Exception) {
            e.printStackTrace()
            this.ddu = exampleDDU
        }
        fixedRateTimer("DodecaView-updater", initialDelay = 1L, period = dt.toLong()) {
            if (updating)
                postInvalidate()
        }
        saveMinorSharedPreferences()
    }

    fun loadSharedPreferences() {
        loadMajorSharedPreferences()
        loadMinorSharedPreferences()
    }

    fun loadMajorSharedPreferences() {
        var updateImmediately = false
        upon(::autocenterOnce) { autocenter() }
        with(sharedPreferences) {
            listOf(redrawTraceOnMove, reverseMotion).forEach { fetch(it) }
            listOf(showAllCircles, showCenters, showOutline, rotateShapes, shape).forEach {
                fetch(it) { updateImmediately = true }
            }
            // load FPS/UPS
            // NOTE: restart/rotate screen to update FPS
            fetch(autocenterAlways) { if (it && width > 0) autocenter() }
            fetch(canvasFactor) { if (width > 0) retrace() }
        }
        if (!updating && updateImmediately)
            invalidate()
    }

    private fun loadMinorSharedPreferences() {
        with (sharedPreferences) {
            fetch(motion)
            drawTrace = getBoolean("drawTrace", drawTrace)
            updating = getBoolean("updating", updating)
        }
    }

    private fun saveMinorSharedPreferences() {
        editing {
            put(motion)
            putBoolean("drawTrace", drawTrace)
            putBoolean("updating", updating)
        }
    }

    private fun clearMinorSharedPreferences() {
        editing {
            setOf("dx", "dy", "scale", "drawTrace", "updating").forEach { remove(it) }
        }
    }

    private fun centerize(ddu: DDU? = null) {
        // check rotation! or move checking to trace.translation
        val targetDDU: DDU = ddu ?: this.ddu
        if (width > 0) { // we know sizes
            if (autocenterAlways.value)
                autocenter()
            else if (targetDDU.bestCenter != null)
                centerize(targetDDU.bestCenter!!)
        }
    }

    private fun autocenter() {
        val center = ComplexFF(centerX, centerY)
        val shownCircles = circles.filter(CircleFigure::show)
        val visibleCenter = mean(shownCircles.map { visible(it.center) })
        val (dx, dy) = (center - visibleCenter).asFF()
        updateScroll(dx, dy)
    }

    private fun centerize(newCenter: Complex) {
        val visibleNewCenter = visible(newCenter)
        val center = ComplexFF(centerX, centerY)
        Log.i(TAG, "centering $visibleNewCenter -> $center")
        val (dx, dy) = (visibleNewCenter - center).asFF()
        updateScroll(dx, dy)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerize()
        if (!trace.initialized)
            retrace()
    }

    override fun onDraw(canvas: Canvas?) {
        // max performance impact
        super.onDraw(canvas)
        canvas?.let {
            when {
                updating -> updateCanvas(it)
                updateOnce -> {
                    updateCanvas(it)
                    updateOnce = false
                }
                drawTrace -> drawTraceCanvas(it)
                else -> onCanvas(it) {
                    // pause and no drawTrace
                    drawBackground(it)
                    drawCircles(it)
                }
            }
        }
    }

    private fun updateCanvas(canvas: Canvas) { // important performance impact
        val timeToUpdate by lazy { System.currentTimeMillis() - lastUpdateTime >= updateDt }
        if (updating && timeToUpdate) {
            updateCircles()
            lastUpdateTime = System.currentTimeMillis()
        }
        if (drawTrace) {
            onTraceCanvas {
                upon(::redrawTraceOnce) { drawBackground(it) }
                drawCircles(it)
            }
            drawTraceCanvas(canvas)
        } else {
            onCanvas(canvas) {
                drawBackground(it)
                drawCircles(it)
            }
        }
    }

    /* scroll/translate screen and drawTrace */
    fun updateScroll(ddx: Float, ddy: Float) {
        motion.value.postTranslate(ddx, ddy)
        updatingTrace { trace.translate(ddx, ddy) }
    }

    /* scale screen and drawTrace */
    fun updateScale(dscale: Float, focusX: Float? = null, focusY: Float? = null) {
        motion.value.postScale(dscale, dscale, focusX ?: centerX, focusY ?: centerY)
        updatingTrace { trace.scale(dscale, dscale, focusX ?: centerX, focusY ?: centerY) }
    }

    /* when drawTrace: retrace if should do it on move, else do [action] */
    private fun updatingTrace(action: () -> Unit) {
        if (drawTrace) {
            if (updating && redrawTraceOnMove.value || !updating && shouldRedrawTraceOnMoveWhenPaused)
                retrace()
            else {
                action()
                invalidate()
            }
        } else if (!updating)
            invalidate()
    }

    /* when drawTrace turns on or sizes change */
    fun retrace() {
        trace.retrace(width, height)
        trace.canvas.concat(motion.value)
        redrawTraceOnce = drawTrace
        if (!updating) {
            onTraceCanvas {
                drawBackground(it)
                drawCircles(it)
            }
        }
        invalidate()
    }

    private fun onCanvas(canvas: Canvas, draw: (Canvas) -> Unit) =
        canvas.withMatrix(motion.value) { draw(this) }

    private fun onTraceCanvas(draw: (Canvas) -> Unit) =
        draw(trace.canvas)

    /* draw drawTrace canvas on DodecaView canvas */
    private fun drawTraceCanvas(canvas: Canvas) =
        canvas.drawBitmap(trace.bitmap, trace.blitMatrix, trace.paint)

    /* draw background color on [canvas] */
    private fun drawBackground(canvas: Canvas) =
        canvas.drawColor(ddu.backgroundColor)

    private fun drawCircles(canvas: Canvas) = logMeasureTimeMilis("drawCircles") { _drawCircles(canvas) }
    /* draw `circles` on [canvas] */
    private fun _drawCircleShapes(canvas: Canvas) { // maybe does performance impact
        circles.filter { it.show || showAllCircles.value }.forEach { drawCircle(canvas, it) }
    }
    private fun _drawCircles(canvas: Canvas) {
        if (showAllCircles.value)
            circles.forEach { drawCircle(canvas, it) }
        else
            circles.filter { it.show }.forEach { drawCircle(canvas, it) }
    }

    // NOTE: draw ONLY circle
    /* draw shape from [circle] on [canvas] */
    private fun drawCircle(canvas: Canvas, circle: CircleFigure) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        val (x, y) = c.asFF()
        canvas.drawCircle(x, y, r.toFloat(), paint)
    }

    /* draw shape from [circle] on [canvas] */
    private fun drawCircleShape(canvas: Canvas, circle: CircleFigure) {
        val (c, r) = circle
        paint.color = circle.borderColor
        paint.style = if (circle.fill) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
        val (x, y) = c.asFF()
        val (pX, pY) = (c + r * circle.point).asFF()
        shape.value.draw(canvas, x, y, r.toFloat(), pX, pY, circle.point, showOutline.value, paint)
        canvas.drawCircle(x, y, r.toFloat(), paint)
    }

    fun pickColor(x: Float, y: Float) {
        // TODO: color picker/changer or discard
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // layout(left, top, right, bottom)
        draw(canvas)
        // NOTE: sometimes traceBitmap can be used
        pickedColor = bitmap.getPixel(x.toInt(), y.toInt())
        Log.i(TAG, pickedColor.toString())
    }

    /* change all circles with `pickedColor` -> `newColor` */
    fun changeColor(newColor: Int) {
        // maybe: also change DDU?
        pickedColor?.let {
            circles.filter { it.borderColor == pickedColor }.forEach { it.borderColor = newColor }
        }
        pickedColor = newColor
        postInvalidate()
    }

    fun oneStep() {
        updateCircles()
        lastUpdateTime = System.currentTimeMillis()
        updateOnce = true
        updating = false
        postInvalidate()
    }

    private fun updateCircles() = logMeasureTimeMilis("updateCircles") { _updateCircles() }
    private fun _updateCircles() {
        nUpdates += if (reverseMotion.value) -1 else 1
        if (showStat) {
            nUpdatesView?.text = context.getString(R.string.stat_n_updates_text).format(nUpdates)
            if (nUpdates - last20NUpdates >= 20) {
                time20UpdatesView?.text = context.getString(R.string.stat_20_updates_per_text)
                    .format((lastUpdateTime - last20UpdateTime) / 1000f)
                last20NUpdates = nUpdates
                last20UpdateTime = lastUpdateTime
            }
        }
        val oldCircles = circles.map { it.copy(newRule = null) }
        for (i in circles.indices) {
            val circle = circles[i]
            circle.rule?.let { rule ->
                val rule = if (rule.startsWith("n")) rule.drop(1) else rule
                val sequence = rule.map(Character::getNumericValue)
                for (j in sequence) {
                    circle.invert(oldCircles[j])
                }
            }
        }
//        val n = circles.size
//        oldCircles.forEachIndexed { i, circle ->
//            circle.rule?.let { rule ->
//                val theRule = if (rule.startsWith("n")) rule.drop(1) else rule
//                val chars = if (reverseMotion.value) theRule.reversed() else theRule
//                chars.forEach { ch ->
//                    val j = Integer.parseInt(ch.toString())
//                    if (j >= n) {
//                        Log.e(TAG, "updateCircles: index $j >= $n out of `circles` bounds (from rule $rule for $circle)")
//                    }
//                    else {
//                        circles[i].invert(oldCircles[j])
//                    }
//                }
//            }
//        }
    }

    /* scale and translate all circles in ddu according to current view */
    fun prepareDDUToSave(): DDU {
        return ddu.copy().apply {
            circles.forEach {
                it.center = visible(it.center)
                it.radius *= scale // = visibleRadius(it.radius)
            }
            drawTrace = this@DodecaView.drawTrace
            bestCenter = ComplexFF(centerX, centerY)
        }
    }

    private fun visible(z: Complex): Complex = motion.value.move(z)
    private fun visibleRadius(r: Float): Float = motion.value.mapRadius(r)

    private fun editing(block: SharedPreferences.Editor.() -> Unit) {
        with (sharedPreferences.edit()) {
            this.block()
            apply()
        }
    }

    companion object {
        val defaultPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
        }
        // FIX: changing FPS and UPS does not work properly, not in preferences now
        private var FPS = Option("fps", 100)
        private var UPS = Option("ups", 100) // updates per second
        val dt get() = 1000f / FPS.value
        val updateDt get() = 1000f / UPS.value
        private var redrawTraceOnMove = Option("redraw_trace", false)
        enum class RedrawOnMoveWhenPaused { ALWAYS, NEVER, RESPECT_REDRAW_TRACE_ON_MOVE }
        private var redrawTraceOnMoveWhenPaused =
            Option("redraw_trace_when_paused", RedrawOnMoveWhenPaused.RESPECT_REDRAW_TRACE_ON_MOVE)
        private val shouldRedrawTraceOnMoveWhenPaused get() = when (redrawTraceOnMoveWhenPaused.value) {
            RedrawOnMoveWhenPaused.ALWAYS -> true
            RedrawOnMoveWhenPaused.NEVER -> false
            RedrawOnMoveWhenPaused.RESPECT_REDRAW_TRACE_ON_MOVE -> redrawTraceOnMove.value
        }
        private var showAllCircles = Option("show_all_circles", false)
        var showCenters = Option("show_centers", false)
        private var showOutline = Option("show_outline", false)
        private var reverseMotion = Option("reverse_motion", false)
        private var shape = object : Option<Shapes>("shape", Shapes.CIRCLE) {
            override fun peek(sharedPreferences: SharedPreferences): Shapes =
                sharedPreferences.getString(key, default.toString())?.toUpperCase()?.let { Shapes.valueOf(it) } ?: default
            override fun put(editor: SharedPreferences.Editor) {
                editor.putString(key, toString().capitalize())
            }
        }
        var rotateShapes = Option("rotate_shapes", false)
        var autocenterAlways = Option("autocenter_always", false)
        var autocenterOnce = false
        // maybe: add load random
        var canvasFactor = object : Option<Int>("canvas_factor", 2) {
            override fun peek(sharedPreferences: SharedPreferences): Int =
                sharedPreferences.getString(key, default.toString())?.let { Integer.parseInt(it) } ?: default
            override fun put(editor: SharedPreferences.Editor) {
                editor.putString(key, toString())
            }
        }
        var preferRecentDDU = Option("prefer_recent_ddu", true) // TODO: add to preferences
        const val defaultDrawTrace = true
        const val defaultUpdating = true
        private const val TAG = "DodecaView"
    }
}


enum class Shapes {
    CIRCLE, POINTED_CIRCLE, SQUARE, CROSS, VERTICAL_BAR, HORIZONTAL_BAR;

    fun draw(canvas: Canvas, x: Float, y: Float, halfWidth: Float, pointX: Float, pointY: Float, point: Complex, drawOutline: Boolean, paint: Paint) {
        val center by lazy { ComplexFF(x, y) }
        fun trueRotated(x: Float, y: Float): PointF =
            (center + (ComplexFF(x, y) - center) * point).run { PointF(real.toFloat(), imaginary.toFloat()) }
        val rotated: (Float, Float) -> PointF =
            if (DodecaView.rotateShapes.value) { x, y -> trueRotated(x, y) } else { x, y -> PointF(x, y) }
        val top by lazy { rotated(x, y - halfWidth) }
        val bottom by lazy { rotated(x, y + halfWidth) }
        val left by lazy { rotated(x - halfWidth, y) }
        val right by lazy { rotated(x + halfWidth, y) }
        // TODO: compare performance when canvas.withRotation vs rotated
        when(this) {
            CIRCLE -> canvas.drawCircle(x, y, halfWidth, paint)
            POINTED_CIRCLE -> {
                canvas.drawCircle(x, y, halfWidth, paint)
                canvas.drawPoint(pointX, pointY, pointPaint)
            }
            SQUARE -> canvas.drawRect( // how to rotate rect? try canvas.withRotation(<degrees>) { <draw> }
                x - halfWidth, y - halfWidth,
                x + halfWidth, y + halfWidth,
                paint
            )
            CROSS -> canvas.drawLines(
                floatArrayOf(
                    top.x, top.y, bottom.x, bottom.y,
                    left.x, left.y, right.x, right.y
                ), paint
            )
            VERTICAL_BAR -> canvas.drawLine(top.x, top.y, bottom.x, bottom.y, paint)
            HORIZONTAL_BAR -> canvas.drawLine(left.x, left.y, right.x, right.y, paint)
        }
        if (drawOutline)
            when(this) {
                CIRCLE, POINTED_CIRCLE -> canvas.drawCircle(x, y, halfWidth, outlinePaint)
                SQUARE -> canvas.drawRect( // how to rotate rect?
                    x - halfWidth, y - halfWidth,
                    x + halfWidth, y + halfWidth,
                    outlinePaint
                )
            }
        if (DodecaView.showCenters.value)
            canvas.drawPoint(x, y, pointPaint)
    }

    companion object {
        private val pointPaint = Paint(DodecaView.defaultPaint).apply {
            color = Color.MAGENTA
            strokeWidth = 3.0f
        }
        val outlinePaint = Paint(DodecaView.defaultPaint).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
        }
    }
}

internal inline fun upon(prop: KMutableProperty0<Boolean>, action: () -> Unit) {
    if (prop.get()) {
        prop.set(false)
        action()
    }
}

val times: MutableMap<String, Long> = mutableMapOf()
val ns: MutableMap<String, Int> = mutableMapOf()
internal fun logMeasureTimeMilis(name: String = "", block: () -> Unit) {
    val time = measureTimeMillis(block)
    if (ns.contains(name)) {
        ns[name] = ns[name]!! + 1
        times[name] = times[name]!! + time
    } else {
        ns[name] = 1
        times[name] = time
    }
    val overall = "%.2f".format(times[name]!!.toFloat()/ns[name]!!)
    Log.i("logMeasureTimeMilis/$name", "overall: ${overall}ms, current: ${time}ms")
}
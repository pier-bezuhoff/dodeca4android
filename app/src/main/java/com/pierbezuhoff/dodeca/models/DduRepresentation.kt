package com.pierbezuhoff.dodeca.models

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.graphics.withMatrix
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.CoordinateSystem3D
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.DduAttributesHolder
import com.pierbezuhoff.dodeca.data.DduOptionsChangeListener
import com.pierbezuhoff.dodeca.data.Shape
import com.pierbezuhoff.dodeca.data.Trace
import com.pierbezuhoff.dodeca.data.circlegroup.ProjectiveCircles3D
import com.pierbezuhoff.dodeca.data.circlegroup.SuspendableCircleGroup
import com.pierbezuhoff.dodeca.data.circlegroup.mkCircleGroup
import com.pierbezuhoff.dodeca.ui.dodecaview.DodecaGestureDetector
import com.pierbezuhoff.dodeca.utils.Connection
import com.pierbezuhoff.dodeca.utils.Just
import com.pierbezuhoff.dodeca.utils.Once
import com.pierbezuhoff.dodeca.utils.Sleeping
import com.pierbezuhoff.dodeca.utils.asFF
import com.pierbezuhoff.dodeca.utils.div
import com.pierbezuhoff.dodeca.utils.dx
import com.pierbezuhoff.dodeca.utils.dy
import com.pierbezuhoff.dodeca.utils.mean
import com.pierbezuhoff.dodeca.utils.minus
import com.pierbezuhoff.dodeca.utils.move
import com.pierbezuhoff.dodeca.utils.plus
import com.pierbezuhoff.dodeca.utils.sx
import com.pierbezuhoff.dodeca.utils.times
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.math3.complex.Complex
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

class DduRepresentation(
    override val ddu: Ddu,
    private val optionsManager: OptionsManager
) : Any()
    , DduAttributesHolder
    , DduOptionsChangeListener
    , DodecaGestureDetector.ScrollListener
    , DodecaGestureDetector.ScaleListener
{
    interface Presenter : LifecycleOwner {
        fun getCenter(): Complex?
        /** Return (width, height) or null */
        fun getSize(): Pair<Int, Int>?
        fun redraw()
    }
    interface StatHolder { fun updateStat(delta: Int = 1) }
    interface ToastEmitter {
        fun toast(message: CharSequence)
        fun formatToast(@StringRes id: Int, vararg args: Any)
    }
    private var _presenter: WeakReference<Presenter>? = null
    private var presenter: Presenter?
        get() = _presenter?.get()
        set(value) { value?.let { _presenter = WeakReference(it) } }
    private val statHolderConnection = Connection<StatHolder>()
    private val toastEmitterConnection = Connection<ToastEmitter>()
    val statHolderSubscription = statHolderConnection.subscription
    val toastEmitterSubscription = toastEmitterConnection.subscription

    private val optionValues = optionsManager.values

    private val updateScheduler: UpdateScheduler = UpdateScheduler()

    private val paint: Paint = Paint(DEFAULT_PAINT)

    var circleGroup: SuspendableCircleGroup =
        mkCircleGroup(optionValues.circleGroupImplementation, optionValues.projR, ddu.circles, paint)
    override var updating: Boolean = DEFAULT_UPDATING
        set(value) { field = value; changeUpdating(value) }
    override var drawTrace: Boolean = ddu.drawTrace ?: DEFAULT_DRAW_TRACE
        set(value) { field = value; changeDrawTrace(value) }
    override var shape: Shape = ddu.shape
        set(value) { field = value; changeShape(value) }
    var showEverything: Boolean = false
        set(value) { changeShowEverything(value); field = value }
    var selectedCircles: IntArray = intArrayOf()
        set(value) { changeSelectedCircle(value); field = value }

    private val motion: Matrix = Matrix() // visible(z) = motion.move(z)
    private var trace: Trace? = null
    val traceBitmap: Bitmap?
        get() = trace?.bitmap
    private var currentCenter: Complex? = null // for screen rotations, visible current center (almost always == presenter?.getCenter())

    private var mode: Mode = Mode.MODE_2D // whether to use ProjectiveCircles3D as a cg

    private var redrawTraceOnce: Boolean by Once()
    private var updateOnce: Boolean by Once()

    private val presenterDisconnector = PresenterDisconnector(this)

    fun connectPresenter(presenter: Presenter) {
        if (this.presenter != null) { // should not happen
            Log.w(TAG, "Connecting new presenter while previous one is non-null!")
            this.presenter?.lifecycle?.removeObserver(presenterDisconnector)
        }
        this.presenter = presenter
        presenter.lifecycle.addObserver(presenterDisconnector)
        setBestCenter()
        centerizeToBestCenter()
        if (trace == null) // do not clearTrace on screen rotation and other config. changes
            clearTrace()
        presenter.mainLoop()
    }

    private fun setBestCenter() {
        if (ddu.bestCenter == null)
            ddu.bestCenter =
                if (optionValues.autocenterAlways) ddu.autoCenter
                else presenter?.getCenter()
    }

    private fun centerizeToBestCenter() {
        if (currentCenter == null && ddu.bestCenter != null)
            currentCenter = visible(ddu.bestCenter!!)
        currentCenter?.let { centerizeToVisible(it) }
    }

    private fun centerizeToVisible(newVisibleCenter: Complex) {
        presenter?.getCenter()?.let { center ->
            val (dx, dy) = (center - newVisibleCenter).asFF()
            scroll(dx, dy)
        }
    }

    private fun Presenter.mainLoop() {
        val dduRepresentation: WeakReference<DduRepresentation> = WeakReference(this@DduRepresentation)
        with(this) {
            lifecycleScope.launch {
                delay(INITIAL_DELAY_IN_MILLISECONDS)
                while (isActive && dduRepresentation.get() != null) {
                    if (dduRepresentation.get()?.updating == true)
                        redraw()
                    delay(DT_IN_MILLISECONDS)
                }
            }
        }
    }

    fun autocenterize() {
        // MAYBE: when canvasFactor * scale ~ 1 try to fit screen
        val scale: Float = motion.sx
        if (drawTrace && trace != null &&
            optionValues.canvasFactor == 1 &&
            1 - 1e-4 < scale && scale < 1 + 1e-1
        ) {
            trace?.motion?.run {
                scroll(-dx, -dy)
            }
        } else { // BUG: ?sometimes it skips to else
            presenter?.getCenter()?.let { center ->
                val visibleCircles: List<CircleFigure> = circleGroup.figures.filter(CircleFigure::visible)
                val visibleCenter = visibleCircles.map { visible(it.center) }.mean()
                val (dx, dy) = (center - visibleCenter).asFF()
                scroll(dx, dy)
            }
        }
    }

    fun updateCircleGroup() {
        val figures = circleGroup.figures
        circleGroup = mkCircleGroup(
            optionValues.circleGroupImplementation,
            optionValues.projR,
            figures, paint
        )
    }

    fun oneStep() {
        val batch: Int = optionValues.speed.roundToInt()
        drawTimes(batch)
        updateOnce = true
        presenter?.redraw()
        statHolderConnection.send { updateStat(batch) }
    }

    fun requestUpdateOnce() {
        updateOnce = true
    }

    override fun onScroll(fromX: Float, fromY: Float, dx: Float, dy: Float) {
        when (mode) {
            Mode.MODE_2D -> scroll(-dx, -dy)
            Mode.MODE_3D_NAVIGATE -> (circleGroup as ProjectiveCircles3D).run {
                doTransformation(CoordinateSystem3D.Transformation.Right(dx.toDouble()))
                doTransformation(CoordinateSystem3D.Transformation.Up(dy.toDouble()))
            }
            Mode.MODE_3D_ROTATE -> (circleGroup as ProjectiveCircles3D).run {
                doTransformation(CoordinateSystem3D.Transformation.Yaw(dx.toDouble() / DISTANCE_TO_ANGLE_DESCALE))
                doTransformation(CoordinateSystem3D.Transformation.Pitch(dy.toDouble() / DISTANCE_TO_ANGLE_DESCALE))
            }
        }
    }

    override fun onScale(scale: Float, focusX: Float, focusY: Float) {
        when (mode) {
            Mode.MODE_2D -> scale(scale, focusX, focusY)
            else -> (circleGroup as ProjectiveCircles3D)
                .doTransformation(CoordinateSystem3D.Transformation.Zoom(scale.toDouble()))
        }
    }

    private fun scroll(dx: Float, dy: Float) =
        transform {
            postTranslate(dx, dy)
        }

    private fun scale(dscale: Float, focusX: Float, focusY: Float) =
        transform {
            postScale(dscale, dscale, focusX, focusY)
        }

    private inline fun transform(crossinline transformation: Matrix.() -> Unit) {
        currentCenter = presenter?.getCenter()
        motion.transformation()
        if (drawTrace) {
            if (optionValues.redrawTraceOnMove)
                clearTrace()
            else {
                trace?.motion?.transformation()
                presenter?.redraw()
            }
        } else if (!updating)
            presenter?.redraw()
    }

    /** Scale and translate all figures in ddu according to the current view creating a new ddu */
    suspend fun buildCurrentDdu(): Ddu =
        withContext(Dispatchers.Default) {
            val currentDrawTrace = drawTrace
            val currentShape = shape
            ddu.copy().apply {
                circles = circles.zip(circleGroup.figures) { figure, newFigure ->
                    figure.center = visible(figure.center)
                    figure.radius *= motion.sx
                    return@zip figure.copy(
                        newColor = newFigure.color, newFill = newFigure.fill,
                        newRule = newFigure.rule, newBorderColor = Just(newFigure.borderColor)
                    )
                }
                drawTrace = currentDrawTrace
                bestCenter = presenter?.getCenter()
                shape = currentShape
            }
        }

    // capture current circle's positions
    suspend fun buildCurrentDduState(): Ddu =
        withContext(Dispatchers.Default) {
            val currentDrawTrace = drawTrace
            val currentShape = shape
            ddu.copy().apply {
                circles = circleGroup.figures
                drawTrace = currentDrawTrace
                bestCenter = presenter?.getCenter()
                shape = currentShape
            }
        }

    suspend fun updateTimes(times: Int) =
        circleGroup.suspendableUpdateTimes(times, reverse = optionValues.reverseMotion)

    private fun visible(z: Complex): Complex =
        motion.move(z)

    override fun onAutocenterAlways(autocenterAlways: Boolean) {
        if (autocenterAlways)
            autocenterize()
    }

    override fun onCanvasFactor(canvasFactor: Int) {
        if (canvasFactor != trace?.currentCanvasFactor)
            clearTrace()
    }

    override fun onSpeed(speed: Float) {
        updateScheduler.setSpeed(speed)
    }

    private fun changeUpdating(newUpdating: Boolean) {
        if (newUpdating)
            presenter?.redraw()
    }

    private fun changeDrawTrace(newDrawTrace: Boolean) {
        if (newDrawTrace)
            clearTrace()
        else
            presenter?.redraw()
    }

    private fun changeShape(@Suppress("UNUSED_PARAMETER") newShape: Shape) {
        presenter?.redraw()
    }

    private fun changeShowEverything(newShowEverything: Boolean) {
        if (newShowEverything != showEverything) {
            presenter?.redraw()
        }
    }

    private fun changeSelectedCircle(newSelectedCircles: IntArray) {
        if (!newSelectedCircles.contentEquals(selectedCircles))
            presenter?.redraw()
    }

    fun draw(canvas: Canvas) {
        when {
            updating || updateOnce -> updateCanvas(canvas)
            drawTrace -> canvas.drawTraceCanvas()
            else -> onCanvas(canvas) { drawVisible() }
        }
        if (showEverything && mode == Mode.MODE_2D) {
//            _drawOverlay(canvas)
            onCanvas(canvas) { drawOverlay() }
        }
//        Log.i(TAG, "draw") // TMP
    }

    /** Reset trace and invalidate */
    fun clearTrace() {
        tryClearTrace()
        trace?.canvas?.concat(motion)
        redrawTraceOnce = drawTrace
        if (!updating) {
            onTraceCanvas {
                drawBackground()
//                drawVisible()
            }
        }
        presenter?.redraw()
    }

    private fun tryClearTrace() {
        var done = false
        var canvasFactor = optionValues.canvasFactor
        presenter?.getSize()?.let { (width: Int, height: Int) ->
            while (!done) {
                try {
                    trace = Trace(width, height, optionsManager)
                    done = true
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    if (canvasFactor > 1) {
                        val nextFactor = canvasFactor - 1
                        Log.w(TAG, "too large canvasFactor: $canvasFactor -> $nextFactor")
                        toastEmitterConnection.send {
                            formatToast(R.string.canvas_factor_oom_toast, canvasFactor, nextFactor)
                        }
                        canvasFactor = nextFactor
                    } else {
                        // TODO: memory profiling
                        // ISSUE: after many ddu loads RAM seems to be only increasing which eventually leads to OOM
                        Log.e(TAG, "min canvasFactor  $canvasFactor is too large!")
                        toastEmitterConnection.send {
                            formatToast(R.string.minimal_canvas_factor_oom_toast, canvasFactor)
                        }
                        done = true // very bad
                    }
                }
            }
            if (canvasFactor != optionValues.canvasFactor) {
                with(optionsManager) { set(options.canvasFactor, canvasFactor) }
            }
        }
    }

    private fun updateCanvas(canvas: Canvas) {
        if (updating && updateScheduler.timeToUpdate()) {
            val times = optionValues.speed.roundToInt()
            drawTimes(times)
            statHolderConnection.send { updateStat(times) }
            updateScheduler.doUpdate()
//            Log.i(TAG, "update") // TMP
        }
        drawUpdatedCanvas(canvas)
    }

    private fun drawTimes(times: Int = 1) {
        if (times <= 1) {
            circleGroup.update(reverse = optionValues.reverseMotion)
        } else {
            onTraceCanvas {
                drawCirclesTimes(times)
            }
        }
    }

    private fun drawUpdatedCanvas(canvas: Canvas) {
        if (drawTrace) {
            onTraceCanvas {
                if (redrawTraceOnce)
                    drawBackground()
                drawCircles()
            }
            canvas.drawTraceCanvas()
        } else {
            onCanvas(canvas) {
                drawVisible()
            }
        }
    }

    private inline fun onCanvas(canvas: Canvas, crossinline draw: Canvas.() -> Unit) =
        canvas.withMatrix(motion) { draw() }

    private inline fun onTraceCanvas(crossinline draw: Canvas.() -> Unit) =
        trace?.canvas?.draw()

    private inline fun Canvas.drawTraceCanvas() =
        trace?.drawOn(this, paint)

    /** Draw background and circles */
    private inline fun Canvas.drawVisible() {
        drawBackground()
        drawCircles()
    }

    private inline fun Canvas.drawBackground() =
        drawColor(ddu.backgroundColor)

    private inline fun Canvas.drawCircles() {
        circleGroup.draw(
            canvas = this,
            shape = shape
        )
    }

    private inline fun Canvas.drawCirclesTimes(times: Int) {
        circleGroup.drawTimes(
            times = times, canvas = this,
            shape = shape, reverse = optionValues.reverseMotion
        )
    }

    private inline fun Canvas.drawOverlay() {
        circleGroup.drawOverlay(this, selectedCircles)
        presenter?.redraw()
    }

    private fun _drawOverlay(canvas: Canvas) {
        1
        presenter?.redraw()
    }

    fun changeAngularSpeed(factor: Float) {
        circleGroup.changeAngularSpeed(factor)
        // TODO: also upd ddu
    }

    fun allCirclesAreFilled(): Boolean =
        circleGroup.figures.filter { it.visible }.all { it.fill }

    fun fillCircles() {
        val fs = circleGroup.figures
        for (i in fs.indices) {
            val f = fs[i]
            if (f.visible)
                circleGroup[i] = f.copy(newFill = true)
        }
    }

    fun unfillCircles() {
        val fs = circleGroup.figures
        for (i in fs.indices) {
            val f = fs[i]
            if (f.visible)
                circleGroup[i] = f.copy(newFill = false)
        }
    }

    fun moveCircle(i: Int, v: Complex) {
        val dc = v/motion.sx.toDouble()
        val f = circleGroup[i]
        f.center += dc
        circleGroup[i] = f
        presenter?.redraw()
    }

    fun changeCircleRadius(i: Int, scale: Double = 1.0, dr: Double = 0.0) {
        val f = circleGroup[i]
        val newRadius = abs(scale * f.radius + dr/motion.sx)
        f.radius = newRadius
        circleGroup[i] = f
        presenter?.redraw()
    }

    fun selectCircles(
        p: Complex,
        threshold: Double = 10.0, amongHidden: Boolean = true
    ): List<Int> {
        val indexedCircles = circleGroup.figures.withIndex().reversed()
        val targets = if (amongHidden) indexedCircles else indexedCircles.filter { (_, c) -> c.visible }
        val result = targets.filter { (_, c) ->
            val d = (p - visible(c.center)).abs()
//            useCenters && d <= threshold ||
                abs(d - c.radius * motion.sx) <= threshold
        }.map { (i, _) -> i }
        return result.ifEmpty {
            targets.filter { (_, c) ->
                val d = (p - visible(c.center)).abs()
                val visibleRadius = c.radius * motion.sx
                c.fill && d < visibleRadius + threshold
            }.map { (i, _) -> i }
        }
    }

    fun inlineDdu(ddu: Ddu, targetCircles: List<Int>) {
        presenter?.getSize()?.let { (w, h) ->
            val minSize = min(w, h)
            val currentCircles = circleGroup.figures
            val newCircles = mutableListOf<CircleFigure>()
            require(targetCircles.all { it in currentCircles.indices })
            var n = currentCircles.size
            val k = ddu.circles.size
            val sourceCenter = ddu.bestCenter ?: Complex(w/2.0, h/2.0)
            targetCircles.forEach { i ->
                val target = currentCircles[i]
                val scale = target.radius/minSize
                newCircles += ddu.circles
                    .map { c ->
                        val newCenter = scale * (c.center - sourceCenter) + target.center
                        val newR = scale * c.radius
                        val newRule = c.rule.map { it + n } + target.rule
                        return@map c.copy(newCenter = newCenter, newRadius = newR, newRule = newRule)
                    }
                n += k
            }
            val result = currentCircles.mapIndexed { i, c ->
                if (i in targetCircles)
                    c.copy(newFill = false) // MAYBE: hide instead
                else c
            } + newCircles
            circleGroup = mkCircleGroup(
                optionValues.circleGroupImplementation,
                optionValues.projR,
                result, paint
            )
        }
    }

    fun switchMode(newMode: Mode) {
        when (newMode) {
            Mode.MODE_2D -> if (mode !== Mode.MODE_2D) {
                clearTrace()
                updateCircleGroup()
            }
            else -> if (mode !in listOf(Mode.MODE_3D_NAVIGATE, Mode.MODE_3D_ROTATE)) {
                clearTrace()
                circleGroup = ProjectiveCircles3D(circleGroup.figures, paint, optionValues.projR.toDouble())
            }
        }
        mode = newMode
    }

    private class UpdateScheduler {
        private var lastUpdateTime: Long = 0
        private var ups: Int = DEFAULT_UPS // updates per second
            set(value) { field = value; sleepingUpdateDt.awake() }
        private val sleepingUpdateDt = Sleeping { 1000f / ups }
        private val updateDt: Float by sleepingUpdateDt

        fun timeToUpdate(): Boolean =
            System.currentTimeMillis() - lastUpdateTime >= updateDt

        fun doUpdate() {
            lastUpdateTime = System.currentTimeMillis()
        }

        fun setSpeed(speed: Float) {
            ups =
                if (speed < 1)
                    (speed * DEFAULT_UPS).roundToInt()
                else DEFAULT_UPS
        }

        companion object {
            private const val DEFAULT_UPS = 60 // empirical
        }
    }

    class PresenterDisconnector(dduRepresentation: DduRepresentation) : DefaultLifecycleObserver {
        private val dduRepresentation: WeakReference<DduRepresentation> =
            WeakReference(dduRepresentation)

        override fun onDestroy(owner: LifecycleOwner) {
            super.onDestroy(owner)
            disconnectPresenter()
        }
        private fun disconnectPresenter() { dduRepresentation.get()?.presenter = null }
    }

    enum class Mode {
        MODE_2D, MODE_3D_NAVIGATE, MODE_3D_ROTATE
    }

    companion object {
        private const val TAG: String = "DduRepresentation"
        private val DEFAULT_PAINT =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
            }
        private const val DEFAULT_DRAW_TRACE = true
        private const val DEFAULT_UPDATING = true
        private const val FPS = 60
        /** Interval between presenter.redraw() calls */
        private const val DT_IN_MILLISECONDS: Long = (1000f / FPS).toLong()
        private const val INITIAL_DELAY_IN_MILLISECONDS: Long = 1

        private const val DISTANCE_TO_ANGLE_DESCALE: Double = 100.0
    }
}


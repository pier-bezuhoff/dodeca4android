package com.pierbezuhoff.dodeca.models

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.graphics.withMatrix
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.pierbezuhoff.dodeca.R
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.CircleGroupImpl
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.Trace
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.ui.DodecaGestureDetector
import com.pierbezuhoff.dodeca.utils.Just
import com.pierbezuhoff.dodeca.utils.asFF
import com.pierbezuhoff.dodeca.utils.dx
import com.pierbezuhoff.dodeca.utils.dy
import com.pierbezuhoff.dodeca.utils.mean
import com.pierbezuhoff.dodeca.utils.minus
import com.pierbezuhoff.dodeca.utils.move
import com.pierbezuhoff.dodeca.utils.sx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.math3.complex.Complex
import kotlin.math.roundToInt

class DduRepresentation(val ddu: Ddu) :
    DodecaGestureDetector.ScrollListener,
    DodecaGestureDetector.ScaleListener
{
    interface Presenter : LifecycleOwner {
        fun getCenter(): Complex?
        /** return (width, height) or null */
        fun getSize(): Pair<Int, Int>?
        fun redraw()
        fun requestRedrawTraceOnce()
    }
    interface StatHolder { fun updateStat(delta: Int = 1) }
    interface ToastEmitter {
        fun toast(message: CharSequence)
        fun formatToast(@StringRes id: Int, vararg args: Any)
    }

    internal lateinit var sharedPreferencesModel: SharedPreferencesModel // inject
    private var presenter: Presenter? = null
    private var statHolder: StatHolder? = null
    private var toastEmitter: ToastEmitter? = null

    private val paint: Paint = Paint(DEFAULT_PAINT)

    private val circleGroup: CircleGroup = CircleGroupImpl(ddu.circles, paint)
    private var updating: Boolean = DEFAULT_UPDATING

    private var drawTrace: Boolean = ddu.drawTrace ?: DEFAULT_DRAW_TRACE
    private var shape: Shapes = ddu.shape

    private val motion: Matrix = Matrix() // visible(x) = motion.move(x)
    private var trace: Trace? = null

    private val presenterDisconnector: LifecycleObserver = object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun disconnectPresenter() {
            this@DduRepresentation.presenter = null
        }
    }

    fun connectPresenter(presenter: Presenter) {
        if (this.presenter != null) { // should not happen
            Log.w(TAG, "Connecting new presenter while previous one is non-null!")
            this.presenter?.lifecycle?.removeObserver(presenterDisconnector)
        }
        this.presenter = presenter
        presenter.lifecycle.addObserver(presenterDisconnector)
        setBestCenter()
        ddu.bestCenter?.let {
            centerizeTo(it)
        }
        clearTrace()
        presenter.redraw()
    }

    fun connectStatHolder(statHolder: StatHolder) {
        this.statHolder = statHolder
    }

    fun connectToastEmitter(toastEmitter: ToastEmitter) {
        this.toastEmitter = toastEmitter
    }

    private fun setBestCenter() {
        if (ddu.bestCenter == null)
            ddu.bestCenter =
                if (values.autocenterAlways) ddu.autoCenter
                else presenter?.getCenter()
    }

    private fun centerizeTo(newCenter: Complex) {
        presenter?.getCenter()?.let { center ->
            val newVisibleCenter = visible(newCenter)
            val (dx, dy) = (center - newVisibleCenter).asFF()
            scroll(dx, dy)
        }
    }

    fun autocenterize() {
        // maybe: when canvasFactor * scale ~ 1 try to fit screen
        val scale: Float = motion.sx
        if (drawTrace && trace != null &&
            values.canvasFactor == 1 &&
            1 - 1e-4 < scale && scale < 1 + 1e-1
        ) {
            trace?.let {
                scroll(-it.motion.dx, -it.motion.dy)
            }
        } else { // BUG: sometimes skip to else
            presenter?.getCenter()?.let { center ->
                val shownCircles: List<CircleFigure> = circleGroup.figures.filter(CircleFigure::show)
                val visibleCenter = shownCircles.map { visible(it.center) }.mean()
                val (dx, dy) = (center - visibleCenter).asFF()
                scroll(dx, dy)
            }
        }
    }

    fun clearTrace() {
        presenter?.getSize()?.let { (width: Int, height: Int) ->
            trace = Trace(width, height)
        }
    }

    override fun onScale(scale: Float, focusX: Float, focusY: Float) =
        scale(scale, focusX, focusY)

    override fun onScroll(dx: Float, dy: Float) =
        scroll(-dx, -dy)

    private fun scroll(ddx: Float, ddy: Float) =
        transform {
            postTranslate(ddx, ddy)
        }

    private fun scale(dscale: Float, focusX: Float, focusY: Float) =
        transform {
            postScale(dscale, dscale, focusX, focusY)
        }

    private inline fun transform(crossinline transformation: Matrix.() -> Unit) {
        motion.transformation()
        if (drawTrace) {
            if (values.redrawTraceOnMove)
                clearTrace()
            else {
                trace?.motion?.transformation()
                presenter?.redraw()
            }
        } else if (!updating)
            presenter?.redraw()
    }

    /** Scale and translate all figures in ddu according to current view creating new ddu */
    suspend fun buildCurrentDdu(): Ddu? =
        withContext(Dispatchers.Default) {
            presenter?.getCenter()?.let { center ->
                val currentDrawTrace = drawTrace
                val currentShape = shape
                ddu.copy().apply {
                    circles.zip(circleGroup.figures) { figure, newFigure ->
                        figure.center = visible(figure.center)
                        figure.radius *= motion.sx
                        return@zip figure.copy(
                            newColor = newFigure.color, newFill = newFigure.fill,
                            newRule = newFigure.rule, newBorderColor = Just(newFigure.borderColor)
                        )
                    }
                    drawTrace = currentDrawTrace
                    bestCenter = center
                    shape = currentShape
                }
            }
        }

    private fun visible(z: Complex): Complex =
        motion.move(z)

    ////////////////////////////////////////////////
    fun onDraw(canvas: Canvas) =
        when {
            updating || updateOnce -> updateCanvas(canvas)
            drawTrace -> canvas.drawTraceCanvas()
            else -> onCanvas(canvas) { drawVisible() } // pause and not drawTrace
        }

    /** Reset trace and invalidate */
    private fun retrace() {
        tryRetrace()
        trace?.canvas?.concat(motion)
        redrawTraceOnce = drawTrace
        if (!updating) {
            onTraceCanvas {
                drawVisible()
            }
        }
        presenter?.redraw()
    }

    private fun tryRetrace() {
        var done = false
        var canvasFactor = values.canvasFactor
        presenter?.getSize()?.let { (width: Int, height: Int) ->
            while (!done) {
                try {
                    trace = Trace(width, height)
                    done = true
                } catch (e: OutOfMemoryError) {
                    e.printStackTrace()
                    if (canvasFactor > 1) {
                        val nextFactor = canvasFactor - 1
                        Log.w(TAG, "too large canvasFactor: $canvasFactor -> $nextFactor")
                        toastEmitter?.formatToast(R.string.canvas_factor_oom_toast, canvasFactor, nextFactor)
                        canvasFactor = nextFactor
                    } else {
                        Log.e(TAG, "min canvasFactor  $canvasFactor is too large! Retrying...")
                        toastEmitter?.formatToast(R.string.minimal_canvas_factor_oom_toast, canvasFactor)
                    }
                }
            }
            if (canvasFactor != values.canvasFactor)
                sharedPreferencesModel.set(options.canvasFactor, canvasFactor)
        }
    }

    private fun updateCanvas(canvas: Canvas) {
        val timeToUpdate: Boolean = System.currentTimeMillis() - lastUpdateTime >= updateDt.value
        if (updating && timeToUpdate) {
            val times = values.speed.roundToInt()
            drawTimes(times)
        }
        drawUpdatedCanvas(canvas)
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
            onCanvas(canvas) { drawVisible() }
        }
    }

    fun drawTimes(times: Int = 1) {
        if (times <= 1) {
            updateCircles()
        } else {
            onTraceCanvas {
                drawCirclesTimes(times)
            }
        }
        lastUpdateTime = System.currentTimeMillis()
        statHolder?.updateStat(times) // FIX: wrong stat
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
            shape = shape, showAllCircles = values.showAllCircles
        )
    }

    private inline fun Canvas.drawCirclesTimes(times: Int) {
        circleGroup.drawTimes(
            times = times, canvas = this,
            shape = shape, showAllCircles = values.showAllCircles, reverse = values.reverseMotion
        )
    }

    private inline fun updateCircles() {
        circleGroup.update(values.reverseMotion)
    }
    ////////////////////////////////////////////////

    companion object {
        private const val TAG: String = "DduRepresentation"
        private val DEFAULT_PAINT =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
            }
        private const val DEFAULT_DRAW_TRACE = true
        private const val DEFAULT_UPDATING = true
    }
}
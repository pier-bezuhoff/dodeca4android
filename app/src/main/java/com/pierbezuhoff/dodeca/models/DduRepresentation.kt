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
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.DduAttributesHolder
import com.pierbezuhoff.dodeca.data.DduOptionsChangeListener
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.SuspendableCircleGroup
import com.pierbezuhoff.dodeca.data.Trace
import com.pierbezuhoff.dodeca.data.options
import com.pierbezuhoff.dodeca.data.values
import com.pierbezuhoff.dodeca.ui.DodecaGestureDetector
import com.pierbezuhoff.dodeca.utils.Just
import com.pierbezuhoff.dodeca.utils.Once
import com.pierbezuhoff.dodeca.utils.Sleeping
import com.pierbezuhoff.dodeca.utils.asFF
import com.pierbezuhoff.dodeca.utils.dx
import com.pierbezuhoff.dodeca.utils.dy
import com.pierbezuhoff.dodeca.utils.mean
import com.pierbezuhoff.dodeca.utils.minus
import com.pierbezuhoff.dodeca.utils.move
import com.pierbezuhoff.dodeca.utils.sx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.math3.complex.Complex
import kotlin.math.roundToInt

class DduRepresentation(override val ddu: Ddu) :
    DodecaGestureDetector.ScrollListener,
    DodecaGestureDetector.ScaleListener,
    DduOptionsChangeListener,
    DduAttributesHolder
{
    interface Presenter : LifecycleOwner, CoroutineScope {
        fun getCenter(): Complex?
        /** return (width, height) or null */
        fun getSize(): Pair<Int, Int>?
        fun redraw()
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

    private val updateScheduler: UpdateScheduler = UpdateScheduler()
    private val drawer: Drawer = Drawer()

    private val paint: Paint = Paint(DEFAULT_PAINT)

    val circleGroup: SuspendableCircleGroup = SuspendableCircleGroup(ddu.circles, paint)
    override var updating: Boolean = DEFAULT_UPDATING
        set(value) { field = value; changeUpdating(value) }
    override var drawTrace: Boolean = ddu.drawTrace ?: DEFAULT_DRAW_TRACE
        set(value) { field = value; changeDrawTrace(value) }
    override var shape: Shapes = ddu.shape
        set(value) { field = value; changeShape(value) }

    private val motion: Matrix = Matrix() // visible(z) = motion.move(z)
    private var trace: Trace? = null

    private var redrawTraceOnce: Boolean by Once()
    private var updateOnce: Boolean by Once()

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
        // drawing main loop
        with(presenter) {
            launch(coroutineContext) {
                delay(INITIAL_DELAY)
                while (isActive) {
                    if (updating)
                        redraw()
                    delay(DT_IN_MILLISECONDS)
                }
            }
        }
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

    fun draw(canvas: Canvas) =
        drawer.draw(canvas)

    fun clearTrace() =
        drawer.clearTrace()

    fun oneStep() {
        val batch: Int = values.speed.roundToInt()
        drawer.drawTimes(batch)
        updateOnce = true
        presenter?.redraw()
    }

    fun requestUpdateOnce() {
        updateOnce = true
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

    suspend fun updateTimes(times: Int) =
        circleGroup.suspendableUpdateTimes(times, reverse = values.reverseMotion)

    private fun visible(z: Complex): Complex =
        motion.move(z)

    override fun onShowAllCircles(showAllCircles: Boolean) {
        presenter?.redraw()
    }

    override fun onAutocenterAlways(autocenterAlways: Boolean) {
        // MAYBE: invoke only when changed
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

    private fun changeShape(newShape: Shapes) {
        presenter?.redraw()
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

    private inner class Drawer {
        fun draw(canvas: Canvas) =
            when {
                updating || updateOnce -> updateCanvas(canvas)
                drawTrace -> canvas.drawTraceCanvas()
                else -> onCanvas(canvas) {
                    drawVisible()
                }
            }

        /** Reset trace and invalidate */
        fun clearTrace() {
            tryClearTrace()
            trace?.canvas?.concat(motion)
            redrawTraceOnce = drawTrace
            if (!updating) {
                onTraceCanvas {
                    drawVisible()
                }
            }
            presenter?.redraw()
        }

        private fun tryClearTrace() {
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
            if (updating && updateScheduler.timeToUpdate()) {
                val times = values.speed.roundToInt()
                drawer.drawTimes(times)
                statHolder?.updateStat(times)
                updateScheduler.doUpdate()
            }
            drawer.drawUpdatedCanvas(canvas)
        }

        fun drawTimes(times: Int = 1) {
            if (times <= 1) {
                circleGroup.update(reverse = values.reverseMotion)
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
                shape = shape, showAllCircles = values.showAllCircles
            )
        }

        private inline fun Canvas.drawCirclesTimes(times: Int) {
            circleGroup.drawTimes(
                times = times, canvas = this,
                shape = shape, showAllCircles = values.showAllCircles, reverse = values.reverseMotion
            )
        }
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
        private const val FPS = 60 // empirical
        private const val DT_IN_MILLISECONDS: Long = (1000f / FPS).toLong() // interval between presenter.redraw() calls
        private const val INITIAL_DELAY: Long = 1
    }
}


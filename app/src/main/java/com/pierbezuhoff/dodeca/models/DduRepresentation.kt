package com.pierbezuhoff.dodeca.models

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.CircleGroup
import com.pierbezuhoff.dodeca.data.CircleGroupImpl
import com.pierbezuhoff.dodeca.data.Ddu
import com.pierbezuhoff.dodeca.data.Shapes
import com.pierbezuhoff.dodeca.data.Trace
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

class DduRepresentation(val ddu: Ddu) :
    DodecaGestureDetector.ScrollListener,
    DodecaGestureDetector.ScaleListener
{
    interface Presenter : LifecycleOwner {
        fun getCenter(): Complex?
        fun redraw()
        fun redrawTrace()
        fun requestRedrawTraceOnce()
    }
    private var presenter: Presenter? = null

    private val paint: Paint = Paint(DEFAULT_PAINT)

    private val circleGroup: CircleGroup = CircleGroupImpl(ddu.circles, paint)
    private var updating: Boolean = DEFAULT_UPDATING

    private var drawTrace: Boolean = ddu.drawTrace ?: DEFAULT_DRAW_TRACE
    private var shape: Shapes = ddu.shape

    private val motion: Matrix = Matrix() // visible(x) = motion.move(x)
    private val trace: Trace = Trace()

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
        presenter.requestRedrawTraceOnce()
        presenter.redraw()
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

    private fun autocenterize() {
        // maybe: when canvasFactor * scale ~ 1
        // try to fit screen
        // BUG: sometimes skip to else
        val scale: Float = motion.sx
        if (drawTrace && trace.initialized &&
            values.canvasFactor == 1 &&
            1 - 1e-4 < scale && scale < 1 + 1e-1
        ) {
            scroll(-trace.motion.dx, -trace.motion.dy)
        } else {
            presenter?.getCenter()?.let { center ->
                val shownCircles: List<CircleFigure> = circleGroup.figures.filter(CircleFigure::show)
                val visibleCenter = shownCircles.map { visible(it.center) }.mean()
                val (dx, dy) = (center - visibleCenter).asFF()
                scroll(dx, dy)
            }
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
                presenter?.redrawTrace()
            else {
                trace.motion.transformation()
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

    companion object {
        private const val TAG: String = "DduRepresentation"
        private val DEFAULT_PAINT =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
            }
        private const val DEFAULT_DRAW_TRACE = true
        private const val DEFAULT_UPDATING = true
        private val DEFAULT_SHAPE = Shapes.CIRCLE
    }
}
package com.pierbezuhoff.dodeca.data.circlegroup

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.CoordinateSystem3D
import com.pierbezuhoff.dodeca.data.PerspectiveCamera3D
import com.pierbezuhoff.dodeca.data.Shape
import com.pierbezuhoff.dodeca.data.Vector3
import com.pierbezuhoff.dodeca.models.OptionsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarrayOf

// NOTE: rs is hijacked for -1 = invisible | +1 = visible
// MAYBE: small 100K-point lru cache to preserve for transformations
internal class ProjectiveCircles3D(
    figures: List<CircleFigure>,
    optionValues: OptionsManager.Values,
    paint: Paint,
) : ProjectiveCircles(figures, optionValues, paint)
    , CoordinateSystem3D by PerspectiveCamera3D(optionValues.projR.toDouble())
{
    private val cubePaint = Paint(paint).apply { color = Color.RED }
    private val cube: MutableList<Vector3> = mutableListOf()
    private val edgeSize = 11
    private val cubeVolume = edgeSize*edgeSize*edgeSize
    override val xs: DoubleArray = DoubleArray(cubeVolume)
    override val ys: DoubleArray = DoubleArray(cubeVolume)
    override val rs: DoubleArray = DoubleArray(cubeVolume)

    init {
//        moveForward(-1.5*sphereRadius)
//        project()
        fillCube()
    }

    private fun fillCube() {
        val half = edgeSize.div(2)
        val spacing = sphereRadius/half
        for (i in -half..half)
            for (j in -half..half)
                for (k in -half..half) {
                    cube.add(mk.ndarrayOf(spacing*i, spacing*half, spacing*half))
                }
    }

    // used after update or any camera movement
    private fun _project() {
//        Log.i(TAG, "project")
        shownIndices.forEach { cIx ->
//            Log.i(TAG, "#$cIx: ${poles[cIx].showAsV3()}")
            project2D(poles[cIx].v4ToV3())?.let { (x, y) -> // null pole
                xs[cIx] = x
                ys[cIx] = y
                rs[cIx] = +1.0
            } ?: run {
                rs[cIx] = -1.0
            }
        }
    }

    private fun project() { // for cube
        for (i in cube.indices) {
            project2D(cube[i])?.let { (x, y) ->
                xs[i] = x
                ys[i] = y
                rs[i] = +1.0
            } ?: run {
                rs[i] = -1.0
            }
        }
        Log.i(TAG, "#0: ${if (rs[0] == 1.0) "(${xs[0]}, ${ys[0]})" else "-"}")
        Log.i(TAG, "#-1: ${if (rs[cubeVolume-1] == 1.0) "(${xs[cubeVolume-1]}, ${ys[cubeVolume-1]})" else "-"}")
    }

    override fun doTransformation(transformation: CoordinateSystem3D.Transformation) {
        Log.i(TAG, "do transformation $transformation")
        super.doTransformation(transformation)
        project()
    }

    override fun applyMatrices() {
        super.applyMatrices()
        project()
    }

    override fun applyAllMatrices() {
        super.applyAllMatrices()
        project()
    }

    override fun draw(canvas: Canvas, shape: Shape) {
        for (i in cube.indices)
            if (rs[i] == +1.0) {
                val x = xs[i].toFloat()
                val y = ys[i].toFloat()
                canvas.drawPoint(x, y, cubePaint)
            }
//        for (i in shownIndices)
//            draw(i, canvas)
    }

    private inline fun _draw(i: Ix, canvas: Canvas) {
        if (rs[i] == +1.0) {
            val x = xs[i].toFloat()
            val y = ys[i].toFloat()
            canvas.drawPoint(x, y, paints[i])
        }
    }

    override fun drawTimes(times: Int, reverse: Boolean, canvas: Canvas, shape: Shape) {
        repeat(times) {
            draw(canvas, shape)
            _update(reverse)
        }
        draw(canvas, shape)
    }

    override suspend fun suspendableDrawTimes(times: Int, reverse: Boolean, canvas: Canvas, shape: Shape) {
        repeat(times) {
            withContext(Dispatchers.Default) {
                draw(canvas, shape)
                _update(reverse)
            }
        }
        withContext(Dispatchers.Default) {
            draw(canvas, shape)
        }
    }

    companion object {
        private const val TAG: String = "ProjectiveCircles3D"
    }
}
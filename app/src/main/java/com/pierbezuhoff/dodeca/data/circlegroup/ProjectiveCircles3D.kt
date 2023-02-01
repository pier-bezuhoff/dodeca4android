package com.pierbezuhoff.dodeca.data.circlegroup

import android.graphics.Canvas
import android.graphics.Paint
import com.pierbezuhoff.dodeca.data.Camera
import com.pierbezuhoff.dodeca.data.CircleFigure
import com.pierbezuhoff.dodeca.data.CoordinateSystem3D
import com.pierbezuhoff.dodeca.data.Shape

// NOTE: rs hijacked for zs
internal class ProjectiveCircles3D(
    figures: List<CircleFigure>,
    paint: Paint,
    sphereRadius: Double
) : ProjectiveCircles(figures, paint, sphereRadius)
    , CoordinateSystem3D by Camera()
{
    init {
        moveForward(-1.5*sphereRadius)
    }

    // after update or any camera movement
    private fun project() {
        shownIndices.forEach { cIx ->
            val (x,y,z) = transform(poles[cIx])
            xs[cIx] = x
            ys[cIx] = y
            rs[cIx] = z // z >= 0 => visible
        }
    }

    override fun draw(canvas: Canvas, shape: Shape) {
        for (i in shownIndices)
            draw(i, canvas)
    }

    private inline fun draw(i: Ix, canvas: Canvas) {
        if (rs[i] >= 0) {
            val x = xs[i].toFloat()
            val y = ys[i].toFloat()
            canvas.drawPoint(x, y, paints[i])
        }
    }


}
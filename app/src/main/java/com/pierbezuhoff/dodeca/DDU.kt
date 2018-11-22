package com.pierbezuhoff.dodeca

import android.graphics.Color
import org.apache.commons.math3.complex.Complex
import java.io.File
import java.io.InputStream

internal enum class Mode { // for scanning .ddu, before <mode parameter>
    NO, GLOBAL, RADIUS, X, Y, BORDER_COLOR, FILL, RULE, CIRCLE_AUX;
    fun next(): Mode = Mode.values().elementAtOrElse( ordinal + 1, { Mode.CIRCLE_AUX })
}

internal data class CircleParams(
    var radius: Double? = null, var x: Double? = null, var y: Double? = null,
    var borderColor: Int? = null, var fill: Boolean? = null, var rule: String? = null) {
    /* CircleParams MUST have [radius], [x] and [y] */
    fun toCircle(): Circle {
        return Circle(Complex(x!!, y!!), radius!!, borderColor, fill, rule)
    }
}

/* in kotlin colors are negative and strangely inverted, e.g.
Color.BLACK = - 0xffffff - 1, etc.
`toColor` is involution
 */
//internal fun Int.toColor(): Int = Color.parseColor(this.toString()) //(0xff000000 xor this.toLong()).toInt()
 internal fun Int.toColor(): Int {
     val red = (this and 0xff0000) shr 16
     val green = (this and 0x00ff00) shr 8
     val blue = this and 0x0000ff
     val color = (blue shl 16) + (green shl 8) + red
     return color.inv() xor 0xffffff
 }

// MAYBE: serialize DDU to json
class DDU(var backgroundColor: Int = defaultBackgroundColor, var circles: List<Circle>, var file: File? = null) {

    val centroid: Complex get() {
        val sum = circles.fold(Complex.ZERO) { x, circle -> x + circle.center }
        return sum / circles.size.toDouble()
    }
    val visibleCentroid: Complex get() {
        val visibleCircles = circles.filter { it.show }
        val sum = visibleCircles.fold(Complex.ZERO) { x, circle -> x + circle.center }
        return sum / visibleCircles.size.toDouble()
    }

    fun translateAndScale(dx: Double = 0.0, dy: Double = 0.0, scaleFactor: Double = 1.0, center: Complex = Complex.ZERO) {
        circles.forEach {
            it.translate(dx, dy)
            it.scale(scaleFactor, center)
        }
    }

    fun save(file: File? = null) {
        val doBackup = this.file?.exists() == true && file == null || this.file == file
        if (doBackup)
            this.file!!.copyTo(File(this.file!!.absolutePath + "~"), overwrite = true)
        file?.let {
            this.file = file
        }
        this.file?.printWriter()?.use { writer ->
            writer.println("Dodeca for Android")
            setOf(backgroundColor, defaultBackgroundColor).forEach {
                writer.println("global")
                writer.println(it.toString())
            }
            circles.forEach { circle ->
                writer.println("\ncircle:")
                setOf(
                    circle.radius.toString(),
                    circle.x.toString(),
                    circle.y.toString(),
                    circle.borderColor.toColor().toString(),
                    (if (circle.fill) 1 else 0).toString()
                ).forEach {
                    writer.println(it)
                }
                if (circle.dynamic)
                    writer.println(circle.rule)
            }
        }
    }

    companion object {
        const val defaultBackgroundColor: Int = Color.WHITE

        fun readStream(stream: InputStream): DDU {
            var backgroundColor = defaultBackgroundColor
            val circles: MutableList<Circle> = mutableListOf()
            var nGlobals = 0
            var mode: Mode = Mode.NO
            var params = CircleParams()
            stream.reader().forEachLine {
                when {
                    it.startsWith("global") -> mode = Mode.GLOBAL
                    mode == Mode.GLOBAL && it.isNotBlank() -> {
                        if (nGlobals == 0)
                            backgroundColor = it.toInt().toColor()
                        // ignoring other (2) globals
                        nGlobals++
                        mode = Mode.NO
                    }
                    it.startsWith("circle:") -> {
                        if (mode > Mode.Y) { // we have at least radius and center
                            circles.add(params.toCircle())
                        }
                        params = CircleParams() // clear params
                        mode = Mode.RADIUS
                    }
                    mode >= Mode.RADIUS && it.isNotBlank() -> {
                        when (mode) {
                            Mode.RADIUS -> params.radius = it.replace(',', '.').toDouble()
                            Mode.X -> params.x = it.replace(',', '.').toDouble()
                            Mode.Y -> params.y = it.replace(',', '.').toDouble()
                            Mode.BORDER_COLOR -> params.borderColor = it.toInt().toColor()
                            Mode.FILL -> params.fill = it != "0" // carefully
                            Mode.RULE -> params.rule = it
                        }
                        mode = mode.next()
                    }
                }
            }
            return DDU(backgroundColor, circles)
        }

        fun read(file: File): DDU {
            val ddu = readStream(file.inputStream())
            ddu.file = file
            return ddu
        }
    }
}

val exampleDDU: DDU = run {
    val circle = Circle(Complex(300.0, 400.0), 200.0, Color.BLUE, rule = "12")
    val circle1 = Circle(Complex(450.0, 850.0), 300.0, Color.LTGRAY)
    val circle2 = Circle(Complex(460.0, 850.0), 300.0, Color.DKGRAY)
    val circle0 = Circle(Complex(0.0, 0.0), 100.0, Color.GREEN)
    val circles = listOf(
        circle,
        circle1,
        circle2,
        circle0,
        circle0.invert(circle),
        circle1.invert(circle),
        Circle(Complex(600.0, 900.0), 10.0, Color.RED, fill = true)
    )
    DDU(Color.WHITE, circles)
}
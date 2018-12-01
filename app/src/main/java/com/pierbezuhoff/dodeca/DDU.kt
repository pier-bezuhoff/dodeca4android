package com.pierbezuhoff.dodeca

import android.graphics.Color
import android.net.Uri
import org.apache.commons.math3.complex.Complex
import java.io.InputStream
import java.io.OutputStream

internal enum class Mode { // for scanning .ddu, before <mode parameter>
    NO, GLOBAL, RADIUS, X, Y, BORDER_COLOR, FILL, RULE, CIRCLE_AUX;
    fun next(): Mode = Mode.values().elementAtOrElse(ordinal + 1) { Mode.CIRCLE_AUX }
}

internal data class CircleParams(
    var radius: Double? = null, var x: Double? = null, var y: Double? = null,
    var borderColor: Int? = null, var fill: Boolean? = null, var rule: String? = null) {
    /* CircleParams MUST have [radius], [x] and [y] */
    fun toCircle(): Circle = Circle(Complex(x!!, y!!), radius!!, borderColor, fill, rule)
}

/* In C++ (with it ddu was created) color is BBGGRR, but in Java -- AARRGGBB */
internal val Int.red: Int get() = (this and 0xff0000) shr 16
internal val Int.green: Int get() = (this and 0x00ff00) shr 8
internal val Int.blue: Int get() = this and 0x0000ff

/* BBGGRR -> AARRGGBB */
internal fun Int.toColor(): Int = Color.rgb(blue, green, red)

/* AARRGGBB -> BBGGRR */
internal fun Int.fromColor(): Int = blue shl 16 + green shl 8 + red

// maybe: serialize DDU to json
class DDU(var backgroundColor: Int = defaultBackgroundColor, var circles: List<Circle>, var uri: Uri? = null) {

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

    fun saveStream(stream: OutputStream) {
        // maybe: use buffered stream
        stream.use {
            val writeln = { s: String -> it.write(s.toByteArray()) }
            writeln("Dodeca for Android")
            setOf(
                backgroundColor.fromColor(),
                defaultBackgroundColor.fromColor()
            ).forEach { param ->
                writeln("global")
                writeln(param.toString())
            }
            circles.forEach { circle ->
                writeln("\ncircle:")
                setOf(
                    circle.radius,
                    circle.x,
                    circle.y,
                    circle.borderColor.fromColor(),
                    if (circle.fill) 1 else 0
                ).forEach { param ->
                    writeln(param.toString())
                }
                if (circle.dynamic) // dynamic => rule != null
                    writeln(circle.rule!!)
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
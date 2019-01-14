package com.pierbezuhoff.dodeca

import android.graphics.Color
import android.util.Log
import org.apache.commons.math3.complex.Complex
import java.io.File
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
    fun toCircleFigure(): CircleFigure =
        CircleFigure(x!!, y!!, radius!!, borderColor, fill, rule)
}

/* In C++ (with it ddu was created) color is BBGGRR, but in Java -- AARRGGBB */
internal val Int.red: Int get() = (this and 0xff0000) shr 16
internal val Int.green: Int get() = (this and 0x00ff00) shr 8
internal val Int.blue: Int get() = this and 0x0000ff

/* BBGGRR -> AARRGGBB */
internal fun Int.toColor(): Int = Color.rgb(blue, green, red)
// = ((blue shl 16) + (green shl 8) + red).inv() xor 0xffffff

/* AARRGGBB -> BBGGRR */
internal fun Int.fromColor(): Int =
    (Color.blue(this) shl 16) + (Color.green(this) shl 8) + Color.red(this)


// maybe: serialize DDU to json
class DDU(
    var backgroundColor: Int = defaultBackgroundColor,
    private var restGlobals: List<Int> = emptyList(), // unused
    var drawTrace: Boolean? = null,
    var bestCenter: Complex? = null, // cross-(screen size)
    var shape: Shapes = defaultShape,
    var showOutline: Boolean = defaultShowOutline,
    var circles: List<CircleFigure> = emptyList(),
    var file: File? = null
) {

    // NOTE: copy(newRule = null) resolves overload ambiguity
    fun copy() =
        DDU(
            backgroundColor, restGlobals.toList(), drawTrace, bestCenter, shape, showOutline,
            circles.map { it.copy(newRule = null) }, file)

    override fun toString(): String = """DDU(
        |  backgroundColor = ${backgroundColor.fromColor()}
        |  restGlobals = $restGlobals
        |  drawTrace = $drawTrace
        |  bestCenter = $bestCenter
        |  shape = $shape
        |  showOutline = $showOutline
        |  file = $file
        |  figures = $circles
        |)
    """.trimMargin()

    fun saveStream(stream: OutputStream) {
        // maybe: use buffered stream
        stream.use { outputStream ->
            val writeln = { s: String -> outputStream.write("$s\n".toByteArray()) }
            val appVersion = "1.1" // TODO: fetch from somewhere
            writeln("Dodeca Look $appVersion for Android")
            val globals: MutableList<String> = listOf(
                backgroundColor.fromColor(),
                *restGlobals.toTypedArray()
            ).map { it.toString() }.toMutableList()
            globals.forEach { param ->
                writeln("global")
                writeln(param)
            }
            drawTrace?.let { writeln("drawTrace: $it") }
            bestCenter?.let { writeln("bestCenter: ${it.real} ${it.imaginary}") }
            writeln("shape: $shape")
            writeln("showOutline: $showOutline")
            circles.forEach { circle ->
                writeln("\ncircle:")
                listOf(
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
        val defaultShape: Shapes = Shapes.CIRCLE
        const val defaultShowOutline = false

        fun readFile(file: File): DDU {
            return readStream(file.inputStream()).apply { this.file = file }
        }

        fun readStream(stream: InputStream): DDU {
            var backgroundColor: Int = defaultBackgroundColor
            val restGlobals: MutableList<Int> = mutableListOf()
            var drawTrace: Boolean? = null
            var bestCenter: Complex? = null
            var shape: Shapes = defaultShape
            var showOutline: Boolean = defaultShowOutline
            val circles: MutableList<CircleFigure> = mutableListOf()
            var nGlobals = 0
            var mode: Mode = Mode.NO
            var params = CircleParams()
            val appendCircle: () -> Unit = {
                if (mode > Mode.Y) { // we have at least radius and center
                    circles.add(params.toCircleFigure())
                } else if (mode >= Mode.RADIUS) {
                    Log.w("DDU.readStream", "Unexpected end of circle, discarding...")
                }
            }
            stream.reader().forEachLine {
                when {
                    mode == Mode.GLOBAL && it.isNotBlank() -> {
                        when (nGlobals) {
                            0 -> backgroundColor = it.toInt().toColor()
                            1, 2 -> restGlobals.add(it.toInt()) // don't know, what this 2 means ("howInvers" and "howAnim")
                            3 -> drawTrace = it != "0"
                            4 -> it.split(" ").let {
                                if (it.size == 2) {
                                    val x = it[0].toDoubleOrNull()
                                    val y = it[1].toDoubleOrNull()
                                    if (x != null && y != null)
                                        bestCenter = Complex(x, y)
                                }
                            }
                        }
                        nGlobals++
                        mode = Mode.NO
                    }
                    it.startsWith("circle:") -> {
                        appendCircle()
                        params = CircleParams() // clear params
                        mode = Mode.RADIUS
                    }
                    mode == Mode.NO -> when {
                        it.startsWith("global") -> mode = Mode.GLOBAL
                        it.startsWith("drawTrace:") ->
                            drawTrace = it.substringAfter("drawTrace:").trim().toBoolean()
                        it.startsWith("bestCenter:") ->
                            it.substringAfter("bestCenter:").trim().split(" ").let {
                                if (it.size == 2) {
                                    val x = it[0].toDoubleOrNull()
                                    val y = it[1].toDoubleOrNull()
                                    if (x != null && y != null)
                                        bestCenter = Complex(x, y)
                                }
                            }
                        it.startsWith("shape:") ->
                            Shapes.valueOfOrNull(it.substringAfter("shape:").trim())?.let {
                                shape = it
                            }
                        it.startsWith("showOutline:") ->
                            it.substringAfter("showOutline:").trim().let {
                                showOutline = it.toBoolean()
                            }
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
            appendCircle()
            return DDU(backgroundColor, restGlobals, drawTrace, bestCenter, shape, showOutline, circles)
        }
    }
}

val exampleDDU: DDU = run {
    val circle = CircleFigure(300.0, 400.0, 200.0, Color.BLUE, rule = "12")
    val circle1 = CircleFigure(450.0, 850.0, 300.0, Color.LTGRAY)
    val circle2 = CircleFigure(460.0, 850.0, 300.0, Color.DKGRAY)
    val circle0 = CircleFigure(0.0, 0.0, 100.0, Color.GREEN)
    val circles: List<CircleFigure> = listOf(
        circle,
        circle1,
        circle2,
        circle0,
        circle0.inverted(circle),
        circle1.inverted(circle),
        CircleFigure(600.0, 900.0, 10.0, Color.RED, fill = true)
    )
    DDU(Color.WHITE, circles = circles)
}
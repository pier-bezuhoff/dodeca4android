package com.pierbezuhoff.dodeca

import android.graphics.Color
import org.apache.commons.math3.complex.Complex
import java.io.File
import java.io.FileInputStream

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

/* in kotlin colors are negative and strangely inverted
Color.BLACK = - 0xffffff - 1, etc.
 */
fun toColor(color: Int): Int = color.inv() xor 0xffffff

class DDU(val backgroundColor: Int = defaultBackgroundColor, val circles: List<Circle>) {
    companion object {
        const val defaultBackgroundColor: Int = Color.WHITE

        fun read(stream: FileInputStream): DDU {
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
                            backgroundColor = toColor(it.toInt())
                        // ignoring other (2) globals
                        nGlobals ++
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
                            Mode.BORDER_COLOR -> params.borderColor = toColor(it.toInt())
                            Mode.FILL -> params.fill = it != "0"
                            Mode.RULE -> params.rule = it
                        }
                        mode = mode.next()
                    }
                }
            }
            return DDU(backgroundColor, circles)
        }

        fun readFile(filename: String): DDU {
            return read(File(filename).inputStream())
        }
    }
}

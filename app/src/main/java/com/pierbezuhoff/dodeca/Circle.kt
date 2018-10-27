package com.pierbezuhoff.dodeca

import android.util.JsonReader
import android.util.JsonWriter
import org.apache.commons.math3.complex.Complex
import org.json.JSONException
import java.io.*

data class Circle(val center: Complex, val radius: Double, val borderColor: Int, val fill: Boolean, val rule: String?) {
    val x: Double get() = center.real
    val y: Double get() = center.imaginary
    val r2: Double get() = radius * radius

    constructor(center: Complex, radius: Double, borderColor: Int? = null, fill: Boolean? = null, rule: String? = null)
            : this(center, radius, borderColor ?: defaultBorderColor, fill ?: defaultFill, rule ?: defaultRule)

    /* Inverts complex [point] with respect to [this]
     * [point] == [this].center => Complex.INF */
    fun invert(point: Complex): Complex =
        if (point == center) Complex.INF
        else center + r2 / (point - center).conjugate()

    /* Inverts [circle] with respect to [this] */
    fun invert(circle: Circle): Circle {
        var (c, r) = circle // not val to change r if c <- this
        if (center == c)
            return Circle(c, r2 / r)
        val d = c - center
        val d2 = d.abs2()
        if (d.abs() == r) // c <- this
            r += 1e-6 // cheat to avoid returning line
            // return Line(c, d)
        val newCenter = center + r2 * d / (d2 - r * r)
        val newRadius = r2 * r / Math.abs(d2 - r * r)
        return Circle(newCenter, newRadius)
        /* NOTE: center = 0, radius = 1; c <- |R
        => Circle(a/(a^2-r^2), r/|a^2-r^2|)
         */
    }

    companion object {
        const val defaultBorderColor: Int = 1
        const val defaultFill = false
        val defaultRule: String? = null
    }
}

fun json2circles(filename: String): List<Circle> {
    val file = File(filename)
    val reader = JsonReader(InputStreamReader(file.inputStream(), "UTF-8"))
    val result = mutableListOf<Circle>()
    reader.beginArray()
    while (reader.hasNext()) {
        reader.beginObject()
        var center: Complex? = null
        var radius: Double? = null
        while (reader.hasNext()) {
            val name = reader.nextName()
            when (name) {
                "center" -> center = Complex(reader.nextDouble(), reader.nextDouble())
                "radius" -> radius = reader.nextDouble()
                else -> throw JSONException("Unexpected field: $name")
            }
        }
        if (center != null && radius != null)
            result.add(Circle(center, radius))
        else
            throw JSONException("Not enough field for Circle")
    }
    return result
}

fun circles2json(circles: List<Circle>, filename: String): Unit {
    val file = File(filename)
    val writer = JsonWriter(OutputStreamWriter(file.outputStream(), "UTF-8"))
    writer.setIndent("  ")
    with (writer) {
        beginArray()
        circles.forEach {
            beginObject()
            val (c, r) = it
            name("center"); beginArray(); value(c.real); value(c.imaginary); endArray()
            name("radius"); value(r)
            endObject()
        }
       endArray()
    }
}

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

class DDU(val backgroundColor: Int = defaultBackgroundColor, val circles: List<Circle>) {
    companion object {
        const val defaultBackgroundColor: Int = 16777215

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
                            backgroundColor = it.toInt()
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
                            Mode.BORDER_COLOR -> params.borderColor = it.toInt()
                            Mode.FILL -> params.fill = it.toBoolean()
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

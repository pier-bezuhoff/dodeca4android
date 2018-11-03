package com.pierbezuhoff.dodeca

import android.graphics.Color
import android.util.JsonReader
import android.util.JsonWriter
import org.apache.commons.math3.complex.Complex
import org.json.JSONException
import java.io.*

data class Circle(var center: Complex, val radius: Double, var borderColor: Int, var fill: Boolean, var rule: String?) {
    val x: Double get() = center.real
    val y: Double get() = center.imaginary
    private val r2: Double get() = radius * radius

    constructor(center: Complex, radius: Double, borderColor: Int? = null, fill: Boolean? = null, rule: String? = null)
            : this(center, radius, borderColor ?: defaultBorderColor, fill ?: defaultFill, rule ?: defaultRule)

    /* Inverts complex [point] with respect to [this]
     * [point] == [this].center => Complex.INF */
    fun invert(point: Complex): Complex =
        if (point == center) Complex.INF
        else center + r2 / (point - center).conjugate()

    /* Inverts [this] with respect to [circle] */
    fun invert(circle: Circle): Circle {
        var (c, r) = circle // not val to change r if c <- this
        if (center == c)
            return Circle(center, circle.r2 / radius, borderColor, fill, rule)
        val d = center - c
        val d2 = d.abs2()
        if (d.abs() == radius) // c <- this
            r += 1e-6 // cheat to avoid returning line
            // return Line(c, d)
        val newCenter = c + circle.r2 * d / (d2 - r2)
        val newRadius = circle.r2 * radius / Math.abs(d2 - r2)
        return Circle(newCenter, newRadius, borderColor, fill, rule)
        /* NOTE: c = 0, r = 1; center <- |R
        => Circle(a/(a^2-r2), radius/|a^2-r2|)
         */
    }

    companion object {
        const val defaultBorderColor: Int = Color.BLACK
        const val defaultFill = false
        val defaultRule: String? = null
    }
}

// TODO: check them or delete
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
            throw JSONException("Not enough fields for Circle")
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

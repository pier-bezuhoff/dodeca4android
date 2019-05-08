package com.pierbezuhoff.dodeca.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.withMatrix
import com.pierbezuhoff.dodeca.BuildConfig
import com.pierbezuhoff.dodeca.utils.asFF
import com.pierbezuhoff.dodeca.utils.mean
import com.pierbezuhoff.dodeca.utils.minus
import org.apache.commons.math3.complex.Complex
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.reflect.KMutableProperty0

/* In C++ (with it ddu was created) color is RRGGBB, but in Java -- AABBGGRR
* see Bitmap.Config.ARGB_8888 (https://developer.android.com/reference/android/graphics/Bitmap.Config.html#ARGB_8888) */
private val Int.red: Int get() = (this and 0xff0000) shr 16
private val Int.green: Int get() = (this and 0x00ff00) shr 8
private val Int.blue: Int get() = this and 0x0000ff

/* RRGGBB -> AABBGGRR */
internal fun Int.toColor(): Int = Color.rgb(blue, green, red)
// = ((blue shl 16) + (green shl 8) + red).inv() xor 0xffffff

/* AABBGGRR -> RRGGBB */
internal fun Int.fromColor(): Int =
    (Color.blue(this) shl 16) + (Color.green(this) shl 8) + Color.red(this)


// NOTE: when restGlobals = listOf(4, 4) some magic occurs (rule-less circles are moving in DodecaLook)
class DDU(
    var backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    var restGlobals: List<Int> = emptyList(), // unused
    var drawTrace: Boolean? = null,
    var bestCenter: Complex? = null, // cross-(screen size)
    var shape: Shapes = DEFAULT_SHAPE,
    var circles: List<CircleFigure> = emptyList(),
    var file: File? = null
) {

    val autoCenter get() = circles.filter { it.show }.map { it.center }.mean()
    val complexity: Int get() = circles.sumBy { it.rule?.length ?: 0 }
    private val nSmartUpdates: Int
        get() = (MIN_PREVIEW_UPDATES + values.nPreviewUpdates * 20 / sqrt(1.0 + complexity)).roundToInt()
    private val nUpdates: Int // for preview
        get() = if (values.previewSmartUpdates) nSmartUpdates else values.nPreviewUpdates

    // NOTE: copy(newRule = null) resolves overload ambiguity
    fun copy() =
        DDU(
            backgroundColor, restGlobals.toList(), drawTrace, bestCenter, shape,
            circles.map { it.copy(newRule = null) }, file
        )

    override fun toString(): String = """DDU(
        |  backgroundColor = ${backgroundColor.fromColor()}
        |  restGlobals = $restGlobals
        |  drawTrace = $drawTrace
        |  bestCenter = $bestCenter
        |  shape = $shape
        |  file = $file
        |  figures = $circles
        |)
    """.trimMargin()

    fun saveToFile(file: File) {
        if (!file.exists())
            file.createNewFile()
        saveToStream(file.outputStream())
    }

    fun saveToStream(outputStream: OutputStream) =
        DDUWriter(this).write(outputStream)

    fun saveToStreamForDodecaLook(outputStream: OutputStream) =
        DDUWriter(this).writeForDodecaLook(outputStream)

    fun preview(width: Int, height: Int): Bitmap {
        // used RGB_565 instead of ARGB_8888 for performance (visually indistinguishable)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val circleGroup = CircleGroupImpl(circles, paint)
        val center = Complex((width / 2).toDouble(), (height / 2).toDouble())
        val bestCenter = bestCenter ?: if (values.autocenterPreview) autoCenter else center
        val (dx, dy) = (center - bestCenter).asFF()
        val (centerX, centerY) = center.asFF()
        val scale: Float = PREVIEW_SCALE * width / NORMAL_PREVIEW_SIZE // or use options.preview_size.default
        val matrix = Matrix().apply { postTranslate(dx, dy); postScale(scale, scale, centerX, centerY) }
        canvas.withMatrix(matrix) {
            canvas.drawColor(backgroundColor)
            // load preferences
            if (drawTrace ?: true) {
                // TODO: understand, why drawTimes is slower
                // circleGroup.drawTimes(previewUpdates, canvas = canvas, shape = shape)
                repeat(nUpdates) {
                    circleGroup.draw(canvas, shape = shape)
                    circleGroup.update()
                }
                circleGroup.draw(canvas, shape = shape)
            } else {
                circleGroup.draw(canvas, shape = shape)
            }
        }
/*        Log.i(
            TAG,
            "preview \"${file?.nameWithoutExtension}\", smart: ${values.previewSmartUpdates}, complexity = $complexity, nUpdates = $nUpdates"
        )*/
        return bitmap
    }

    companion object {
        private const val TAG: String = "DDU"
        const val DEFAULT_BACKGROUND_COLOR: Int = Color.WHITE
        val DEFAULT_SHAPE: Shapes = Shapes.CIRCLE
        const val MIN_PREVIEW_UPDATES = 10
        const val NORMAL_PREVIEW_SIZE = 300 // with this preview_size preview_scale was tuned
        const val PREVIEW_SCALE = 0.5f

        fun fromFile(file: File): DDU =
            fromStream(file.inputStream()).apply {
                this.file = file
            }

        private fun fromStream(stream: InputStream): DDU =
            DDUReader(stream.reader()).read()
    }
}


private class CircleBuilder {
    var radius: Double? = null
    var x: Double? = null
    var y: Double? = null
    var color: Int? = null
    var fill: Boolean? = null
    var rule: String? = null
    var borderColor: Int? = null

    class NotEnoughBuildParametersException : Exception(
        "CircleBuilder must have radius, x and y in order to build CircleFigure"
    )

    fun build(): CircleFigure {
        if (x == null || y == null || radius == null)
            throw NotEnoughBuildParametersException()
        return CircleFigure(x!!, y!!, radius!!, color, fill, rule, borderColor)
    }
}


private class DDUBuilder {
    var backgroundColor: Int = DDU.DEFAULT_BACKGROUND_COLOR
    val restGlobals: MutableList<Int> = mutableListOf()
    var drawTrace: Boolean? = null
    var bestCenter: Complex? = null
    var shape: Shapes = DDU.DEFAULT_SHAPE
    val circleFigures: MutableList<CircleFigure> = mutableListOf()

    fun addCircleFigure(circleFigure: CircleFigure) {
        circleFigures.add(circleFigure)
    }

    fun tryBuildAndAddCircleFigure(circleBuilder: CircleBuilder) {
        try {
            val circleFigure = circleBuilder.build()
            addCircleFigure(circleFigure)
        } catch (e: CircleBuilder.NotEnoughBuildParametersException) {
            e.printStackTrace()
        }
    }

    fun build(): DDU =
        DDU(backgroundColor, restGlobals, drawTrace, bestCenter, shape, circleFigures)
}


private class DDUReader(private val reader: InputStreamReader) {
    private enum class Mode { // for scanning .ddu, before <mode parameter>
        NO, GLOBAL, RADIUS, X, Y, COLOR, FILL, RULE, CIRCLE_AUX;
        fun next(): Mode = values().elementAtOrElse(ordinal + 1) { CIRCLE_AUX }
    }

    private val dduBuilder = DDUBuilder()
    private var mode: Mode = Mode.NO // mode == FILL === before scanning [fill] parameter
    private var nGlobals: Int = 0
    private var circleBuilder = CircleBuilder()
    private lateinit var trimmedLine: String

    private fun maybeReadBoolean(s: String = trimmedLine): Boolean? {
        return when (s) {
            "0", "false" -> false
            "1", "true" -> true
            else -> null
        }
    }

    private fun maybeReadDouble(s: String = trimmedLine): Double? =
        s.replace(',', '.').toDoubleOrNull()

    private fun maybeReadInt(s: String = trimmedLine): Int? =
        s.toIntOrNull()

    private fun maybeReadColor(s: String = trimmedLine): Int? =
        s.toIntOrNull()?.toColor()

    private fun maybeReadComplex(s: String = trimmedLine): Complex? {
        s.split(" ").let {
            if (it.size == 2) {
                val x = it[0].toDoubleOrNull()
                val y = it[1].toDoubleOrNull()
                if (x != null && y != null)
                    return Complex(x, y)
            }
        }
        return null
    }

    private fun maybeReadShape(s: String = trimmedLine): Shapes? =
        Shapes.valueOfOrNull(s)

    private fun maybeReadRule(s: String = trimmedLine): String? =
        if (Regex("n?\\d+").matches(s)) s else null

    private infix fun <T : Any?> KMutableProperty0<T>.maybeSetGlobalTo(maybeValue: T?) {
        maybeValue?.let { value ->
            this.set(value)
            nGlobals++
        }
    }

    private infix fun <T : Any?> KMutableProperty0<T>.maybeSetTo(maybeValue: T?) {
        maybeValue?.let { value ->
            this.set(value)
        }
    }

    private fun trimmedSubstringAfter(prefix: String) =
        trimmedLine.substringAfter(prefix).trim()

    private fun readLegacyGlobalLine() {
        when (nGlobals) {
            0 -> dduBuilder::backgroundColor maybeSetGlobalTo maybeReadColor()
            // don't know, what this 2 means ("howInvers" and "howAnim")
            1, 2 -> maybeReadInt()?.let {
                dduBuilder.restGlobals.add(it)
                nGlobals++
            }
            3 -> dduBuilder::drawTrace maybeSetGlobalTo maybeReadBoolean()
            4 -> dduBuilder::bestCenter maybeSetGlobalTo maybeReadComplex()
        }
        mode = Mode.NO
    }

    private fun tryAddCircle() {
        dduBuilder.tryBuildAndAddCircleFigure(circleBuilder)
        circleBuilder = CircleBuilder()
        mode = Mode.RADIUS
    }

    private fun readModernGlobalLine() {
        when {
            trimmedLine.startsWith("global") ->
                mode = Mode.GLOBAL
            trimmedLine.startsWith("drawTrace:") ->
                dduBuilder::drawTrace maybeSetTo maybeReadBoolean(trimmedSubstringAfter("drawTrace:"))
            trimmedLine.startsWith("bestCenter:") ->
                dduBuilder::bestCenter maybeSetTo maybeReadComplex(trimmedSubstringAfter("bestCenter:"))
            trimmedLine.startsWith("shape:") ->
                dduBuilder::shape maybeSetTo maybeReadShape(trimmedSubstringAfter("shape:"))
            trimmedLine.startsWith("showOutline:") ->
                Log.w(TAG, "Deprecated ddu parameter: showOutline")
        }
    }

    private fun readCircleLine() {
        @Suppress("NON_EXHAUSTIVE_WHEN")
        when (mode) {
            Mode.RADIUS -> circleBuilder::radius maybeSetTo maybeReadDouble()
            Mode.X -> circleBuilder::x maybeSetTo maybeReadDouble()
            Mode.Y -> circleBuilder::y maybeSetTo maybeReadDouble()
            Mode.COLOR -> circleBuilder::color maybeSetTo maybeReadColor()
            Mode.FILL -> circleBuilder::fill maybeSetTo maybeReadBoolean()
            Mode.RULE -> circleBuilder::rule maybeSetTo maybeReadRule()
        }
        if (mode >= Mode.RULE && trimmedLine.startsWith("borderColor:"))
            circleBuilder::borderColor maybeSetTo maybeReadColor(trimmedSubstringAfter("borderColor:"))
        mode = mode.next()
    }

    fun read(): DDU {
        reader.forEachLine { line ->
            trimmedLine = line.trim()
            if (trimmedLine.isNotBlank())
                when {
                    mode == Mode.GLOBAL -> readLegacyGlobalLine()
                    trimmedLine.startsWith("circle:") -> tryAddCircle()
                    mode == Mode.NO -> readModernGlobalLine()
                    mode >= Mode.RADIUS -> readCircleLine()
                }
        }
        tryAddCircle()
        return dduBuilder.build()
    }

    companion object {
        private const val TAG = "DDUReader"
    }
}


private class DDUWriter(private val ddu: DDU) {
    private lateinit var outputStream: OutputStream
    private val legacyGlobals: List<String> = listOf(
        ddu.backgroundColor.fromColor(),
        *ddu.restGlobals.toTypedArray()
    ).map { it.toString() }

    private fun writeLine(s: String) =
        outputStream.write("$s\n".toByteArray())

    private fun writeLegacyGlobal(global: String) {
        writeLine("global")
        writeLine(global)
    }

    private fun writeModernGlobals() {
        maybeWriteDrawTrace()
        maybeWriteBestCenter()
        writeShape()
    }

    private fun maybeWriteDrawTrace() {
        ddu.drawTrace?.let {
            writeLine("drawTrace: $it")
        }
    }

    private fun maybeWriteBestCenter() {
        ddu.bestCenter?.let {
            writeLine("bestCenter: ${it.real} ${it.imaginary}")
        }
    }

    private fun writeShape() =
        writeLine("shape: ${ddu.shape}")

    private fun writeCircle(circleFigure: CircleFigure) {
        writeLine("\ncircle:")
        with(circleFigure) {
            val fillInt = if (fill) 1 else 0
            listOf(radius, x, y, color.fromColor(), fillInt).forEach {
                writeLine(it.toString())
            }
            rule?.let { writeLine(it) }
            borderColor?.fromColor()?.let {
                writeLine("borderColor: $it")
            }
        }
    }

    private fun writeCircleForDodecaLook(circleFigure: CircleFigure) {
        writeLine("circle:")
        with(circleFigure) {
            val fillInt = if (fill) 1 else 0
            listOf(radius, x, y, color.fromColor(), fillInt).forEach {
                writeLine(it.toString())
            }
            writeLine(rule ?: "")
        }
    }

    fun write(output: OutputStream) {
        // maybe: use buffered stream
        outputStream = output
        output.use {
            writeLine(HEADER)
            legacyGlobals.forEach { writeLegacyGlobal(it) }
            writeModernGlobals()
            ddu.circles.forEach { writeCircle(it) }
        }
    }

    fun writeForDodecaLook(output: OutputStream) {
        // maybe: abstract DDUWriter + 2 impl-s
        outputStream = output
        output.use {
            writeLine(DODECA_LOOK_HEADER)
            legacyGlobals.forEach { writeLegacyGlobal(it) }
            ddu.circles.forEach { writeCircleForDodecaLook(it) }
        }
    }

    companion object {
        private const val HEADER: String = "Dodeca Meditation ${BuildConfig.VERSION_NAME} for Android"
        private const val DODECA_LOOK_HEADER: String = "DUDU C++v.1" // NOTE: important! without it DodecaLook fails
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
package com.pierbezuhoff.dodeca.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.core.graphics.withMatrix
import com.pierbezuhoff.dodeca.BuildConfig
import com.pierbezuhoff.dodeca.data.circlegroup.mkCircleGroup
import com.pierbezuhoff.dodeca.models.OptionsManager
import com.pierbezuhoff.dodeca.utils.asFF
import com.pierbezuhoff.dodeca.utils.mean
import com.pierbezuhoff.dodeca.utils.minus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.math3.complex.Complex
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.reflect.KMutableProperty0

// NOTE: when restGlobals = listOf(4, 4) some magic occurs (rule-less circles are moving in DodecaLook)
class Ddu(
    var backgroundColor: Int = DEFAULT_BACKGROUND_COLOR,
    var restGlobals: List<Int> = emptyList(), // unused
    var drawTrace: Boolean? = null,
    var bestCenter: Complex? = null, // cross-(screen size)
    var shape: Shape = DEFAULT_SHAPE,
    var circles: List<CircleFigure> = emptyList(),
    var file: File? = null
) {

    val autoCenter get() = circles.filter { it.show }.map { it.center }.mean()
    val complexity: Int get() = circles.sumOf { it.rule?.length ?: 0 }
    private fun getNSmartUpdates(nPreviewUpdates: Int): Int =
        (MIN_PREVIEW_UPDATES + nPreviewUpdates * 20 / sqrt(1.0 + complexity)).roundToInt()
    private fun getNUpdates(nPreviewUpdates: Int, previewSmartUpdates: Boolean): Int =
        if (previewSmartUpdates) getNSmartUpdates(nPreviewUpdates) else nPreviewUpdates
    // ^^^ for buildPreview

    // NOTE: copy(newRule = null) resolves overload ambiguity
    fun copy() =
        Ddu(
            backgroundColor, restGlobals.toList(), drawTrace, bestCenter, shape,
            circles.map { it.copy(newRule = null) }, file
        )

    override fun toString(): String = """Ddu(
        |  backgroundColor = ${backgroundColor.fromColor()}
        |  restGlobals = $restGlobals
        |  drawTrace = $drawTrace
        |  bestCenter = $bestCenter
        |  shape = $shape
        |  file = $file
        |  circles = $circles
        |)
    """.trimMargin()

    suspend fun saveToFile(file: File) = withContext(Dispatchers.IO) {
        if (!file.exists())
            file.createNewFile()
        saveToStream(file.outputStream())
    }

    suspend fun saveToStream(outputStream: OutputStream) =
        DduWriter(this).write(outputStream)

    suspend fun saveToStreamForDodecaLook(outputStream: OutputStream) =
        DduWriter(this).writeForDodecaLook(outputStream)

    // BUG: indefinite loading
    suspend fun buildPreview(width: Int, height: Int, values: OptionsManager.Values): Bitmap =
        withContext(Dispatchers.Default) {
            // used RGB_565 instead of ARGB_8888 for performance (visually indistinguishable)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val circleGroup = mkCircleGroup(values.circleGroupImplementation, values.projR, circles, paint)
            val center = Complex((width / 2).toDouble(), (height / 2).toDouble())
            val bestCenter = bestCenter ?: if (values.autocenterPreview) autoCenter else center
            val (dx, dy) = (center - bestCenter).asFF()
            val (centerX, centerY) = center.asFF()
            val scale: Float = PREVIEW_SCALE * width / NORMAL_PREVIEW_SIZE // or use options.preview_size.default
            val matrix = Matrix().apply { postTranslate(dx, dy); postScale(scale, scale, centerX, centerY) }
            canvas.withMatrix(matrix) {
                drawColor(backgroundColor)
                if (drawTrace ?: true) {
                    circleGroup.drawTimes(getNUpdates(values.nPreviewUpdates, values.previewSmartUpdates), canvas = canvas, shape = shape)
                } else {
                    circleGroup.draw(canvas, shape = shape)
                }
            }
            return@withContext bitmap
        }

    companion object {
        private const val TAG: String = "Ddu"
        const val DEFAULT_BACKGROUND_COLOR: Int = Color.WHITE
        val DEFAULT_SHAPE: Shape = Shape.CIRCLE
        private const val MIN_PREVIEW_UPDATES = 10
        private const val NORMAL_PREVIEW_SIZE = 300 // with this preview_size preview_scale was tuned
        private const val PREVIEW_SCALE = 0.5f

        suspend fun fromFile(file: File): Ddu = withContext(Dispatchers.IO) {
            fromStream(file.inputStream()).apply {
                this.file = file
            }
        }

        suspend fun fromStream(stream: InputStream): Ddu = withContext(Dispatchers.IO) {
            DduReader(stream.reader()).read()
        }

        fun createBlankPreview(previewSizePx: Int): Bitmap {
            val size = previewSizePx
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            return bitmap
        }

        val BLANK_DDU: Ddu = Ddu()
        val EXAMPLE_DDU: Ddu = run {
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
            Ddu(backgroundColor = Color.WHITE, circles = circles)
        }
    }
}

interface DduAttributesHolder {
    val ddu: Ddu
    var updating: Boolean
    var drawTrace: Boolean
    var shape: Shape
}

private class CircleBuilder {
    var radius: Double? = null
    var x: Double? = null
    var y: Double? = null
    var color: Int? = null
    var fill: Boolean? = null
    var rule: String? = null
    var borderColor: Int? = null

    class NotEnoughBuildParametersException(message: String = DEFAULT_MESSAGE) : Exception(message) {
        companion object {
            private const val DEFAULT_MESSAGE: String =
                "CircleBuilder must have [radius], [x] and [y] in order to build CircleFigure"
        }
    }

    @Throws(NotEnoughBuildParametersException::class)
    fun build(): CircleFigure {
        if (x == null || y == null || radius == null)
            throw NotEnoughBuildParametersException()
        return CircleFigure(x!!, y!!, radius!!, color, fill, rule, borderColor)
    }
}


private class DduBuilder {
    var backgroundColor: Int = Ddu.DEFAULT_BACKGROUND_COLOR
    val restGlobals: MutableList<Int> = mutableListOf()
    var drawTrace: Boolean? = null
    var bestCenter: Complex? = null
    var shape: Shape = Ddu.DEFAULT_SHAPE
    val circleFigures: MutableList<CircleFigure> = mutableListOf()

    fun build(): Ddu =
        Ddu(backgroundColor, restGlobals, drawTrace, bestCenter, shape, circleFigures)

    fun addCircleFigure(circleFigure: CircleFigure) {
        circleFigures.add(circleFigure)
    }
}


private class DduReader(private val reader: InputStreamReader) {
    private enum class Mode { // for scanning .ddu, before <mode parameter>
        NO, GLOBAL, RADIUS, X, Y, COLOR, FILL, RULE, CIRCLE_AUX;
        fun next(): Mode = values().elementAtOrElse(ordinal + 1) { CIRCLE_AUX }
    }

    private val dduBuilder = DduBuilder()
    private var mode: Mode = Mode.NO // mode == FILL === before scanning [fill] parameter
    private var nGlobals: Int = 0
    private lateinit var circleBuilder: CircleBuilder
    private lateinit var trimmedLine: String
    private var nOfLine: Long = 0

    suspend fun read(): Ddu =
        withContext(Dispatchers.IO) {
            reader.forEachLine { line ->
                nOfLine++
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
            dduBuilder.build()
        }

    private fun readLegacyGlobalLine() {
        when (nGlobals) {
            0 -> dduBuilder::backgroundColor maybeSetGlobalTo maybeReadColor()
            // don't know, what these 2 mean ("howInvers" and "howAnim")
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
        if (this::circleBuilder.isInitialized) {
            try {
                val circleFigure = circleBuilder.build()
                dduBuilder.addCircleFigure(circleFigure)
            } catch (e: CircleBuilder.NotEnoughBuildParametersException) {
                e.printStackTrace()
                Log.w(TAG, "Error in DduReader.tryAddCircle occurred while reading line $nOfLine")
            }
        }
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
        when (mode) {
            Mode.RADIUS -> circleBuilder::radius maybeSetTo maybeReadDouble()
            Mode.X -> circleBuilder::x maybeSetTo maybeReadDouble()
            Mode.Y -> circleBuilder::y maybeSetTo maybeReadDouble()
            Mode.COLOR -> circleBuilder::color maybeSetTo maybeReadColor()
            Mode.FILL -> circleBuilder::fill maybeSetTo maybeReadBoolean()
            Mode.RULE -> circleBuilder::rule maybeSetTo maybeReadRule()
            else -> {}
        }
        if (mode >= Mode.RULE && trimmedLine.startsWith("borderColor:"))
            circleBuilder::borderColor maybeSetTo maybeReadColor(trimmedSubstringAfter("borderColor:"))
        mode = mode.next()
    }

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

    private fun maybeReadShape(s: String = trimmedLine): Shape? =
        Shape.valueOfOrNull(s)

    private fun maybeReadRule(s: String = trimmedLine): String? =
        if (Regex("n?\\d+").matches(s)) s else null

    private fun trimmedSubstringAfter(prefix: String): String =
        trimmedLine.substringAfter(prefix).trim()

    companion object {
        private const val TAG = "DduReader"
    }
}


private class DduWriter(private val ddu: Ddu) {
    private lateinit var outputStream: OutputStream
    private val legacyGlobals: List<String> = listOf(
        ddu.backgroundColor.fromColor(),
        *ddu.restGlobals.toTypedArray()
    ).map { it.toString() }

    suspend fun write(output: OutputStream) {
        withContext(Dispatchers.IO) {
            outputStream = output
            output.use {
                writeLine(HEADER)
                legacyGlobals.forEach { writeLegacyGlobal(it) }
                writeModernGlobals()
                ddu.circles.forEach { writeCircle(it) }
            }
        }
    }

    suspend fun writeForDodecaLook(output: OutputStream) {
        // MAYBE: abstract DduWriter + 2 impl-s
        withContext(Dispatchers.IO) {
            outputStream = output
            output.use {
                writeLine(DODECA_LOOK_HEADER)
                legacyGlobals.forEach { writeLegacyGlobal(it) }
                ddu.circles.forEach { writeCircleForDodecaLook(it) }
            }
        }
    }

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

    companion object {
        private const val HEADER: String = "Dodeca Meditation ${BuildConfig.VERSION_NAME} for Android"
        private const val DODECA_LOOK_HEADER: String = "DUDU C++v.1" // NOTE: important! without it DodecaLook fails
    }
}

interface DduOptionsChangeListener {
    fun onAutocenterAlways(autocenterAlways: Boolean)
    fun onCanvasFactor(canvasFactor: Int)
    fun onSpeed(speed: Float)
}

/* In C++ (with it ddu was created) color is RRGGBB, but in Java -- AABBGGRR
* see Bitmap.Config.ARGB_8888 (https://developer.android.com/reference/android/graphics/Bitmap.Config.html#ARGB_8888) */
@get:IntRange(from = 0, to = 255)
private val Int.red: Int get() = (this and 0xff0000) shr 16
@get:IntRange(from = 0, to = 255)
private val Int.green: Int get() = (this and 0x00ff00) shr 8
@get:IntRange(from = 0, to = 255)
private val Int.blue: Int get() = this and 0x0000ff

/* RRGGBB -> AABBGGRR */
@ColorInt internal fun Int.toColor(): Int = Color.rgb(blue, green, red)
// = ((blue shl 16) + (green shl 8) + red).inv() xor 0xffffff

/* AABBGGRR -> RRGGBB */
internal fun @receiver:ColorInt Int.fromColor(): Int =
    (Color.blue(this) shl 16) + (Color.green(this) shl 8) + Color.red(this)


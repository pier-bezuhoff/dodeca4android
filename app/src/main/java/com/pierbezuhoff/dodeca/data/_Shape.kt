package com.pierbezuhoff.dodeca.data

import androidx.databinding.InverseMethod

enum class _Shape {
    CIRCLE, SQUARE, CROSS, VERTICAL_BAR, HORIZONTAL_BAR;
    companion object {
        fun valueOfOrNull(s: String): _Shape? =
            try { valueOf(s) } catch (e: IllegalArgumentException) { null }

        fun fromOrdinal(ordinal: Int): _Shape =
            values()[ordinal]

        @InverseMethod("fromOrdinal")
        fun toOrdinal(shape: _Shape): Int =
            shape.ordinal
    }
}
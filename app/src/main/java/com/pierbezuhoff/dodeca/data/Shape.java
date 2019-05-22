package com.pierbezuhoff.dodeca.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.InverseMethod;

// NOTE: used java because databindings cannot find "static" kotlin method Shape.toOrdinal from toolbar1.xml/shape_spinner
public enum Shape {
    CIRCLE, SQUARE, CROSS, VERTICAL_BAR, HORIZONTAL_BAR;

    @Nullable
    public static Shape valueOfOrNull(String s) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @InverseMethod("fromOrdinal")
    static public int toOrdinal(@NonNull Shape shape) {
        return shape.ordinal();
    }

    @NonNull
    static public Shape fromOrdinal(int ordinal) {
        return Shape.values()[ordinal];
    }
}

package uikit.extensions

import android.graphics.Rect

inline var Rect.horizontal: Int
    get() = left
    set(value) {
        left = value
        right = value
    }


inline var Rect.vertical: Int
    get() = top
    set(value) {
        top = value
        bottom = value
    }

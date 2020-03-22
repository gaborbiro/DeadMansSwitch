package app.gaborbiro.deadmansswitch

import android.graphics.Rect
import android.view.View

fun View.contains(rx: Float, ry: Float): Boolean {
    return Rect().let {
        getDrawingRect(it)
        it.contains(rx.toInt(), ry.toInt())
    }
}

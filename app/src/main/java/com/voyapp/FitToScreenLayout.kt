package com.voyapp

import android.content.Context
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import kotlin.math.min

/**
 * Contenedor de un solo hijo que garantiza que se vea entero sin scroll:
 *  - si cabe, lo estira a todo el alto disponible (los huecos con peso se reparten);
 *  - si no cabe (letra grande, movil pequeno), lo reduce de escala hasta que quepa.
 */
class FitToScreenLayout(context: Context) : FrameLayout(context) {

    private var scale = 1f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val child = getChildAt(0) ?: return

        val availW = w - paddingLeft - paddingRight
        val availH = h - paddingTop - paddingBottom
        if (availW <= 0 || availH <= 0) return

        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        child.measure(MeasureSpec.makeMeasureSpec(availW, MeasureSpec.EXACTLY), unspecified)
        var natural = child.measuredHeight

        if (natural <= availH) {
            scale = 1f
        } else {
            // Al encoger, el contenido gana anchura relativa y suele necesitar menos alto:
            // se recalcula un par de veces para afinar la escala.
            var s = availH.toFloat() / natural
            repeat(2) {
                child.measure(MeasureSpec.makeMeasureSpec((availW / s).toInt(), MeasureSpec.EXACTLY), unspecified)
                natural = child.measuredHeight
                s = min(1f, availH.toFloat() / natural)
            }
            scale = s
        }
        child.measure(
            MeasureSpec.makeMeasureSpec((availW / scale).toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((availH / scale).toInt(), MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        child.layout(paddingLeft, paddingTop, paddingLeft + child.measuredWidth, paddingTop + child.measuredHeight)
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = scale
        child.scaleY = scale
    }
}

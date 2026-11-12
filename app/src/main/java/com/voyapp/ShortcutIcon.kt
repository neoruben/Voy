package com.voyapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Dibuja el icono de un acceso directo: el icono de la app con la foto del contacto
 * (o sus iniciales) en pequeno en la esquina inferior derecha.
 * Se genera como icono adaptativo de 108x108 "dp" para que el launcher le aplique su forma.
 */
object ShortcutIcon {

    private const val SIZE = 432               // 108 dp * 4
    private val AVATAR_COLORS = intArrayOf(
        0xFF5C6BC0.toInt(), 0xFFEF6C00.toInt(), 0xFF8E24AA.toInt(), 0xFF00897B.toInt(),
        0xFFD81B60.toInt(), 0xFF3949AB.toInt(), 0xFF6D4C41.toInt(), 0xFF546E7A.toInt(),
    )

    fun build(ctx: Context, name: String, photo: Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        for (res in intArrayOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            ContextCompat.getDrawable(ctx, res)?.apply { setBounds(0, 0, SIZE, SIZE); draw(c) }
        }

        // Insignia dentro de la "zona segura" (circulo central de 66 dp) para que no se recorte
        val u = SIZE / 108f
        val cx = 68.5f * u
        val cy = 68.5f * u
        val r = 13.5f * u

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        c.drawCircle(cx, cy, r + 1.8f * u, paint)

        if (photo != null) {
            val scale = (2 * r) / minOf(photo.width, photo.height)
            val m = Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - photo.width * scale / 2, cy - photo.height * scale / 2)
            }
            paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            c.drawCircle(cx, cy, r, paint)
            paint.shader = null
        } else {
            paint.color = AVATAR_COLORS[abs(name.hashCode()) % AVATAR_COLORS.size]
            c.drawCircle(cx, cy, r, paint)
            val initials = initialsOf(name)
            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = r * if (initials.length > 1) 0.85f else 1.05f
            val fm = paint.fontMetrics
            c.drawText(initials, cx, cy - (fm.ascent + fm.descent) / 2, paint)
        }
        return bmp
    }

    /**
     * Icono para el widget: icono de la app con forma de squircle y la foto del contacto
     * en la esquina inferior derecha, justo donde el launcher pondria su insignia.
     */
    fun buildWidget(ctx: Context, name: String, photo: Bitmap?): Bitmap {
        val s = 256
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Icono principal: la parte visible de un icono adaptativo es el centro (72 de 108)
        val icon = 208f
        val full = (icon * 108f / 72f).toInt()
        val off = ((icon - full) / 2f).toInt()
        c.save()
        c.clipPath(systemIconShape(ctx, icon))
        for (res in intArrayOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground)) {
            ContextCompat.getDrawable(ctx, res)?.apply { setBounds(off, off, off + full, off + full); draw(c) }
        }
        c.restore()

        // Insignia con la persona
        drawAvatar(c, name, photo, cx = 202f, cy = 202f, r = 46f, ring = 5f)
        return bmp
    }

    /**
     * Forma de los iconos del sistema (circulo, squircle...) escalada a [size].
     * Asi el widget sigue la misma forma que el resto de iconos del launcher.
     */
    private fun systemIconShape(ctx: Context, size: Float): Path {
        val mask = (ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher) as? AdaptiveIconDrawable)?.iconMask
        if (mask != null && !mask.isEmpty) {
            val b = RectF()
            @Suppress("DEPRECATION")
            mask.computeBounds(b, true)
            if (b.width() > 0 && b.height() > 0) {
                return Path(mask).apply {
                    transform(Matrix().apply {
                        setRectToRect(b, RectF(0f, 0f, size, size), Matrix.ScaleToFit.FILL)
                    })
                }
            }
        }
        return Path().apply { addCircle(size / 2, size / 2, size / 2, Path.Direction.CW) }
    }

    private fun drawAvatar(c: Canvas, name: String, photo: Bitmap?, cx: Float, cy: Float, r: Float, ring: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        c.drawCircle(cx, cy, r + ring, paint)
        if (photo != null) {
            val scale = (2 * r) / minOf(photo.width, photo.height)
            val m = Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - photo.width * scale / 2, cy - photo.height * scale / 2)
            }
            paint.shader = BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            c.drawCircle(cx, cy, r, paint)
            paint.shader = null
        } else {
            paint.color = AVATAR_COLORS[abs(name.hashCode()) % AVATAR_COLORS.size]
            c.drawCircle(cx, cy, r, paint)
            val initials = initialsOf(name)
            paint.color = Color.WHITE
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = r * if (initials.length > 1) 0.85f else 1.05f
            val fm = paint.fontMetrics
            c.drawText(initials, cx, cy - (fm.ascent + fm.descent) / 2, paint)
        }
    }

    /**
     * Nombre del widget dibujado como imagen. El ImageView que la muestra usa fitCenter,
     * asi que si el texto es mas ancho que el widget se reduce entero en vez de recortarse.
     */
    fun buildLabel(ctx: Context, text: String): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val h = (19 * d).toInt()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14 * d
            setShadowLayer(2f * d, 0f, 0.5f * d, 0xAA000000.toInt())
        }
        // Tope para nombres larguisimos: a partir de ahi, puntos suspensivos
        val shown = TextUtils.ellipsize(text, paint, 150 * d, TextUtils.TruncateAt.END).toString()
        val pad = 3 * d
        val w = (paint.measureText(shown) + 2 * pad).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val fm = paint.fontMetrics
        Canvas(bmp).drawText(shown, pad, h / 2f - (fm.ascent + fm.descent) / 2, paint)
        return bmp
    }

    private fun initialsOf(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.firstOrNull()?.isLetter() == true }
        return when {
            words.isEmpty() -> "#"
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    /** Lee una foto a partir de su URI de Contactos. Devuelve null si no hay o no hay permiso. */
    fun loadPhoto(ctx: Context, uri: String?): Bitmap? {
        if (uri.isNullOrBlank()) return null
        return try {
            ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Busca la foto del contacto por su numero (necesita permiso de contactos). */
    fun findPhotoUriByNumber(ctx: Context, number: String): String? = try {
        val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        ctx.contentResolver.query(lookup, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) {
        null
    }
}

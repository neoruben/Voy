package com.voyapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.RemoteViews
import java.io.File
import java.io.FileOutputStream

/**
 * Widget de 1x1 que parece un icono: el icono de Voy con la foto del contacto en la esquina.
 * A diferencia de un acceso directo, el launcher no le anade su insignia.
 */
class ContactWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_PINNED = "com.voyapp.WIDGET_PINNED"
        private const val EXTRA_ICON = "icon_path"

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        private fun iconFile(ctx: Context, id: Int) = File(ctx.filesDir, "widget_$id.png")

        /** Pide al launcher que anada el widget. Devuelve false si el launcher no lo permite. */
        fun requestPin(ctx: Context, phone: String, name: String, duration: Int, icon: Bitmap): Boolean {
            val awm = AppWidgetManager.getInstance(ctx)
            if (!awm.isRequestPinAppWidgetSupported) return false

            // El icono se guarda ya; cuando el launcher confirme, se asocia al id del widget
            val token = System.currentTimeMillis()
            val tmp = File(ctx.filesDir, "pending_$token.png")
            FileOutputStream(tmp).use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val callback = Intent(ctx, ContactWidget::class.java)
                .setAction(ACTION_PINNED)
                .putExtra(ShareActivity.EXTRA_PHONE, phone)
                .putExtra(ShareActivity.EXTRA_NAME, name)
                .putExtra(ShareActivity.EXTRA_DURATION, duration)
                .putExtra(EXTRA_ICON, tmp.absolutePath)
            // MUTABLE: el sistema tiene que poder anadir el id del widget nuevo
            val pi = PendingIntent.getBroadcast(
                ctx, (token % Int.MAX_VALUE).toInt(), callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            return awm.requestPinAppWidget(ComponentName(ctx, ContactWidget::class.java), null, pi)
        }

        /** Redibuja todos los widgets ya colocados (p. ej. tras actualizar la app). */
        fun updateAll(ctx: Context) {
            val awm = AppWidgetManager.getInstance(ctx)
            awm.getAppWidgetIds(ComponentName(ctx, ContactWidget::class.java)).forEach { update(ctx, awm, it) }
        }

        fun update(ctx: Context, awm: AppWidgetManager, id: Int) {
            val p = prefs(ctx)
            val phone = p.getString("${id}_phone", null)
            val views = RemoteViews(ctx.packageName, R.layout.widget_contact)

            val click: PendingIntent
            if (phone == null) {
                // Widget anadido desde el selector de widgets, sin contacto: abre la app
                views.setImageViewResource(R.id.icon, R.mipmap.ic_launcher)
                views.setImageViewBitmap(R.id.label, ShortcutIcon.buildLabel(ctx, ctx.getString(R.string.app_name)))
                click = PendingIntent.getActivity(
                    ctx, id, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                val name = p.getString("${id}_name", phone).orEmpty()
                val duration = p.getInt("${id}_duration", 1)
                val bmp = BitmapFactory.decodeFile(iconFile(ctx, id).path)
                if (bmp != null) views.setImageViewBitmap(R.id.icon, bmp)
                else views.setImageViewResource(R.id.icon, R.mipmap.ic_launcher)
                val label = "${ctx.getString(R.string.app_name)} $name"   // p. ej. "Voy Ana"
                views.setImageViewBitmap(R.id.label, ShortcutIcon.buildLabel(ctx, label))
                Log.i(LiveLocationService.TAG, "Widget $id actualizado: '$label'")
                views.setContentDescription(R.id.root, label)
                val intent = ShareActivity.intentFor(ctx, phone, name, duration)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                click = PendingIntent.getActivity(
                    ctx, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            views.setOnClickPendingIntent(R.id.root, click)
            awm.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PINNED) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val tmp = intent.getStringExtra(EXTRA_ICON)?.let { File(it) }
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                prefs(context).edit()
                    .putString("${id}_phone", intent.getStringExtra(ShareActivity.EXTRA_PHONE))
                    .putString("${id}_name", intent.getStringExtra(ShareActivity.EXTRA_NAME))
                    .putInt("${id}_duration", intent.getIntExtra(ShareActivity.EXTRA_DURATION, 1))
                    .commit()
                tmp?.let { if (it.exists()) it.renameTo(iconFile(context, id)) }
                update(context, AppWidgetManager.getInstance(context), id)
            } else {
                tmp?.delete()
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        update(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val e = prefs(context).edit()
        appWidgetIds.forEach { id ->
            e.remove("${id}_phone").remove("${id}_name").remove("${id}_duration")
            iconFile(context, id).delete()
        }
        e.apply()
    }
}

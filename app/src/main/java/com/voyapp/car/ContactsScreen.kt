package com.voyapp.car

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.voyapp.LiveLocationService
import com.voyapp.R
import com.voyapp.ShareActivity
import com.voyapp.ShareJob
import java.io.File

/** Un botón por cada icono de Voy que haya en la pantalla de inicio (widgets y accesos directos). */
class ContactsScreen(ctx: CarContext) : Screen(ctx) {

    private data class Target(val phone: String, val name: String, val duration: Int, val icon: Bitmap?)

    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        val targets = loadTargets()
        if (targets.isEmpty()) items.setNoItemsMessage(carContext.getString(R.string.car_no_contacts))
        targets.forEach { t ->
            val image = t.icon?.let { CarIcon.Builder(IconCompat.createWithBitmap(it)).build() } ?: CarIcon.APP_ICON
            items.addItem(
                GridItem.Builder()
                    .setTitle(t.name)
                    .setImage(image, GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener { share(t) }
                    .build()
            )
        }
        @Suppress("DEPRECATION")
        return GridTemplate.Builder()
            .setTitle(carContext.getString(R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .build()
    }

    private fun share(t: Target) {
        val service = LiveLocationService.instance
        if (service == null) {
            toast(carContext.getString(R.string.share_enable_service))
            return
        }
        service.startJob(ShareJob(t.phone, t.name, t.duration))
        if (ShareActivity.openChat(service, t.phone)) toast(carContext.getString(R.string.svc_sharing, t.name))
        else toast(carContext.getString(R.string.share_no_whatsapp))
    }

    private fun toast(msg: String) = CarToast.makeText(carContext, msg, CarToast.LENGTH_LONG).show()

    /** Contactos de los widgets colocados y de los accesos directos anclados (sin repetir). */
    private fun loadTargets(): List<Target> {
        val out = linkedMapOf<String, Target>()
        val awm = android.appwidget.AppWidgetManager.getInstance(carContext)
        val prefs = carContext.getSharedPreferences("widgets", android.content.Context.MODE_PRIVATE)
        awm.getAppWidgetIds(android.content.ComponentName(carContext, com.voyapp.ContactWidget::class.java)).forEach { id ->
            val phone = prefs.getString("${id}_phone", null) ?: return@forEach
            val name = prefs.getString("${id}_name", null).orEmpty().ifBlank { phone }
            val duration = prefs.getInt("${id}_duration", 1)
            val icon = BitmapFactory.decodeFile(File(carContext.filesDir, "widget_$id.png").path)
            out.putIfAbsent("$phone/$duration", Target(phone, name, duration, icon))
        }
        runCatching {
            ShortcutManagerCompat.getShortcuts(carContext, ShortcutManagerCompat.FLAG_MATCH_PINNED).forEach { s ->
                val i = s.intent
                val phone = i.getStringExtra(ShareActivity.EXTRA_PHONE) ?: return@forEach
                val name = i.getStringExtra(ShareActivity.EXTRA_NAME).orEmpty().ifBlank { phone }
                val duration = i.getIntExtra(ShareActivity.EXTRA_DURATION, 1)
                out.putIfAbsent("$phone/$duration", Target(phone, name, duration, null))
            }
        }
        return out.values.toList()
    }
}

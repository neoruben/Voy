package com.voyapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/**
 * Actividad invisible que abre el icono de la pantalla de inicio:
 * encarga el trabajo al servicio y abre el chat del contacto en WhatsApp.
 */
class ShareActivity : Activity() {

    companion object {
        const val EXTRA_PHONE = "phone"
        const val EXTRA_NAME = "name"
        const val EXTRA_DURATION = "duration"

        fun intentFor(ctx: android.content.Context, phone: String, name: String, duration: Int) =
            Intent(ctx, ShareActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_PHONE, phone)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_DURATION, duration)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { phone }
        val duration = intent.getIntExtra(EXTRA_DURATION, 1)

        val service = LiveLocationService.instance
        when {
            phone.isBlank() -> toast(getString(R.string.share_no_number))

            service == null -> {
                toast(getString(R.string.share_enable_service))
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }

            else -> {
                service.startJob(ShareJob(phone, name, duration))
                if (!openChat(phone)) toast(getString(R.string.share_no_whatsapp))
            }
        }
        finish()
    }

    private fun openChat(phone: String): Boolean {
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone")
        for (pkg in LiveLocationService.WA_PACKAGES) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        return false
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

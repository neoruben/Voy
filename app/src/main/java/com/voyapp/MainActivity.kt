package com.voyapp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Pantalla de configuracion: contacto, duracion, activar el servicio y crear el icono. */
class MainActivity : Activity() {

    // Paleta "tipo chat": verdes y fondo beige claro
    private val DARK = Color.parseColor("#075E54")
    private val GREEN = Color.parseColor("#00A884")
    private val BG = Color.parseColor("#EFEAE2")
    private val TEXT = Color.parseColor("#111B21")
    private val MUTED = Color.parseColor("#667781")
    private val RED = Color.parseColor("#C62828")

    private lateinit var nameField: EditText
    private lateinit var phoneField: EditText
    private lateinit var durationGroup: RadioGroup
    private lateinit var status: TextView
    private lateinit var enableButton: Button

    private val prefs by lazy { getSharedPreferences("config", MODE_PRIVATE) }
    private val durationLabels by lazy { listOf(getString(R.string.dur_15), getString(R.string.dur_1h), getString(R.string.dur_8h)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }

        // ---------- Cabecera verde ----------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(DARK)
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = getString(R.string.app_name)
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = getString(R.string.subtitle)
                textSize = 13f
                setTextColor(Color.argb(210, 255, 255, 255))
            })
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        // ---------- Contenido (sin scroll: se encoge si no cabe) ----------
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        // Tarjeta 1: servicio
        val c1 = card(col, "1", getString(R.string.step1_title))
        status = TextView(this).apply { textSize = 14f }
        c1.addView(status)
        enableButton = outlineButton(getString(R.string.open_a11y)) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        c1.addView(enableButton, buttonParams(top = 8, height = 46))

        // Tarjeta 2: contacto
        val c2 = card(col, "2", getString(R.string.step2_title))
        c2.addView(filledButton(getString(R.string.pick_contact)) { pickContact() }, buttonParams(top = 2, height = 46))
        c2.addView(TextView(this).apply {
            text = getString(R.string.or_type)
            setTextColor(MUTED)
            textSize = 12f
            setPadding(dp(2), dp(8), 0, 0)
        })
        nameField = field(getString(R.string.hint_name), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        nameField.setText(prefs.getString("name", ""))
        c2.addView(nameField)
        phoneField = field(getString(R.string.hint_phone), InputType.TYPE_CLASS_PHONE)
        phoneField.setText(prefs.getString("phone", ""))
        c2.addView(phoneField)

        // Tarjeta 3: duracion
        val c3 = card(col, "3", getString(R.string.step3_title))
        durationGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        durationLabels.forEachIndexed { i, label ->
            durationGroup.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                tag = i
                textSize = 15f
                setTextColor(TEXT)
                buttonTintList = ColorStateList.valueOf(GREEN)
            }, RadioGroup.LayoutParams(0, -2, 1f))
        }
        (durationGroup.getChildAt(prefs.getInt("duration", 1)) as RadioButton).isChecked = true
        c3.addView(durationGroup)

        // Hueco flexible: empuja los botones al fondo de la pantalla
        col.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        // Acciones, en una sola fila
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(outlineButton(getString(R.string.try_now)) {
            readForm()?.let { (p, n, d) -> startActivity(ShareActivity.intentFor(this, p, n, d)) }
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(10) })
        actions.addView(filledButton(getString(R.string.create_icon)) { createShortcut() },
            LinearLayout.LayoutParams(0, dp(54), 1.4f))
        col.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { null } ?: "1.0.0"
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        footer.addView(TextView(this).apply {
            text = getString(R.string.credits, version) + "  ·  "
            setTextColor(MUTED)
            textSize = 12f
        })
        footer.addView(TextView(this).apply {
            text = getString(R.string.kofi)
            setTextColor(DARK)
            textSize = 12f
            paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ko-fi.com/neoruben")))
                } catch (_: Exception) {
                }
            }
        })
        col.addView(footer, LinearLayout.LayoutParams(-1, -2))

        val fit = FitToScreenLayout(this).apply { addView(col) }
        root.addView(fit, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        // Cabecera detras de la barra de estado; contenido por encima de la barra de navegacion
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(dp(16), bars.top + dp(10), dp(16), dp(12))
            fit.setPadding(0, 0, 0, bars.bottom)
            insets
        }
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = false
    }

    override fun onResume() {
        super.onResume()
        ContactWidget.updateAll(this)
        val on = LiveLocationService.instance != null
        status.text = getString(if (on) R.string.status_on else R.string.status_off)
        status.setTextColor(if (on) DARK else RED)
        enableButton.visibility = if (on) View.GONE else View.VISIBLE
    }

    /** Devuelve (telefono normalizado, nombre, duracion) o null si falta algo. */
    private fun readForm(): Triple<String, String, Int>? {
        val digits = normalizePhone(phoneField.text.toString())
        if (digits == null) {
            toast(getString(R.string.err_phone))
            return null
        }
        val name = nameField.text.toString().trim().ifBlank { "+$digits" }
        val checked = durationGroup.findViewById<RadioButton>(durationGroup.checkedRadioButtonId)
        val duration = (checked?.tag as? Int) ?: 1
        prefs.edit()
            .putString("name", nameField.text.toString().trim())
            .putString("phone", phoneField.text.toString().trim())
            .putInt("duration", duration)
            .apply()
        return Triple(digits, name, duration)
    }

    // ---------- Selector de contactos (no necesita permiso de contactos) ----------

    private val REQ_CONTACT = 42

    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        try {
            startActivityForResult(intent, REQ_CONTACT)
        } catch (_: Exception) {
            toast(getString(R.string.err_no_contacts))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CONTACT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        contentResolver.query(uri, cols, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                nameField.setText(c.getString(0).orEmpty())
                phoneField.setText(c.getString(1).orEmpty())
                pickedPhone = phoneField.text.toString()
                pickedPhotoUri = c.getString(2)
            }
        }
    }

    // Foto del ultimo contacto elegido (solo vale si no se ha cambiado el telefono a mano)
    private var pickedPhone: String? = null
    private var pickedPhotoUri: String? = null
    private val REQ_PERM_CONTACTS = 43
    private var askedContactsPermission = false

    /** Foto del contacto para la insignia, o null (se usaran sus iniciales). */
    private fun contactPhoto(phone: String): Bitmap? {
        val uri = pickedPhotoUri.takeIf { pickedPhone == phoneField.text.toString() }
            ?: if (hasContactsPermission()) ShortcutIcon.findPhotoUriByNumber(this, "+$phone") else null
        return ShortcutIcon.loadPhoto(this, uri)
    }

    private fun hasContactsPermission() =
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERM_CONTACTS) createShortcut()
    }

    /**
     * Pasa el numero a formato internacional sin "+" (p. ej. 34600123456).
     * Si no lleva prefijo, usa el pais de la SIM (o del idioma del movil).
     */
    private fun normalizePhone(raw: String): String? {
        val t = raw.trim()
        var digits = t.filter { it.isDigit() }
        if (t.startsWith("+")) return digits.takeIf { it.length >= 8 }
        if (digits.startsWith("00")) return digits.drop(2).takeIf { it.length >= 8 }

        val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
        val country = (tm?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: tm?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: resources.configuration.locales[0].country).lowercase()
        val codes = mapOf(
            "es" to "34", "it" to "39", "fr" to "33", "pt" to "351", "gb" to "44",
            "ie" to "353", "de" to "49", "be" to "32", "ch" to "41", "nl" to "31",
            "us" to "1", "ca" to "1", "mx" to "52", "ar" to "54",
        )
        val code = codes[country] ?: "34"
        // Paises con "0" de marcacion nacional delante (Francia, Reino Unido...): se quita
        if (country !in setOf("es", "it", "us", "ca") && digits.startsWith("0")) digits = digits.drop(1)
        if (digits.length < 6) return null
        return code + digits
    }

    private fun createShortcut() {
        val (phone, name, duration) = readForm() ?: return
        val photo = contactPhoto(phone)
        // Sin foto y sin permiso: lo pedimos una vez (para leer la foto); si dice que no, iniciales
        if (photo == null && !hasContactsPermission() && !askedContactsPermission) {
            askedContactsPermission = true
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQ_PERM_CONTACTS)
            return
        }
        // Preferimos un widget 1x1: el launcher no le pone su insignia encima de la foto
        if (ContactWidget.requestPin(this, phone, name, duration, ShortcutIcon.buildWidget(this, name, photo))) {
            return
        }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            toast(getString(R.string.err_no_pin))
            return
        }
        val icon = ShortcutIcon.build(this, name, photo)
        val info = ShortcutInfoCompat.Builder(this, "live_${phone}_${duration}_${System.currentTimeMillis()}")
            .setShortLabel(name)
            .setLongLabel(getString(R.string.shortcut_long, name, durationLabels[duration]))
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon))
            .setIntent(ShareActivity.intentFor(this, phone, name, duration))
            .build()
        val ok = ShortcutManagerCompat.requestPinShortcut(this, info, null)
        Log.i(LiveLocationService.TAG, "requestPinShortcut -> $ok")
        if (!ok) toast(getString(R.string.err_no_pin))
    }

    // ---------- Piezas de interfaz ----------

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(2), it) }
    }

    /** Tarjeta blanca con titulo numerado; devuelve el contenedor donde anadir cosas. */
    private fun card(parent: LinearLayout, number: String, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 14)
            elevation = dp(1).toFloat()
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = number
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = rounded(GREEN, 12)
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })
        row.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(TEXT)
            setTypeface(typeface, Typeface.BOLD)
        })
        c.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        parent.addView(c, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return c
    }

    private fun field(hintText: String, type: Int) = EditText(this).apply {
        hint = hintText
        inputType = type
        setTextColor(TEXT)
        setHintTextColor(MUTED)
        backgroundTintList = ColorStateList.valueOf(GREEN)
    }

    private fun filledButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        maxLines = 2
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(GREEN, 26)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun outlineButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        maxLines = 2
        setTextColor(DARK)
        background = rounded(Color.WHITE, 26, strokeColor = GREEN)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun buttonParams(top: Int, height: Int = 52) =
        LinearLayout.LayoutParams(-1, dp(height)).apply { topMargin = dp(top) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}

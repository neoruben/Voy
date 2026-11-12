package com.voyapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/** Lo que hay que hacer: compartir con [phone] durante [duration] (0 = 15 min, 1 = 1 h, 2 = 8 h). */
data class ShareJob(val phone: String, val name: String, val duration: Int)

/**
 * Servicio de accesibilidad que, cuando se le encarga un [ShareJob], pulsa en WhatsApp:
 *   Adjuntar -> Ubicacion -> Compartir ubicacion en tiempo real -> duracion -> Enviar
 * y vuelve a la pantalla de inicio. No hace nada si no tiene un encargo activo.
 */
class LiveLocationService : AccessibilityService() {

    companion object {
        const val TAG = "Voy"
        val WA_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

        /** Instancia viva del servicio (null si no esta activado en Accesibilidad). */
        var instance: LiveLocationService? = null
            private set

        private const val TIMEOUT_MS = 25_000L
        private const val MIN_GAP_MS = 700L      // pausa minima entre dos pulsaciones
        private const val POLL_MS = 250L

        // Textos que buscamos en WhatsApp (espanol, ingles, italiano y frances)
        private val ATTACH_DESC = listOf("Adjuntar", "Attach", "Allega", "Joindre", "Pièce jointe")
        private val LOCATION_TEXT = listOf("Ubicación", "Location", "Posizione", "Localisation", "Position")
        private val LIVE_TEXT = listOf("ubicación en tiempo real", "live location", "posizione in tempo reale", "position en direct")
        private val CONTINUE_TEXT = listOf("Continuar", "Continue", "Continua", "Continuer")
        private val OK_TEXT = listOf("Aceptar", "OK", "Accetta", "Accepter")
        private val SEND_DESC = listOf("Enviar", "Send", "Invia", "Envoyer")
        private val DURATION_TEXT = listOf(
            listOf("15 minutos", "15 minutes", "15 minuti", "15 min"),
            listOf("1 hora", "1 hour", "1 ora", "1 heure"),
            listOf("8 horas", "8 hours", "8 ore", "8 heures"),
        )
    }

    private enum class Step { ATTACH, LOCATION, LIVE, SEND }

    private val handler = Handler(Looper.getMainLooper())
    private var job: ShareJob? = null
    private var step = Step.ATTACH
    private var startedAt = 0L
    private var stepAt = 0L
    private var lastActionAt = 0L
    private var durationPicked = false
    private var dumpedThisStep = false

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "Servicio conectado")
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // El trabajo lo hace el bucle de sondeo; los eventos no hacen falta.
    }

    /** Llamado desde [ShareActivity] justo antes de abrir el chat. */
    fun startJob(newJob: ShareJob) {
        Log.i(TAG, "Nuevo encargo: $newJob")
        job = newJob
        val now = SystemClock.uptimeMillis()
        startedAt = now
        lastActionAt = now          // da tiempo a que WhatsApp abra el chat
        setStep(Step.ATTACH)
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 600)
    }

    private val tick = object : Runnable {
        override fun run() {
            val j = job ?: return
            if (SystemClock.uptimeMillis() - startedAt > TIMEOUT_MS) {
                fail("tiempo agotado en el paso $step")
                return
            }
            try {
                advance(j)
            } catch (e: Exception) {
                Log.e(TAG, "Error en paso $step", e)
            }
            if (job != null) handler.postDelayed(this, POLL_MS)
        }
    }

    private fun setStep(s: Step) {
        step = s
        stepAt = SystemClock.uptimeMillis()
        dumpedThisStep = false
        if (s == Step.ATTACH) durationPicked = false
        Log.i(TAG, "-> paso $s")
    }

    private fun advance(j: ShareJob) {
        val now = SystemClock.uptimeMillis()
        if (now - lastActionAt < MIN_GAP_MS) return

        val roots = waRoots()
        if (roots.isEmpty()) return
        val inStep = now - stepAt

        // Dialogo informativo que WhatsApp ensena la primera vez
        if (step >= Step.LIVE) {
            findText(roots, CONTINUE_TEXT, exact = true)?.let { if (click(it)) return }
        }

        when (step) {
            Step.ATTACH -> {
                val btn = findId(roots, "input_attach_button")
                    ?: findText(roots, ATTACH_DESC, exact = true)
                if (btn != null && click(btn)) setStep(Step.LOCATION)
            }

            Step.LOCATION -> {
                val item = findText(roots, LOCATION_TEXT, exact = true)
                when {
                    item != null && click(item) -> setStep(Step.LIVE)
                    inStep > 3000 -> setStep(Step.ATTACH)   // el menu no se abrio: reintenta
                }
            }

            Step.LIVE -> {
                val item = findText(roots, LIVE_TEXT, exact = false)
                if (item != null && click(item)) setStep(Step.SEND)
            }

            Step.SEND -> {
                if (!durationPicked) {
                    val d = findText(roots, DURATION_TEXT[j.duration.coerceIn(0, 2)], exact = false)
                    if (d != null && click(d)) {
                        durationPicked = true
                        return
                    }
                }
                findText(roots, OK_TEXT, exact = true)?.let { if (click(it)) return }

                // Si no aparecen las duraciones, espera un poco antes de enviar con la de por defecto
                if (durationPicked || inStep > 2500) {
                    val send = findId(roots, "send") ?: findText(roots, SEND_DESC, exact = true)
                    if (send != null && click(send)) {
                        done(j)
                        return
                    }
                }
            }
        }

        // Si llevamos rato atascados, vuelca la pantalla al Logcat para poder ajustar textos
        if (inStep > 4000 && !dumpedThisStep) {
            dumpedThisStep = true
            Log.w(TAG, "Atascado en $step. Contenido de pantalla:")
            roots.forEach { dump(it, 0) }
        }
    }

    private fun done(j: ShareJob) {
        job = null
        Log.i(TAG, "Hecho")
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(this, getString(R.string.svc_sharing, j.name), Toast.LENGTH_SHORT).show()
        }, 900)
    }

    private fun fail(why: String) {
        Log.w(TAG, "Fallo: $why")
        waRoots().forEach { dump(it, 0) }
        job = null
        Toast.makeText(this, getString(R.string.svc_failed), Toast.LENGTH_LONG).show()
    }

    // ---------- Busqueda en pantalla ----------

    /** Raices de todas las ventanas visibles de WhatsApp (chat, menu emergente, dialogos...). */
    private fun waRoots(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        try {
            windows?.forEach { w ->
                w.root?.let { r -> if (r.packageName?.toString() in WA_PACKAGES) list += r }
            }
        } catch (_: Exception) {
        }
        if (list.isEmpty()) {
            rootInActiveWindow?.let { r -> if (r.packageName?.toString() in WA_PACKAGES) list += r }
        }
        // Las ventanas de arriba (menus, dialogos) primero
        return list.reversed()
    }

    private fun findId(roots: List<AccessibilityNodeInfo>, id: String): AccessibilityNodeInfo? {
        for (r in roots) {
            val pkg = r.packageName?.toString() ?: continue
            r.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
                ?.firstOrNull { it.isVisibleToUser }
                ?.let { return it }
        }
        return null
    }

    private fun findText(
        roots: List<AccessibilityNodeInfo>,
        texts: List<String>,
        exact: Boolean,
    ): AccessibilityNodeInfo? {
        for (r in roots) for (t in texts) {
            val hits = r.findAccessibilityNodeInfosByText(t) ?: continue
            hits.firstOrNull { n ->
                if (!n.isVisibleToUser) return@firstOrNull false
                val label = listOfNotNull(n.text?.toString(), n.contentDescription?.toString())
                if (exact) label.any { it.trim().equals(t, ignoreCase = true) }
                else label.any { it.contains(t, ignoreCase = true) }
            }?.let { return it }
        }
        return null
    }

    // ---------- Pulsar ----------

    private fun click(node: AccessibilityNodeInfo): Boolean {
        Log.i(TAG, "Pulsando: text='${node.text}' desc='${node.contentDescription}' id=${node.viewIdResourceName}")
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.isEnabled) {
                if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    lastActionAt = SystemClock.uptimeMillis()
                    return true
                }
                break
            }
            n = n.parent
        }
        // Plan B: toque fisico en el centro del elemento
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.isEmpty) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val ok = dispatchGesture(gesture, null, null)
        if (ok) lastActionAt = SystemClock.uptimeMillis()
        return ok
    }

    private fun dump(n: AccessibilityNodeInfo?, depth: Int) {
        if (n == null || depth > 25) return
        val text = n.text?.toString()?.take(60)
        val desc = n.contentDescription?.toString()?.take(60)
        if (text != null || desc != null || n.viewIdResourceName != null || n.isClickable) {
            Log.d(
                TAG, "  ".repeat(depth) +
                    "${n.className?.toString()?.substringAfterLast('.')} " +
                    "text='$text' desc='$desc' id=${n.viewIdResourceName} click=${n.isClickable}"
            )
        }
        for (i in 0 until n.childCount) dump(n.getChild(i), depth + 1)
    }
}

package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.machine.KitchenTimer.State
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui

/**
 * Minuteur de cuisine, indépendant de la machine (il ne commande ni moteur ni chauffe).
 * Il continue de compter quand on quitte l'écran ; la pastille de la barre du haut le rappelle.
 * Bouton rotatif : un cran = 1 minute (les boutons +10 s servent au réglage fin), appui = démarrer / pause / OK.
 */
class TimerScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Minuteur")
    override val keepAwake: Boolean get() = a.app.timer.timer.state == State.RUNNING
    private val driver get() = a.app.timer
    private lateinit var value: TextView
    private lateinit var status: TextView
    private lateinit var main: LinearLayout
    private lateinit var mainLabel: TextView
    private lateinit var presets: LinearLayout
    private var unsub: (() -> Unit)? = null

    override fun build(): View {
        val root = Ui.vertical(c, Gravity.CENTER_HORIZONTAL)
        root.setPadding(Ui.u(24), Ui.u(10), Ui.u(24), Ui.u(24))
        root.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(1, 0, 1f))
        value = Ui.text(c, "00:00", 190f, Ui.TEXT, Ui.THIN)
        root.addView(value, Ui.lp(Ui.WRAP, Ui.WRAP))
        status = Ui.text(c, "", 22f, Ui.TEXT2)
        root.addView(status, Ui.lp(Ui.WRAP, Ui.WRAP, t = 4))
        root.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(1, 0, 1f))

        presets = Ui.horizontal(c, Gravity.CENTER)
        for ((label, ms) in listOf("+10 s" to 10_000L, "+1 min" to 60_000L, "+5 min" to 300_000L, "+10 min" to 600_000L)) {
            presets.addView(Ui.button(c, label, Ui.Style.GHOST, height = 64, textSize = 20f) { driver.add(ms) }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        }
        presets.addView(Ui.button(c, "−1 min", Ui.Style.GHOST, height = 64, textSize = 20f) { driver.add(-60_000L) })
        root.addView(presets, Ui.lp(Ui.WRAP, Ui.WRAP, b = 18))

        val buttons = Ui.horizontal(c, Gravity.CENTER)
        main = Ui.button(c, tr("Démarrer"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 76, textSize = 23f) { primary() }
        mainLabel = Ui.chipText(main)
        buttons.addView(main, Ui.lp(Ui.WRAP, Ui.WRAP, r = 16))
        buttons.addView(Ui.button(c, tr("Remettre à zéro"), Ui.Style.GHOST, height = 76, textSize = 21f) { driver.reset() })
        root.addView(buttons)
        return root
    }

    private fun primary() {
        when (driver.timer.state) {
            State.IDLE, State.PAUSED -> driver.start()
            State.RUNNING -> driver.pause()
            State.RINGING -> driver.dismiss()
        }
    }

    override fun onShow() {
        unsub?.invoke()
        unsub = driver.listen { refresh() }
        // On ouvre l'écran en réponse au « terminé » : il est vu, la durée réglée revient.
        if (driver.timer.state == State.RINGING && !driver.bellOn) driver.dismiss()
        refresh()
    }

    override fun onHide() {
        unsub?.invoke()
        unsub = null
    }

    private fun refresh() {
        val t = driver.timer
        val s = (t.remainingMs + 999) / 1000
        value.text = "%02d:%02d".format(s / 60, s % 60)
        val (label, col) = when (t.state) {
            State.IDLE -> (if (t.presetMs > 0) tr("Prêt") else tr("Réglez une durée")) to Ui.TEXT2
            State.RUNNING -> tr("En cours") to Ui.ACCENT
            State.PAUSED -> tr("En pause") to Ui.WARN
            State.RINGING -> tr("Temps écoulé") to Ui.DANGER
        }
        status.text = label
        status.setTextColor(col)
        value.setTextColor(if (t.state == State.RINGING) Ui.DANGER else Ui.TEXT)
        mainLabel.text = when (t.state) {
            State.RUNNING -> tr("Pause")
            State.RINGING -> tr("OK")
            State.PAUSED -> tr("Reprendre")
            State.IDLE -> tr("Démarrer")
        }
        main.alpha = if (t.state == State.IDLE && t.presetMs == 0L) 0.4f else 1f
        presets.visibility = if (t.state == State.RINGING) View.INVISIBLE else View.VISIBLE
    }

    override fun onKnob(delta: Int): Boolean {
        if (driver.timer.state == State.RINGING) return true
        driver.add(delta * 60_000L)
        return true
    }

    override fun onKnobPress(): Boolean {
        primary()
        return true
    }
}

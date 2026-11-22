package app.toque

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.toque.core.machine.KitchenTimer
import app.toque.ui.Sounds

/**
 * Fait vivre le minuteur de cuisine : horloge, sonnerie à l'échéance (répétée toutes les 30 s
 * comme l'alarme de fin de programme), notification des écrans. Porté par l'application et non
 * par l'activité : le minuteur continue si l'interface est recréée (changement de langue).
 */
class TimerDriver(private val prefs: Prefs) {
    val timer = KitchenTimer { SystemClock.elapsedRealtime() }
    private val handler = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<() -> Unit>()
    private var bellRepeat: Runnable? = null
    private var ticking = false

    /** Vrai tant que la sonnerie n'a pas été arrêtée (par un toucher) : sert à l'affichage. */
    var bellOn = false
        private set

    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            if (timer.poll()) onDone()
            notifyListeners()
            scheduleTick()
        }
    }

    /** S'abonne aux changements (une fois par quart de seconde en marche). Renvoie le désabonnement. */
    fun listen(l: () -> Unit): () -> Unit {
        listeners += l
        return { listeners -= l }
    }

    fun add(deltaMs: Long) { timer.add(deltaMs); changed() }
    fun start() { timer.start(); changed() }
    fun pause() { timer.pause(); changed() }
    fun reset() { silence(); timer.reset(); changed() }
    fun dismiss() { silence(); timer.dismiss(); changed() }

    /** Coupe la sonnerie sans effacer l'état « terminé » (le premier toucher suffit). */
    fun silence() {
        bellRepeat?.let { handler.removeCallbacks(it) }
        bellRepeat = null
        if (bellOn) { bellOn = false; notifyListeners() }
    }

    private fun changed() {
        notifyListeners()
        scheduleTick()
    }

    private fun scheduleTick() {
        if (ticking || timer.state != KitchenTimer.State.RUNNING) return
        ticking = true
        handler.postDelayed(tick, 250)
    }

    private fun notifyListeners() {
        for (l in listeners.toList()) l()
    }

    private fun onDone() {
        bellOn = true
        if (!prefs.sound) return
        Sounds.recipeDone()
        if (!prefs.repeatEndAlarm) return
        var left = 20
        val r = object : Runnable {
            override fun run() {
                if (--left < 0) { bellRepeat = null; return }
                Sounds.recipeDone()
                handler.postDelayed(this, 30_000L)
            }
        }
        bellRepeat = r
        handler.postDelayed(r, 30_000L)
    }
}

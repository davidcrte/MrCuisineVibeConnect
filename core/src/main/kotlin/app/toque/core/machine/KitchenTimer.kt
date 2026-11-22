package app.toque.core.machine

/**
 * Minuteur de cuisine indépendant de la machine : il ne touche ni au moteur ni à la chauffe.
 * Logique pure, l'horloge est injectée (millisecondes monotones) pour pouvoir la tester.
 *
 * États : IDLE (durée réglée, arrêté), RUNNING, PAUSED, RINGING (échu, sonne jusqu'à [dismiss]).
 */
class KitchenTimer(private val clock: () -> Long) {
    enum class State { IDLE, RUNNING, PAUSED, RINGING }

    var state = State.IDLE
        private set

    /** Durée réglée au départ de la course (affichée à l'arrêt). */
    var presetMs = 0L
        private set

    private var endAt = 0L
    private var pausedLeft = 0L

    /** Temps restant : durée réglée à l'arrêt, 0 quand ça sonne. */
    val remainingMs: Long
        get() = when (state) {
            State.IDLE -> presetMs
            State.RUNNING -> (endAt - clock()).coerceAtLeast(0)
            State.PAUSED -> pausedLeft
            State.RINGING -> 0
        }

    /** Ajoute (ou retire, si négatif) du temps. Bornes 0 à [MAX_MS]. Sans effet pendant la sonnerie. */
    fun add(deltaMs: Long) {
        when (state) {
            State.IDLE -> presetMs = (presetMs + deltaMs).coerceIn(0, MAX_MS)
            State.RUNNING -> endAt = (endAt + deltaMs).coerceIn(clock(), clock() + MAX_MS)
            State.PAUSED -> pausedLeft = (pausedLeft + deltaMs).coerceIn(0, MAX_MS)
            State.RINGING -> Unit
        }
    }

    /** Démarre ou reprend. Refusé si la durée est nulle. */
    fun start() {
        when (state) {
            State.IDLE -> if (presetMs > 0) { endAt = clock() + presetMs; state = State.RUNNING }
            State.PAUSED -> if (pausedLeft > 0) { endAt = clock() + pausedLeft; state = State.RUNNING }
            else -> Unit
        }
    }

    fun pause() {
        if (state != State.RUNNING) return
        pausedLeft = remainingMs
        state = if (pausedLeft > 0) State.PAUSED else State.RINGING
    }

    /** À appeler régulièrement : passe à RINGING à l'échéance. Renvoie vrai à ce passage, une seule fois. */
    fun poll(): Boolean {
        if (state == State.RUNNING && clock() >= endAt) {
            state = State.RINGING
            return true
        }
        return false
    }

    /** Arrête la sonnerie : le minuteur revient à la dernière durée réglée, arrêté. */
    fun dismiss() {
        if (state == State.RINGING) state = State.IDLE
    }

    /** Remet à zéro (durée réglée comprise), sauf pendant la sonnerie où il ne fait que l'arrêter. */
    fun reset() {
        if (state == State.RINGING) { state = State.IDLE; return }
        state = State.IDLE
        presetMs = 0
    }

    companion object {
        const val MAX_MS = 99L * 60_000 + 59_000
    }
}

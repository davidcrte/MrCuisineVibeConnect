package app.toque.core.recipes

/**
 * Enchaînement des étapes d'une recette : quelles étapes la machine peut lancer à la suite de la
 * précédente, sans nouvel appui. Tout ce qui rend la cuisson plus dangereuse que l'étape d'avant
 * (vitesse au-delà de 3 en hausse, chauffe en hausse) ou qui n'a pas été vérifié attend un appui.
 */
object Chain {
    /** Vitesse au-delà de laquelle une hausse demande un appui (au-delà, le contenu peut gicler). */
    const val MAX_UNATTENDED_SPEED = 3

    /**
     * L'étape [i] continue directement la précédente : un réglage machine seul (pas de texte à
     * suivre, pas de pesée), après une autre étape machine, ni plus rapide au-delà de 3 ni plus
     * chaud, et déjà vérifié s'il a été importé. Le Turbo reste toujours à l'appui.
     */
    fun continues(steps: List<Step>, i: Int, reviewed: (Int) -> Boolean): Boolean {
        val s = steps.getOrNull(i) ?: return false
        val m = s.machine ?: return false
        val prev = steps.getOrNull(i - 1)?.machine ?: return false
        if (m.program == StepProgram.TURBO || prev.program == StepProgram.TURBO) return false
        if (s.weigh != null || (s.text.isNotBlank() && s.text != m.summary)) return false
        if (s.needsReview && !reviewed(i)) return false
        if (m.violations().isNotEmpty()) return false
        return !escalates(prev, m)
    }

    /** Nombre d'étapes qui s'enchaîneront après l'étape [i]. */
    fun length(steps: List<Step>, i: Int, reviewed: (Int) -> Boolean): Int {
        var n = 0
        while (continues(steps, i + n + 1, reviewed)) n++
        return n
    }

    /** [next] tourne plus vite que [prev] au-delà de 3, ou chauffe plus (programmes compris : vapeur, rissolage). */
    fun escalates(prev: StepMachine, next: StepMachine): Boolean {
        val a = peak(prev) ?: return true
        val b = peak(next) ?: return true
        val faster = b.first > MAX_UNATTENDED_SPEED && b.first > a.first
        val hotter = b.second > a.second
        return faster || hotter
    }

    /** Vitesse et niveau de température les plus hauts du programme de l'étape. */
    private fun peak(m: StepMachine): Pair<Int, Int>? {
        val phases = m.toJob("")?.phases ?: return null
        return phases.maxOf { it.setting.speed } to phases.maxOf { it.setting.temperatureLevel }
    }
}

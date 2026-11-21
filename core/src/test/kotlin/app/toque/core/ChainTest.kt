package app.toque.core

import app.toque.core.recipes.Chain
import app.toque.core.recipes.Step
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram
import app.toque.core.recipes.Weigh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainTest {
    private fun bare(m: StepMachine) = Step("", m)
    private val none: (Int) -> Boolean = { false }

    /** Fixture S8 à textes vides : chauffe, puis mixage chauffé rapide. Le mixage attend un appui. */
    @Test fun chainPausesBeforeFasterOrHotterStep() {
        val steps = listOf(
            bare(StepMachine(seconds = 300, speed = 1)),
            bare(StepMachine(seconds = 300, speed = 1, tempC = 100)),
            bare(StepMachine(seconds = 30, speed = 5, tempC = 100)),
        )
        assertFalse("chauffe en hausse", Chain.continues(steps, 1, none))
        assertFalse("vitesse 5 après 1", Chain.continues(steps, 2, none))
        assertEquals(0, Chain.length(steps, 0, none))
    }

    @Test fun slowerOrCoolerStepsStillChain() {
        val steps = listOf(
            bare(StepMachine(seconds = 60, speed = 5, tempC = 100)),
            bare(StepMachine(seconds = 60, speed = 3, tempC = 100)),
            bare(StepMachine(seconds = 60, speed = 2)),
            bare(StepMachine(seconds = 60, speed = 3)),
        )
        assertEquals(3, Chain.length(steps, 0, none))
    }

    @Test fun speedUpToThreeChains() {
        val steps = listOf(bare(StepMachine(seconds = 60, speed = 1)), bare(StepMachine(seconds = 60, speed = 3)))
        assertTrue(Chain.continues(steps, 1, none))
        val faster = listOf(bare(StepMachine(seconds = 60, speed = 4)), bare(StepMachine(seconds = 60, speed = 6)))
        assertFalse(Chain.continues(faster, 1, none))
    }

    @Test fun programsCountWithTheirOwnHeatAndSpeed() {
        val manual = bare(StepMachine(seconds = 60, speed = 2))
        assertFalse("vapeur après un réglage froid", Chain.continues(listOf(manual, bare(StepMachine(StepProgram.STEAM, seconds = 600))), 1, none))
        assertFalse("pétrin (vitesse 4) après vitesse 2", Chain.continues(listOf(manual, bare(StepMachine(StepProgram.KNEAD, seconds = 120))), 1, none))
    }

    @Test fun unreviewedImportedStepWaitsForAPress() {
        val steps = listOf(
            bare(StepMachine(seconds = 60, speed = 2)),
            Step("", StepMachine(seconds = 60, speed = 1), needsReview = true),
        )
        assertFalse(Chain.continues(steps, 1, none))
        assertTrue(Chain.continues(steps, 1) { it == 1 })
    }

    @Test fun textWeighingAndTurboBreakTheChain() {
        val a = StepMachine(seconds = 60, speed = 2)
        assertFalse(Chain.continues(listOf(bare(a), Step("Ajouter le lait.", a)), 1, none))
        assertTrue(Chain.continues(listOf(bare(a), Step(a.summary, a)), 1, none))
        assertFalse(Chain.continues(listOf(bare(a), Step("", a, Weigh("lait", 200))), 1, none))
        assertFalse(Chain.continues(listOf(bare(StepMachine(StepProgram.TURBO, pulses = 3)), bare(a)), 1, none))
        assertFalse(Chain.continues(listOf(Step("Couper."), bare(a)), 1, none))
    }
}

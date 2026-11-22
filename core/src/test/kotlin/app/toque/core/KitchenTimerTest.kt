package app.toque.core

import app.toque.core.machine.KitchenTimer
import app.toque.core.machine.KitchenTimer.State
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class KitchenTimerTest {
    private var now = 1_000L
    private val timer = KitchenTimer { now }

    @Test
    fun refusesToStartWithoutDuration() {
        timer.start()
        assertEquals(State.IDLE, timer.state)
    }

    @Test
    fun countsDownAndRingsOnce() {
        timer.add(60_000)
        timer.start()
        now += 59_000
        assertFalse(timer.poll())
        assertEquals(1_000, timer.remainingMs)
        now += 1_000
        assertTrue(timer.poll())
        assertEquals(State.RINGING, timer.state)
        assertFalse("ne signale l'échéance qu'une fois", timer.poll())
        assertEquals(0, timer.remainingMs)
    }

    @Test
    fun pauseKeepsRemainingTimeAndResumeRestartsFromIt() {
        timer.add(120_000)
        timer.start()
        now += 30_000
        timer.pause()
        assertEquals(State.PAUSED, timer.state)
        now += 600_000
        assertFalse(timer.poll())
        assertEquals(90_000, timer.remainingMs)
        timer.start()
        now += 90_000
        assertTrue(timer.poll())
    }

    @Test
    fun adjustWorksInEveryStateExceptRinging() {
        timer.add(60_000)
        timer.add(-90_000)
        assertEquals("jamais négatif", 0, timer.presetMs)
        timer.add(60_000)
        timer.start()
        timer.add(30_000)
        assertEquals(90_000, timer.remainingMs)
        timer.add(-200_000)
        assertEquals(0, timer.remainingMs)
        assertTrue(timer.poll())
        timer.add(60_000)
        assertEquals("sans effet pendant la sonnerie", 0, timer.remainingMs)
    }

    @Test
    fun capsAtNinetyNineMinutes() {
        repeat(20) { timer.add(600_000) }
        assertEquals(KitchenTimer.MAX_MS, timer.presetMs)
        timer.start()
        timer.add(600_000)
        assertEquals(KitchenTimer.MAX_MS, timer.remainingMs)
    }

    @Test
    fun dismissKeepsPresetResetClearsIt() {
        timer.add(45_000)
        timer.start()
        now += 45_000
        timer.poll()
        timer.dismiss()
        assertEquals(State.IDLE, timer.state)
        assertEquals("on peut relancer la même durée", 45_000, timer.presetMs)
        timer.reset()
        assertEquals(0, timer.presetMs)
    }

    @Test
    fun resetWhileRingingOnlyStopsTheBell() {
        timer.add(10_000)
        timer.start()
        now += 10_000
        timer.poll()
        timer.reset()
        assertEquals(State.IDLE, timer.state)
        assertEquals(10_000, timer.presetMs)
    }
}

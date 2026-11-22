package app.toque.core

import app.toque.core.machine.Accessory
import app.toque.core.machine.Clock
import app.toque.core.machine.Limits
import app.toque.core.machine.ControllerConfig
import app.toque.core.machine.EventType
import app.toque.core.machine.FaultCode
import app.toque.core.machine.JobKind
import app.toque.core.machine.JobSpec
import app.toque.core.machine.JobState
import app.toque.core.machine.LinkState
import app.toque.core.machine.MachineController
import app.toque.core.machine.MachineEvent
import app.toque.core.machine.Phase
import app.toque.core.machine.Programs
import app.toque.core.machine.SafetyPolicy
import app.toque.core.machine.Setting
import app.toque.core.machine.SimBoard
import app.toque.core.protocol.Direction
import app.toque.core.protocol.Mcc
import app.toque.core.protocol.Operation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeClock(var now: Long = 1_000_000L) : Clock {
    override fun nowMs() = now
}

/** Banc d'essai : contrôleur + base simulée, cadencés à 100 ms, sans fil d'exécution. */
class Bench(config: ControllerConfig = ControllerConfig()) {
    val clock = FakeClock()
    val board = SimBoard(clock)
    val c = MachineController(clock, config)
    val events = ArrayList<MachineEvent>()
    /** Trames émises : (temps, opération, vitesse, temp, sens) */
    val tx = ArrayList<IntArray>()

    init {
        c.listener = { events += it }
        c.onTransportUp("banc")
    }

    fun step(ms: Long = 100) {
        var left = ms
        while (left > 0) {
            clock.now += 100
            left -= 100
            val frame = c.tick()
            tx += intArrayOf(
                (clock.now - 1_000_000L).toInt(), frame[3].toInt() and 0xFF, frame[5].toInt(),
                frame[6].toInt(), frame[11].toInt(),
            )
            for (r in board.feed(frame)) c.onBytes(r)
        }
    }

    fun ready(): Bench {
        step(600)
        assertEquals(LinkState.READY, c.link)
        return this
    }

    val snap get() = c.snapshot()
    fun lastOp() = tx.last()[1]
}

class ControllerTest {

    @Test fun armsThenAcceptsAManualJobAndFinishes() {
        val b = Bench()
        b.step(100)
        assertEquals(LinkState.ARMING, b.c.link)
        b.ready()
        val v = b.c.startJob(Programs.manual(5, 0, Direction.CLOCKWISE, 10_000))
        assertTrue(v.message, v.ok)
        b.step(300)
        assertEquals(JobState.RUNNING.name, b.snap.jobState)
        b.step(2_000)
        assertEquals(Operation.STANDARD.wire, b.lastOp())
        assertEquals(5, b.tx.last()[2])
        b.step(9_000)
        assertTrue(b.snap.jobState == JobState.STOPPING.name || b.snap.jobState == JobState.FINISHED.name)
        b.step(5_000)
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
        assertEquals(Operation.STOP.wire, b.lastOp())
        assertTrue(b.events.any { it.type == EventType.JOB_FINISHED })
        // le temps de travail ne dépasse pas la consigne
        val running = b.tx.count { it[1] == Operation.STANDARD.wire }
        assertTrue("trames actives : $running", running in 95..102)
    }

    @Test fun coldGoesStraightToTheRequestedSpeed() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(10, 0, Direction.CLOCKWISE, 20_000))
        b.step(4_000)
        val speeds = b.tx.filter { it[1] == Operation.STANDARD.wire }.map { it[2] }
        assertTrue(speeds.isNotEmpty())
        assertTrue("vitesses : $speeds", speeds.all { it == 10 })
    }

    @Test fun refusesToStartWithLidOpen() {
        val b = Bench().ready()
        b.board.lidOpen = true
        b.step(200)
        val v = b.c.startJob(Programs.manual(3, 0, Direction.CLOCKWISE, 5_000))
        assertFalse(v.ok)
        assertTrue(v.has("lid"))
    }

    @Test fun requiredBowlRefusesToStartWithoutIt() {
        val b = Bench(ControllerConfig(requireBowl = true)).ready()
        assertTrue(b.snap.bowlDetected)
        b.board.bowlPresent = false
        b.step(200)
        assertFalse(b.snap.bowlDetected)
        val v = b.c.startJob(Programs.manual(3, 0, Direction.CLOCKWISE, 5_000))
        assertFalse(v.ok)
        assertTrue(v.has("bowl"))
    }

    /** Base qui signale le bol à l'envers : bit NTC à 1 quand le bol est retiré. */
    @Test fun invertedBowlSensorIsReadTheOtherWayRound() {
        val b = Bench(ControllerConfig(requireBowl = true, invertBowl = true)).ready()
        // Bit à 1 : sur cette base, le bol est retiré.
        assertFalse(b.snap.bowlDetected)
        val refused = b.c.startJob(Programs.manual(3, 0, Direction.CLOCKWISE, 5_000))
        assertFalse(refused.ok)
        assertTrue(refused.has("bowl"))
        // Bit à 0 : le bol est en place.
        b.board.bowlPresent = false
        b.step(200)
        assertTrue(b.snap.bowlDetected)
        val v = b.c.startJob(Programs.manual(3, 0, Direction.CLOCKWISE, 5_000))
        assertTrue(v.message, v.ok)
    }

    @Test fun lidOpeningPausesAndNothingRestartsAlone() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(3, 13, Direction.CLOCKWISE, 60_000))
        b.step(5_000)
        val before = b.snap.elapsedMs
        b.board.lidOpen = true
        b.step(300)
        assertEquals(JobState.PAUSED.name, b.snap.jobState)
        assertEquals("LID", b.snap.pauseReason)
        assertEquals(Operation.STOP.wire, b.lastOp())
        b.board.lidOpen = false
        b.step(3_000)
        assertEquals("ne doit pas repartir seul", JobState.PAUSED.name, b.snap.jobState)
        assertTrue(b.snap.elapsedMs - before < 600)
        assertTrue(b.c.resume().ok)
        b.step(3_000)
        assertEquals(JobState.RUNNING.name, b.snap.jobState)
        assertEquals(Operation.STANDARD.wire, b.lastOp())
    }

    @Test fun boardErrorAbortsAndLatches() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(4, 0, Direction.CLOCKWISE, 60_000))
        b.step(2_000)
        b.board.errorCode = 7
        b.step(300)
        assertEquals(FaultCode.BOARD_ERROR.name, b.snap.faultCode)
        assertEquals(JobState.ABORTED.name, b.snap.jobState)
        assertEquals(Operation.STOP.wire, b.lastOp())
        assertFalse(b.c.acknowledgeFault().ok)
        b.board.errorCode = 0
        b.step(300)
        assertTrue(b.c.acknowledgeFault().ok)
        assertEquals("", b.snap.faultCode)
        b.c.clearFinished()
        assertTrue(b.c.startJob(Programs.manual(4, 0, Direction.CLOCKWISE, 5_000)).ok)
    }

    @Test fun silentBoardStopsEverything() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(4, 0, Direction.CLOCKWISE, 60_000))
        b.step(2_000)
        b.board.silent = true
        b.step(2_000)
        assertEquals(LinkState.LOST, b.c.link)
        assertEquals(FaultCode.COMM_LOST.name, b.snap.faultCode)
        assertEquals(Operation.STOP.wire, b.lastOp())
        b.board.silent = false
        b.step(1_000)
        assertEquals(LinkState.READY, b.c.link)
        assertTrue(b.c.acknowledgeFault().ok)
    }

    @Test fun stopThenStartAlwaysEmitsStopAndWaitsForStoppedMotor() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(8, 0, Direction.CLOCKWISE, 60_000))
        b.step(3_000)
        b.c.stop()
        b.c.clearFinished()
        val mark = b.tx.size
        assertTrue(b.c.startJob(Programs.manual(8, 0, Direction.CLOCKWISE, 10_000)).ok)
        b.step(5_000)
        val after = b.tx.subList(mark, b.tx.size)
        val firstStart = after.indexOfFirst { it[1] == Operation.STANDARD.wire }
        assertTrue(firstStart > 0)
        assertTrue(after.take(firstStart).all { it[1] == Operation.STOP.wire })
        // la base simulée met plusieurs centaines de ms à s'arrêter depuis la vitesse 8
        assertTrue("redémarrage trop tôt : $firstStart trames", firstStart >= 5)
    }

    @Test fun kneadChangesDirectionOnlyAfterConfirmedStop() {
        val b = Bench().ready()
        assertTrue(b.c.startJob(Programs.knead(45_000)).ok)
        b.step(50_000)
        val knead = b.tx.filter { it[1] == Operation.KNEAD.wire }
        assertTrue(knead.any { it[4] == Direction.COUNTER_CLOCKWISE.wire })
        assertTrue(knead.any { it[4] == Direction.CLOCKWISE.wire })
        assertTrue(knead.all { it[2] <= 4 })
        for (i in 1 until b.tx.size) {
            val a = b.tx[i - 1]
            val c = b.tx[i]
            if (a[1] == Operation.KNEAD.wire && c[1] == Operation.KNEAD.wire) assertEquals("sens changé sans arrêt", a[4], c[4])
        }
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
    }

    @Test fun toastAlternatesWithoutStopping() {
        val b = Bench().ready()
        assertTrue(b.c.startJob(Programs.toast(70_000)).ok)
        b.step(40_000)
        val toast = b.tx.filter { it[1] == Operation.TOAST.wire }
        assertTrue(toast.any { it[2] == 1 })
        assertTrue(toast.any { it[2] == 0 })
        assertTrue(toast.all { it[3] == 19 && it[4] == Direction.COUNTER_CLOCKWISE.wire })
    }

    @Test fun hotContentNeedsConfirmationAndRampsSlowly() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(1, 13, Direction.CLOCKWISE, 300_000))
        b.step(200_000)
        assertTrue(b.snap.actualTempC >= 60)
        b.c.stop()
        b.step(3_000)
        b.c.clearFinished()
        val v = b.c.startJob(Programs.manual(7, 0, Direction.CLOCKWISE, 30_000))
        assertFalse(v.ok)
        assertTrue(v.has(SafetyPolicy.HOT_BLEND))
        assertTrue(b.c.startJob(Programs.manual(7, 0, Direction.CLOCKWISE, 30_000, hotBlendConfirmed = true)).ok)
        val mark = b.tx.size
        b.step(10_000)
        val speeds = b.tx.subList(mark, b.tx.size).filter { it[1] == Operation.STANDARD.wire }.map { it[2] }
        val firstTop = speeds.indexOf(7)
        assertEquals(1, speeds.first())
        for (i in 1 until speeds.size) assertTrue(speeds[i] - speeds[i - 1] <= 1)
        assertTrue("montée à chaud : $firstTop", firstTop in 12..20)
    }

    /** Étapes de recette enchaînées en un seul programme : pas d'arrêt entre deux réglages de même sens. */
    @Test fun chainedSettingsFollowEachOtherWithoutStopping() {
        val b = Bench().ready()
        val phases = listOf(
            Phase(Setting(Operation.STANDARD, 5, 0, Direction.CLOCKWISE), 3_000),
            Phase(Setting(Operation.STANDARD, 8, 0, Direction.CLOCKWISE), 3_000),
            Phase(Setting(Operation.STANDARD, 2, 0, Direction.CLOCKWISE), 2_000),
        )
        assertTrue(b.c.startJob(JobSpec(JobKind.MANUAL, "série", phases, 8_000, cyclic = false)).ok)
        b.step(9_500)
        val ops = b.tx.map { it[1] }
        val first = ops.indexOf(Operation.STANDARD.wire)
        val last = ops.lastIndexOf(Operation.STANDARD.wire)
        assertTrue(first >= 0)
        assertTrue("arrêt entre deux réglages", ops.subList(first, last + 1).all { it == Operation.STANDARD.wire })
        val speeds = b.tx.subList(first, last + 1).map { it[2] }
        assertEquals(listOf(5, 8, 2), speeds.distinct())
        assertEquals(JobState.FINISHED.name, b.snap.jobState)
    }

    @Test fun countdownWaitsForTemperatureWhenAsked() {
        val b = Bench().ready()
        val spec = Programs.manual(1, 13, Direction.CLOCKWISE, 30_000).copy(countFromTemp = true)
        assertTrue(b.c.startJob(spec).ok)
        b.step(5_000)
        assertTrue(b.snap.heatingUp)
        assertTrue(b.snap.actualTempC < Mcc.celsiusForLevel(13) - Limits.HEAT_REACHED_MARGIN_C)
        assertEquals(0L, b.snap.elapsedMs)
        assertEquals(30_000L, b.snap.remainingMs)
        b.step(300_000)
        assertFalse(b.snap.heatingUp)
        assertTrue(b.snap.elapsedMs > 0 || b.snap.jobState == JobState.FINISHED.name)
    }

    @Test fun countdownStartsAtOnceWithoutTheOptionOrWithoutHeat() {
        val b = Bench().ready()
        assertTrue(b.c.startJob(Programs.manual(1, 13, Direction.CLOCKWISE, 30_000)).ok)
        b.step(5_000)
        assertFalse(b.snap.heatingUp)
        assertTrue(b.snap.elapsedMs >= 4_000)
        b.c.stop()
        b.step(3_000)
        b.c.clearFinished()
        assertTrue(b.c.startJob(Programs.manual(3, 0, Direction.CLOCKWISE, 30_000).copy(countFromTemp = true)).ok)
        b.step(5_000)
        assertFalse(b.snap.heatingUp)
        assertTrue(b.snap.elapsedMs >= 4_000)
    }

    @Test fun waitingForTemperatureHasALimit() {
        val b = Bench().ready()
        // Consigne inatteignable pour la base simulée : on laisse l'attente maximale s'écouler.
        val spec = Programs.manual(1, 19, Direction.CLOCKWISE, 10_000).copy(countFromTemp = true)
        assertTrue(b.c.startJob(spec).ok)
        b.step(Limits.MAX_MS_HEATUP + 5_000)
        assertFalse(b.snap.heatingUp)
        assertTrue(b.snap.elapsedMs > 0 || b.snap.jobState != JobState.RUNNING.name)
    }

    /** Un réglage rapide qui suit une chauffe dans la même série redemande la confirmation « contenu chaud ». */
    @Test fun chainPausesBeforeBlendingHotContents() {
        val b = Bench().ready()
        val phases = listOf(
            Phase(Setting(Operation.STANDARD, 1, 13, Direction.CLOCKWISE), 250_000),
            Phase(Setting(Operation.STANDARD, 7, 0, Direction.CLOCKWISE), 10_000),
        )
        assertTrue(b.c.startJob(JobSpec(JobKind.MANUAL, "série", phases, 260_000, cyclic = false)).ok)
        b.step(252_000)
        assertEquals(JobState.PAUSED.name, b.snap.jobState)
        assertTrue(b.events.any { it.type == EventType.JOB_PAUSED })
        assertTrue(b.tx.takeLast(5).all { it[2] == 0 })
        assertFalse(b.c.resume().ok)
        assertTrue(b.c.resume(hotBlendConfirmed = true).ok)
        b.step(3_000)
        assertEquals(JobState.RUNNING.name, b.snap.jobState)
        assertEquals(7, b.tx.last()[2])
    }

    @Test fun hotRampCanBeTurnedOff() {
        val b = Bench(ControllerConfig(hotRamp = false)).ready()
        b.c.startJob(Programs.manual(1, 13, Direction.CLOCKWISE, 300_000))
        b.step(200_000)
        b.c.stop(); b.step(3_000); b.c.clearFinished()
        assertTrue(b.c.startJob(Programs.manual(7, 0, Direction.CLOCKWISE, 30_000, hotBlendConfirmed = true)).ok)
        val mark = b.tx.size
        b.step(3_000)
        val speeds = b.tx.subList(mark, b.tx.size).filter { it[1] == Operation.STANDARD.wire }.map { it[2] }
        assertTrue("vitesses : $speeds", speeds.isNotEmpty() && speeds.all { it == 7 })
    }

    @Test fun turboRules() {
        val b = Bench().ready()
        assertTrue(b.c.turboPress().ok)
        b.step(5_000)
        val turbo = b.tx.count { it[1] == Operation.TURBO.wire }
        assertTrue("turbo limité à 3 s : $turbo", turbo in 28..31)
        assertTrue(b.events.any { it.type == EventType.TURBO_END })
        assertFalse(b.c.turboPress(Accessory.WHISK).ok)
        b.c.startJob(Programs.manual(1, 13, Direction.CLOCKWISE, 300_000))
        b.step(200_000)
        b.c.stop(); b.step(3_000); b.c.clearFinished()
        val v = b.c.turboPress()
        assertFalse(v.ok)
        assertTrue(v.has("turbo_hot"))
    }

    @Test fun policyLimits() {
        fun refused(speed: Int, temp: Int, dir: Direction) =
            !SafetyPolicy.checkJob(Programs.manual(speed, temp, dir, 60_000), null).isEmpty()
        assertTrue(refused(4, 13, Direction.CLOCKWISE))
        assertFalse(refused(3, 13, Direction.CLOCKWISE))
        assertTrue(refused(4, 0, Direction.COUNTER_CLOCKWISE))
        assertFalse(refused(10, 0, Direction.CLOCKWISE))
        assertTrue(refused(0, 0, Direction.CLOCKWISE))
        assertTrue(SafetyPolicy.checkJob(Programs.manual(7, 0, Direction.CLOCKWISE, 16 * 60_000), null).isNotEmpty())
        assertTrue(SafetyPolicy.checkJob(Programs.manual(5, 0, Direction.CLOCKWISE, 60_000, Accessory.WHISK), null).isNotEmpty())
        assertTrue(SafetyPolicy.checkJob(Programs.knead(121_000), null).isNotEmpty())
        assertTrue(SafetyPolicy.checkJob(Programs.toast(841_000), null).isNotEmpty())
        assertTrue(SafetyPolicy.checkJob(Programs.steam(), null).isEmpty())
    }

    @Test fun adjustDirectionGoesThroughStop() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(3, 0, Direction.CLOCKWISE, 60_000))
        b.step(3_000)
        assertTrue(b.c.adjust(direction = Direction.COUNTER_CLOCKWISE).ok)
        b.step(5_000)
        for (i in 1 until b.tx.size) {
            val a = b.tx[i - 1]
            val c = b.tx[i]
            if (a[1] == Operation.STANDARD.wire && c[1] == Operation.STANDARD.wire) assertEquals(a[4], c[4])
        }
        assertEquals(Direction.COUNTER_CLOCKWISE.wire, b.tx.last()[4])
        assertFalse(b.c.adjust(speed = 6).ok)
    }

    @Test fun longPauseCancels() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(2, 0, Direction.CLOCKWISE, 60_000))
        b.step(2_000)
        b.c.pause()
        b.step(10 * 60_000 + 1_000)
        assertEquals(JobState.ABORTED.name, b.snap.jobState)
    }

    @Test fun steamDoesNotSpinTheBlade() {
        val b = Bench()
        b.board.contentWeightG = 1000
        b.ready()
        assertTrue(b.c.startJob(Programs.steam(60_000)).ok)
        b.step(10_000)
        val steam = b.tx.filter { it[1] == Operation.STEAM.wire }
        assertTrue(steam.isNotEmpty())
        assertTrue(steam.all { it[2] == 0 && it[3] == 17 })
        assertEquals(0, b.snap.rpm)
    }

    @Test fun steamAsksForWaterConfirmation() {
        val b = Bench().ready()
        b.board.contentWeightG = 120
        b.step(2_000)
        val v = b.c.startJob(Programs.steam(60_000))
        assertFalse(v.ok)
        assertEquals(listOf(SafetyPolicy.STEAM_WATER), v.violations.map { it.code })
        assertTrue(b.snap.weightG in 110..120)
        assertTrue(v.message, v.message.contains("${b.snap.weightG} g"))
        b.step(1_000)
        assertTrue(b.tx.none { it[1] == Operation.STEAM.wire })
        assertTrue(b.c.startJob(Programs.steam(60_000).copy(waterConfirmed = true)).ok)
        b.step(1_000)
        assertEquals(Operation.STEAM.wire, b.lastOp())
    }

    @Test fun steamStartsWithWater() {
        val b = Bench().ready()
        b.board.contentWeightG = 600
        b.step(2_000)
        assertTrue(b.c.startJob(Programs.steam(60_000)).ok)
    }

    /** Balance en surcharge ou absente : le poids ne vaut rien, la confirmation est demandée. */
    @Test fun steamAsksForWaterWhenScaleIsUnavailable() {
        val b = Bench().ready()
        b.board.contentWeightG = 1000
        b.board.scaleFlags = 0x04
        b.step(2_000)
        assertTrue(b.c.startJob(Programs.steam(60_000)).has(SafetyPolicy.STEAM_WATER))
        b.board.scaleFlags = 0x00
        b.step(300)
        assertTrue(b.c.startJob(Programs.steam(60_000)).has(SafetyPolicy.STEAM_WATER))
        b.board.scaleFlags = 0x02
        b.step(300)
        assertTrue(b.c.startJob(Programs.steam(60_000)).ok)
    }

    /** La vérification de l'eau ne concerne que la vapeur : un bol vide se mixe normalement. */
    @Test fun waterCheckIsForSteamOnly() {
        val b = Bench().ready()
        assertTrue(b.c.startJob(Programs.manual(1, 13, Direction.CLOCKWISE, 60_000)).ok)
    }

    @Test fun adjustNeedsAFreshSample() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(3, 0, Direction.CLOCKWISE, 60_000))
        b.step(2_000)
        b.board.silent = true
        b.step(1_000)
        val v = b.c.adjust(speed = 4)
        assertFalse(v.ok)
        assertTrue(v.violations.any { it.code == "stale" })
        b.board.silent = false
        b.step(500)
        assertTrue(b.c.adjust(speed = 4).ok)
    }

    @Test fun overTemperatureFaultsOnlyWhileStillRising() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(2, 13, Direction.CLOCKWISE, 300_000))
        b.step(2_000)
        b.board.forcedTempC = 120
        b.step(25_000)
        assertEquals("", b.snap.faultCode)
        b.board.forcedTempC = 124
        b.step(11_000)
        assertEquals(FaultCode.OVER_TEMPERATURE.name, b.snap.faultCode)
        assertEquals(JobState.ABORTED.name, b.snap.jobState)
        assertEquals(Operation.STOP.wire, b.lastOp())
    }

    @Test fun extremeTemperatureFaultsInAnyState() {
        val b = Bench().ready()
        b.board.forcedTempC = 155
        b.step(1_500)
        assertEquals("", b.snap.faultCode)
        b.step(1_000)
        assertEquals(FaultCode.OVER_TEMPERATURE.name, b.snap.faultCode)
        assertFalse(b.c.acknowledgeFault().ok)
        b.board.forcedTempC = 90
        b.step(300)
        assertTrue(b.c.acknowledgeFault().ok)
    }

    @Test fun heaterOnDuringUnheatedPhaseFaults() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(3, 0, Direction.CLOCKWISE, 60_000))
        b.step(2_000)
        b.board.heaterStuck = true
        b.step(5_000)
        assertEquals("", b.snap.faultCode)
        b.step(4_000)
        assertEquals(FaultCode.UNEXPECTED_HEAT.name, b.snap.faultCode)
        assertEquals(JobState.ABORTED.name, b.snap.jobState)
        assertFalse(b.c.acknowledgeFault().ok)
        b.board.heaterStuck = false
        b.step(300)
        assertTrue(b.c.acknowledgeFault().ok)
    }

    @Test fun coolingAfterHotStepDoesNotFault() {
        val b = Bench().ready()
        b.c.startJob(Programs.manual(2, 1, Direction.CLOCKWISE, 120_000))
        b.step(1_000)
        var t = 100
        b.board.forcedTempC = t
        repeat(60) {
            b.step(1_000)
            if (it % 5 == 0) b.board.forcedTempC = --t
        }
        assertEquals("", b.snap.faultCode)
        assertEquals(JobState.RUNNING.name, b.snap.jobState)
    }

    @Test fun tareIsAOneFramePulse() {
        val b = Bench().ready()
        b.board.contentWeightG = 600
        b.step(3_000)
        assertTrue(b.snap.weightG in 590..610)
        b.c.tare()
        val frame = b.c.tick()
        assertEquals(0xA9, frame[4].toInt() and 0xFF)
        assertEquals(0, b.c.tick()[4].toInt())
        assertEquals(Mcc.TX_SIZE, frame.size)
    }
}

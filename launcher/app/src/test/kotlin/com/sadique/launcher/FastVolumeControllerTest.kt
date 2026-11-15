package com.sadique.launcher

import java.io.IOException
import java.util.ArrayList
import java.util.Arrays
import java.util.PriorityQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastVolumeControllerTest {
    private var now: Long = 0
    private val tasks = PriorityQueue<Task> { a, b -> a.`when`.compareTo(b.`when`) }
    private val keys = ArrayList<Int>()
    private val errors = ArrayList<Exception>()
    private var fail = false
    private val controller = FastVolumeController(
        { now },
        { task, delay ->
            val next = Task(now + delay, task)
            tasks.add(next)
            FastVolumeController.Cancel { next.cancelled = true }
        },
        { key ->
            if (fail) throw IOException("injection failed")
            keys.add(key)
        },
        { errors.add(it) }
    )
    private val parser = FastVolumeController.Parser(controller)

    private class Task(val `when`: Long, val run: Runnable) {
        var cancelled = false
    }

    private fun advance(delay: Long) {
        val end = now + delay
        while (!tasks.isEmpty() && tasks.peek().`when` <= end) {
            val task = tasks.remove()
            now = task.`when`
            if (!task.cancelled) task.run.run()
        }
        now = end
    }

    private fun bluetooth(usage: String, action: String) {
        parser.line("[ 2958.911284] /dev/input/event3: EV_MSC MSC_SCAN " + usage)
        parser.line("[ 2958.911284] /dev/input/event3: EV_KEY KEY_UNKNOWN " + action + "     \r")
        parser.line("[ 2958.911284] /dev/input/event3: EV_SYN SYN_REPORT 00000000")
    }

    @Test fun ordinarySinglePressAddsNoRepeat() {
        controller.down(24)
        advance(180)
        controller.up(24)
        advance(1000)
        assertTrue(keys.isEmpty())
    }

    @Test fun startsAt300msAndAddsOnePointEvery50ms() {
        controller.down(24)
        advance(299)
        assertTrue(keys.isEmpty())
        advance(1)
        assertEquals(1, keys.size)
        advance(150)
        assertEquals(4, keys.size)
        assertTrue(keys.stream().allMatch { k -> k == 24 })
    }

    @Test fun releaseCancelsAllFutureSteps() {
        controller.down(25)
        advance(320)
        controller.up(25)
        val count = keys.size
        advance(1000)
        assertEquals(count, keys.size)
    }

    @Test fun duplicateDownDoesNotRestartHoldThreshold() {
        controller.down(24)
        advance(200)
        controller.down(24)
        advance(100)
        assertEquals(1, keys.size)
    }

    @Test fun directionChangeRejectsStaleReleaseAndTick() {
        controller.down(24)
        val old = tasks.peek()
        advance(100)
        controller.down(25)
        controller.up(24)
        old.run.run()
        assertTrue(keys.isEmpty())
        advance(300)
        assertEquals(Integer.valueOf(25), keys[0])
    }

    @Test fun lostReleaseHasThirtySecondLimit() {
        controller.down(24)
        advance(30_000)
        val count = keys.size
        assertTrue(count > 0)
        advance(1000)
        assertEquals(count, keys.size)
    }

    @Test fun injectionFailureStopsAndReportsOnce() {
        fail = true
        controller.down(24)
        advance(300)
        advance(1000)
        assertEquals(1, errors.size)
        assertTrue(keys.isEmpty())
    }

    @Test fun capturedBluetoothFramesControlBothDirections() {
        bluetooth("000c0080", "DOWN")
        advance(300)
        bluetooth("000c0080", "UP")
        bluetooth("000c0081", "DOWN")
        advance(300)
        bluetooth("000c0081", "UP")
        assertEquals(Arrays.asList(24, 25), keys)
    }

    @Test fun unrelatedUsageAndOldFramesCannotInjectVolume() {
        bluetooth("000c009f", "DOWN")
        bluetooth("000c009f", "UP")
        parser.line("EV_MSC MSC_SCAN 000c0080")
        parser.line("EV_SYN SYN_REPORT 00000000")
        parser.line("EV_KEY KEY_UNKNOWN DOWN")
        advance(1000)
        assertTrue(keys.isEmpty())
    }

    @Test fun unrelatedReleaseDoesNotCancelHeldVolumeButMissingUsageDoes() {
        bluetooth("000c0080", "DOWN")
        bluetooth("000c009f", "UP")
        advance(300)
        assertEquals(1, keys.size)
        parser.line("EV_KEY KEY_UNKNOWN UP")
        advance(1000)
        assertEquals(1, keys.size)
    }

    @Test fun standardKeysAndNativeRepeatsAreHandled() {
        parser.line("EV_KEY KEY_VOLUMEDOWN DOWN   ")
        advance(300)
        assertEquals(Integer.valueOf(25), keys[0])
        parser.line("EV_KEY KEY_VOLUMEDOWN REPEAT   ")
        advance(1000)
        assertEquals(1, keys.size)
    }

    @Test fun otherKeyCodesCannotStartRepeat() {
        controller.down(83)
        advance(1000)
        assertTrue(keys.isEmpty())
    }

    @Test fun configuredRepeatWaitIsUsedWithoutChangingHoldThreshold() {
        val slower = FastVolumeController(
            { now },
            { task, delay ->
                val next = Task(now + delay, task)
                tasks.add(next)
                FastVolumeController.Cancel { next.cancelled = true }
            },
            { key -> keys.add(key) },
            { errors.add(it) },
            100
        )
        slower.down(24)
        advance(300)
        assertEquals(1, keys.size)
        advance(99)
        assertEquals(1, keys.size)
        advance(1)
        assertEquals(2, keys.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun repeatWaitBelow50msIsRejected() {
        FastVolumeController({ now }, { _, _ -> FastVolumeController.Cancel {} }, { key -> keys.add(key) }, { errors.add(it) }, 49)
    }

    @Test(expected = IllegalArgumentException::class)
    fun repeatWaitAboveOneSecondIsRejected() {
        FastVolumeController({ now }, { _, _ -> FastVolumeController.Cancel {} }, { key -> keys.add(key) }, { errors.add(it) }, 1001)
    }

    @Test fun heldMuteNeverSynthesizesRepeatedToggles() {
        bluetooth("000c00e2", "DOWN")
        advance(5000)
        bluetooth("000c00e2", "UP")
        assertTrue(keys.isEmpty())
    }

    @Test fun muteCancelsAnExistingVolumeHold() {
        controller.down(24)
        advance(310)
        val before = keys.size
        parser.line("EV_KEY KEY_MUTE DOWN")
        advance(1000)
        parser.line("EV_KEY KEY_MUTE UP")
        assertEquals(before, keys.size)
    }
}

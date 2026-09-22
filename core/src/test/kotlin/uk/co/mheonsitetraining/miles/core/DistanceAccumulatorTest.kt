package uk.co.mheonsitetraining.miles.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistanceAccumulatorTest {

    @Test
    fun `haversine matches a known distance`() {
        // London (Charing Cross) to Birmingham (New Street), roughly 162 km as the crow flies.
        val metres = DistanceAccumulator.haversineMetres(51.5080, -0.1247, 52.4778, -1.8990)
        assertEquals(162.0, metres / 1000, 2.0)
    }

    @Test
    fun `ignores inaccurate fixes, jitter and teleports`() {
        val acc = DistanceAccumulator()
        assertTrue(acc.add(Fix(52.0, -1.0, 5.0, 0)))
        // A 1.1 km jump in 1 second is impossible.
        assertFalse(acc.add(Fix(52.01, -1.0, 5.0, 1_000)))
        // Poor accuracy.
        assertFalse(acc.add(Fix(52.01, -1.0, 200.0, 10_000)))
        // Jitter of ~5m while parked.
        assertFalse(acc.add(Fix(52.00004, -1.0, 5.0, 20_000)))
        // A genuine 1.1 km move over 60 seconds.
        assertTrue(acc.add(Fix(52.01, -1.0, 5.0, 81_000)))
        assertEquals(1112.0, acc.totalMetres, 5.0)
    }
}

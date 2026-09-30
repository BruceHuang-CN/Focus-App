package com.example.focus_app.ui.reminder

import org.junit.Assert.*
import org.junit.Test

class BreathingInteractionTest {
    @Test fun rapid_early_taps_do_not_finish_the_whole_flight_in_two_seconds() {
        val rapidTaps = List(8) { it * .025f }
        assertTrue("Fast tapping must not exhaust all movement with three seconds left",
            boostedParticleProgress(.4f, 0f, rapidTaps) < .85f)
    }

    @Test fun no_taps_preserve_original_progress() {
        assertEquals(.35f, boostedParticleProgress(.35f, 0f, emptyList()), .0001f)
    }
    @Test fun tapping_does_not_teleport_existing_particles() {
        val before = boostedParticleProgress(.3f, 0f, listOf(.1f))
        val tapped = boostedParticleProgress(.3f, 0f, listOf(.1f, .3f))
        assertEquals(before, tapped, .0001f)
    }
    @Test fun repeated_taps_speed_up_future_motion() {
        assertTrue(boostedParticleProgress(.6f, 0f, listOf(.2f, .25f, .3f)) >
            boostedParticleProgress(.6f, 0f, listOf(.2f)))
    }
    @Test fun newly_spawned_particles_start_at_edge_and_finish_at_deadline() {
        val taps = listOf(.1f, .3f, .7f)
        assertEquals(0f, boostedParticleProgress(.7f, .7f, taps), 0f)
        assertEquals(1f, boostedParticleProgress(1f, .7f, taps), 0f)
    }
    @Test fun trajectory_never_moves_backwards_and_stays_bounded() {
        val taps = listOf(.1f, .15f, .2f)
        var previous = 0f
        for (i in 0..100) {
            val result = boostedParticleProgress(i / 100f, .2f, taps)
            assertTrue(result >= previous && result in 0f..1f)
            previous = result
        }
        assertEquals(72, breathingParticles(5, 1000).size)
    }

    @Test fun rapid_early_taps_leave_visible_flights_in_the_last_three_seconds() {
        val taps = List(8) { it * .025f }
        val flights = breathingFlights(42, taps)
        for (t in listOf(.4f, .6f, .8f, .9f, .96f)) {
            assertTrue("There must still be particles on their way at $t",
                flights.any { it.born < t && breathingFlightProgress(it, t, taps) in .01f.. .75f })
        }
    }

    @Test fun later_taps_still_work_after_the_original_eight_taps() {
        val early = List(8) { it * .025f }
        assertEquals(9, recordBreathingTap(early, .7f).size)
        assertEquals(early, recordBreathingTap(early, early.last() + .001f))
        assertEquals(early, recordBreathingTap(early, .99f))
    }

    @Test fun orb_grows_with_absorbed_particles_without_jumping_on_a_tap() {
        fun radius(t: Float, taps: List<Float>): Float {
            val absorbed = breathingFlights(42, taps).sumOf {
                absorbedParticleWeight(breathingFlightProgress(it, t, taps)).toDouble()
            }.toFloat()
            return breathingOrbRadiusDp(absorbed)
        }
        val many = List(25) { it * .025f }
        assertEquals(radius(.6f, many), radius(.6f, many + .6f), .0001f)
        assertTrue(radius(.99f, many) > radius(.99f, emptyList()) * 1.45f)
        var previous = radius(0f, many)
        for (i in 1..100) {
            val current = radius(i / 100f, many)
            assertTrue(current >= previous)
            assertTrue(current <= 96f)
            previous = current
        }
    }

    @Test fun continuous_waves_are_not_exhausted_by_tap_acceleration() {
        val many = List(40) { it * .02f }
        breathingFlights(42, many).filter { !it.interactive }.forEach { flight ->
            for (t in listOf(.3f, .6f, .9f)) {
                assertEquals(breathingFlightProgress(flight, t, emptyList()),
                    breathingFlightProgress(flight, t, many), 0f)
            }
        }
    }

    @Test fun even_late_particles_merge_at_the_five_second_deadline() {
        val taps = listOf(.1f, .5f, .9f)
        val flights = breathingFlights(42, taps)
        flights.forEach { flight ->
            assertTrue(flight.duration > 0f)
            assertEquals(0f, breathingFlightProgress(flight, flight.born, taps), 0f)
            assertEquals(1f, breathingFlightProgress(flight, 1f, taps), 0f)
        }
        assertEquals(5_000L, BREATHING_TOTAL_MS)
    }
}

package com.example.focus_app.ui.reminder
import org.junit.Assert.*
import org.junit.Test
class ForestBreathingTest {
 @Test fun random_seed_changes_starts_but_same_seed_is_stable() {
   assertEquals(breathingParticles(41), breathingParticles(41))
   assertNotEquals(breathingParticles(41), breathingParticles(42))
   assertTrue(breathingParticles(41).map { it.exponent }.distinct().size > 1)
 }
 @Test fun each_particle_accelerates_and_finishes_at_deadline() {
   breathingParticles(42).forEach { p ->
     val early = particleGatherProgress(.3f,p.delay,p.exponent) - particleGatherProgress(.2f,p.delay,p.exponent)
     val late = particleGatherProgress(.9f,p.delay,p.exponent) - particleGatherProgress(.8f,p.delay,p.exponent)
     assertTrue(late > early * 3f)
     assertTrue(particleGatherProgress(.9f,p.delay,p.exponent) < 1f)
     assertEquals(1f, particleGatherProgress(1f,p.delay,p.exponent), 0f)
   }
 }

 @Test fun particlesStillTravelInLastSecond() {
   assertTrue(particleGatherProgress(0.85f, 0f) < 0.999f)
 }
 @Test fun orbVisibleUntilDeadlineThenGone() {
   assertEquals(1f, orbFade(0.99f), 0f)
   assertEquals(0f, orbFade(1f), 0f)
 }
}

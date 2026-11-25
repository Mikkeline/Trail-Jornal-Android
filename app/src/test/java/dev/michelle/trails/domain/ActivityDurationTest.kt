package dev.michelle.trails.domain

import org.junit.Assert.*
import org.junit.Test

class ActivityDurationTest {
    @Test fun `hours and minutes convert without manual arithmetic`() { assertEquals(90, ActivityDuration.fromParts("1", "30").getOrThrow()); assertEquals(90, ActivityDuration.fromParts(" 1 ", " 30 ").getOrThrow()) }
    @Test fun `hours or minutes can be left blank`() { assertEquals(30, ActivityDuration.fromParts("", "30").getOrThrow()); assertEquals(60, ActivityDuration.fromParts("1", "").getOrThrow()) }
    @Test fun `zero and blank values do not silently become an activity`() { assertTrue(ActivityDuration.fromParts("", "").isFailure); assertTrue(ActivityDuration.fromParts("0", "0").isFailure) }
    @Test fun `minutes are bounded to a clock style field`() { assertTrue(ActivityDuration.fromParts("1", "60").isFailure); assertTrue(ActivityDuration.fromParts("0", "-1").isFailure) }
    @Test fun `invalid and overflow values are rejected`() { for (hours in listOf("abc", "999999999999", "-1")) assertTrue(ActivityDuration.fromParts(hours, "0").isFailure) }
    @Test fun `existing maximum duration remains supported`() { assertEquals(10080, ActivityDuration.fromParts("168", "0").getOrThrow()); assertTrue(ActivityDuration.fromParts("168", "1").isFailure) }
    @Test fun `friendly formatting covers minutes whole hours and mixed durations`() { assertEquals("30 min", ActivityDuration.format(30)); assertEquals("1 h", ActivityDuration.format(60)); assertEquals("1 h 30 min", ActivityDuration.format(90)) }
}

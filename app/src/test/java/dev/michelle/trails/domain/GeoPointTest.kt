package dev.michelle.trails.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class GeoPointTest {
    @Test fun `zero and boundary coordinates are valid`() {
        assertEquals(GeoPoint(0.0, 0.0), GeoPoint.fromNullable(0.0, 0.0))
        GeoPoint(90.0, 180.0)
        GeoPoint(-90.0, -180.0)
    }

    @Test fun `invalid and non finite latitudes are rejected`() {
        for (latitude in listOf(90.001, -90.001, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { GeoPoint(latitude, 0.0) }
        }
    }

    @Test fun `invalid and non finite longitudes are rejected`() {
        for (longitude in listOf(180.001, -180.001, Double.NaN, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { GeoPoint(0.0, longitude) }
        }
    }

    @Test fun `manual coordinates accept whitespace and negative values`() {
        assertEquals(GeoPoint(-33.86, 151.21), GeoPoint.parse(" -33.86 ", "151.21").getOrThrow())
    }

    @Test fun `malformed manual coordinates return a failure`() {
        for (latitude in listOf("", "abc", "NaN", "91", "1,5")) {
            assertTrue(GeoPoint.parse(latitude, "100").isFailure)
        }
    }

    @Test fun `absent coordinates are represented by a null point`() {
        assertNull(GeoPoint.fromNullable(null, null))
    }

    @Test fun `partial coordinate pairs are rejected instead of silently discarded`() {
        assertThrows(IllegalArgumentException::class.java) { GeoPoint.fromNullable(1.0, null) }
        assertThrows(IllegalArgumentException::class.java) { GeoPoint.fromNullable(null, 100.0) }
    }

    @Test fun `point survives add draft validation and edit with the same id`() {
        var saved: Walk? = null
        val store = object : WalkWriter, WalkFinder, WalkUpdater {
            override fun save(walk: Walk) { saved = walk }
            override fun delete(id: String) { saved = null }
            override fun find(id: String) = saved?.takeIf { it.id == id }
            override fun update(walk: Walk): Boolean { saved = walk; return true }
        }
        val point = GeoPoint(3.139, 101.6869)
        val today = LocalDate.of(2026, 1, 2)
        val entry = AddWalk(store) { "same-id" }.execute("Park", today, 2500, 30, "", point = point)
        val draft = WalkDraft.from(entry)
        assertEquals(point, draft.point)
        val value = ValidateWalkDraft { today }.execute(draft).value!!
        val updated = EditWalk(store, store).execute(entry.id, value)
        assertEquals(entry.id, updated.id)
        assertEquals(point, updated.point)
        val removed = EditWalk(store, store).execute(entry.id, value.copy(point = null))
        assertNull(removed.point)
    }
}

package dev.michelle.trails.backup

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import dev.michelle.trails.domain.ActivityType
import dev.michelle.trails.domain.BackupCodec
import dev.michelle.trails.domain.BackupLimits
import dev.michelle.trails.domain.Effort
import dev.michelle.trails.domain.GeoPoint
import dev.michelle.trails.domain.Walk
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalTime

/** Explicit versioned fields: no reflection, polymorphic deserialization, or unbounded nesting. */
class JsonBackupCodec : BackupCodec {
    override fun encode(entries: List<Walk>): String {
        require(entries.size <= BackupLimits.MAX_ENTRIES) { "Backup has more than 10,000 entries." }
        val output = StringWriter()
        JsonWriter(output).use { writer ->
            writer.setIndent("  ")
            writer.beginObject().name("format").value(FORMAT).name("version").value(2)
            writer.name("entries").beginArray()
            entries.forEach { writeEntry(writer, it) }
            writer.endArray().endObject()
        }
        return output.toString()
    }

    override fun decode(contents: String): List<Walk> {
        require(contents.toByteArray(Charsets.UTF_8).size <= BackupLimits.MAX_BYTES) { "Backup exceeds the 10 MB limit." }
        try {
            JsonReader(StringReader(contents)).use { reader ->
                reader.strictness = Strictness.STRICT
                reader.beginObject()
                val seen = mutableSetOf<String>()
                var format: String? = null
                var version: Int? = null
                var entries: List<Walk>? = null
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    require(seen.add(name)) { "Duplicate backup field: $name" }
                    when (name) {
                        "format" -> format = readText(reader)
                        "version" -> version = readInteger(reader)
                        "entries" -> entries = readEntries(reader)
                        else -> error("Unknown backup field: $name")
                    }
                }
                reader.endObject()
                require(reader.peek() == JsonToken.END_DOCUMENT) { "Backup has trailing data." }
                require(format == FORMAT && version in setOf(1, 2)) { "Unsupported backup format or version." }
                val result = requireNotNull(entries) { "Backup is missing activities." }
                require(result.map { it.id }.distinct().size == result.size) { "Backup contains duplicate activity IDs." }
                return result
            }
        } catch (error: Exception) {
            throw IllegalArgumentException("Could not read this Trail Journal backup: ${error.message}", error)
        }
    }

    private fun readEntries(reader: JsonReader): List<Walk> {
        val entries = mutableListOf<Walk>()
        reader.beginArray()
        while (reader.hasNext()) {
            require(entries.size < BackupLimits.MAX_ENTRIES) { "Backup has more than 10,000 entries." }
            entries += readEntry(reader)
        }
        reader.endArray()
        return entries
    }

    private fun readEntry(reader: JsonReader): Walk {
        val text = mutableMapOf<String, String>()
        val numbers = mutableMapOf<String, Int>()
        var effort: Effort? = null
        var point: GeoPoint? = null
        val seen = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(seen.add(name)) { "Duplicate activity field: $name" }
            when (name) {
                "id", "title", "date", "notes", "type", "location" -> text[name] = readText(reader)
                "startTime", "zoneId" -> if (!readNull(reader)) text[name] = readText(reader)
                "distanceMeters", "durationMinutes" -> numbers[name] = readInteger(reader)
                "effort" -> if (readNull(reader)) effort = null else effort = Effort.valueOf(readText(reader))
                "point" -> point = if (readNull(reader)) null else readPoint(reader)
                else -> error("Unknown activity field: $name")
            }
        }
        reader.endObject()
        require(seen == ENTRY_FIELDS || seen == ENTRY_FIELDS + setOf("startTime", "zoneId")) { "Activity has missing fields." }
        return Walk(
            id = text.getValue("id"),
            title = text.getValue("title"),
            date = LocalDate.parse(text.getValue("date")),
            distanceMeters = numbers.getValue("distanceMeters"),
            durationMinutes = numbers.getValue("durationMinutes"),
            notes = text.getValue("notes"),
            type = ActivityType.valueOf(text.getValue("type")),
            location = text.getValue("location"),
            effort = effort,
            point = point,
            startTime = text["startTime"]?.let(LocalTime::parse),
            zoneId = text["zoneId"],
        )
    }

    private fun readPoint(reader: JsonReader): GeoPoint {
        reader.beginObject()
        val values = mutableMapOf<String, Double>()
        while (reader.hasNext()) {
            val name = reader.nextName()
            require(name in setOf("latitude", "longitude") && name !in values) { "Invalid coordinate fields." }
            require(reader.peek() == JsonToken.NUMBER) { "Coordinate must be a number." }
            values[name] = reader.nextDouble()
        }
        reader.endObject()
        require(values.size == 2) { "Location is missing a coordinate." }
        return GeoPoint(values.getValue("latitude"), values.getValue("longitude"))
    }

    private fun readText(reader: JsonReader): String {
        require(reader.peek() == JsonToken.STRING) { "Expected a text value." }
        return reader.nextString()
    }

    private fun readInteger(reader: JsonReader): Int {
        require(reader.peek() == JsonToken.NUMBER) { "Expected a whole number." }
        val value = reader.nextString()
        require(value.matches(Regex("-?[0-9]+"))) { "Expected a whole number." }
        return value.toInt()
    }

    private fun readNull(reader: JsonReader): Boolean {
        if (reader.peek() != JsonToken.NULL) return false
        reader.nextNull()
        return true
    }

    private fun writeEntry(writer: JsonWriter, walk: Walk) {
        writer.beginObject()
        writer.name("id").value(walk.id)
        writer.name("title").value(walk.title)
        writer.name("date").value(walk.date.toString())
        writer.name("distanceMeters").value(walk.distanceMeters)
        writer.name("durationMinutes").value(walk.durationMinutes)
        writer.name("notes").value(walk.notes)
        writer.name("startTime").value(walk.startTime?.toString())
        writer.name("zoneId").value(walk.zoneId)
        writer.name("type").value(walk.type.name)
        writer.name("location").value(walk.location)
        writer.name("effort").value(walk.effort?.name)
        writer.name("point")
        if (walk.point == null) {
            writer.nullValue()
        } else {
            val point = checkNotNull(walk.point)
            writer.beginObject().name("latitude").value(point.latitude).name("longitude").value(point.longitude).endObject()
        }
        writer.endObject()
    }

    private companion object {
        const val FORMAT = "trail-journal"
        val ENTRY_FIELDS = setOf("id", "title", "date", "distanceMeters", "durationMinutes", "notes", "type", "location", "effort", "point")
    }
}

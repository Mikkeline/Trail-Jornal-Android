package dev.michelle.trails.domain

/** WGS84 coordinates, with no map-provider or Android dependency. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
    }

    companion object {
        fun parse(latitude: String, longitude: String): Result<GeoPoint> {
            return runCatching {
                GeoPoint(latitude.trim().toDouble(), longitude.trim().toDouble())
            }
        }

        fun fromNullable(latitude: Double?, longitude: Double?): GeoPoint? {
            if (latitude == null && longitude == null) {
                return null
            }
            // Partial coordinate pairs indicate corrupt storage, not an absent location.
            require(latitude != null && longitude != null) { "Location has an incomplete coordinate pair." }
            return GeoPoint(latitude, longitude)
        }
    }
}

# Room schema exports

KSP writes the version-3 Room schema JSON here during the first successful build. No generated identity hash has been fabricated in this source package. After building, commit the generated JSON and retain older versions when adding migrations.

The migration test creates the actual pre-Room version-1 database using its original SQLiteOpenHelper schema. Opening it through the current Room database exercises the migration and Room's own schema validation, without requiring a fictional version-1 Room schema export.

Version 3 adds optional latitude/longitude columns. The version-2 fixture test verifies the separate 2→3 path; retain any existing schema exports.

The previously generated version-2/version-3 exports have been preserved under dev.michelle.trails.data.TrailDatabase after the source-package move. The JSON content and recorded identity hashes were not edited. Future KSP exports use the app module schema location.

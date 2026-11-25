"""Check the exact migration SQL with desktop SQLite; not a Room/device test."""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/dev/michelle/trails/data/TrailDatabase.kt'
LEGACY_SCHEMA = ('CREATE TABLE walks(id TEXT PRIMARY KEY, title TEXT NOT NULL, '
                 'date TEXT NOT NULL, meters INTEGER NOT NULL, minutes INTEGER NOT NULL, notes TEXT NOT NULL)')


def migration_statements():
    source = SOURCE.read_text()
    constants = dict(re.findall(r'internal const val (\w+) = """(.*?)"""', source, re.S))
    operations = re.findall(r'db\.execSQL\((CREATE_VERSION_2|COPY_VERSION_1|"[^"]*")\)', source)
    if len(operations) != 4:
        raise AssertionError('Migration changed; review the SQL verifier before using it.')
    return [constants[token] if token in constants else token[1:-1] for token in operations]


class LegacyMigrationSqlTest(unittest.TestCase):
    def setUp(self):
        self.database = sqlite3.connect(':memory:')
        self.database.execute(LEGACY_SCHEMA)
        self.original = ('legacy-id', "Morning's walk", '2026-01-02', 2500, 30, '保留我的 notes')
        self.database.execute('INSERT INTO walks VALUES (?, ?, ?, ?, ?, ?)', self.original)
        self.database.commit()

    def tearDown(self):
        self.database.close()

    def migrate(self):
        with self.database:
            self.database.execute("BEGIN")
            for sql in migration_statements():
                self.database.execute(sql)

    def test_preserves_all_existing_fields_and_adds_defaults(self):
        self.migrate()
        row = self.database.execute('SELECT * FROM walks').fetchone()
        self.assertEqual(self.original + ('OUTDOOR_WALK', '', None), row)
        info = {row[1]: row for row in self.database.execute('PRAGMA table_info(walks)')}
        self.assertEqual(1, info['id'][3])
        self.assertEqual(1, info['id'][5])
        self.assertEqual(9, len(info))

    def test_existing_id_can_be_edited_without_adding_a_row(self):
        self.migrate()
        self.database.execute("UPDATE walks SET type = 'HIKE', location = 'Hill', effort = 'MODERATE' WHERE id = ?", ('legacy-id',))
        self.assertEqual(1, self.database.execute('SELECT COUNT(*) FROM walks').fetchone()[0])
        self.assertEqual(('HIKE', 'Hill', 'MODERATE'), self.database.execute('SELECT type, location, effort FROM walks').fetchone())
        with self.assertRaises(sqlite3.IntegrityError):
            self.database.execute('INSERT INTO walks SELECT * FROM walks')

    def test_invalid_legacy_id_aborts_without_deleting_original_data(self):
        # SQLite's old TEXT PRIMARY KEY permits NULL; the new Room entity does not.
        self.database.execute("INSERT INTO walks VALUES (NULL, 'Corrupt entry', '2026-01-02', 100, 1, '')")
        self.database.commit()
        with self.assertRaises(sqlite3.IntegrityError):
            self.migrate()
        self.assertEqual(2, self.database.execute('SELECT COUNT(*) FROM walks').fetchone()[0])
        self.assertEqual(6, len(list(self.database.execute('PRAGMA table_info(walks)'))))
        self.assertEqual(0, self.database.execute("SELECT COUNT(*) FROM sqlite_master WHERE name = 'walks_new'").fetchone()[0])



class MapMigrationSqlTest(unittest.TestCase):
    def setUp(self):
        self.database = sqlite3.connect(':memory:')
        source = SOURCE.read_text()
        self.constants = dict(re.findall(r'internal const val (ADD_\w+) = "([^"]+)"', source))
        self.database.execute(LEGACY_SCHEMA)
        self.database.execute("INSERT INTO walks VALUES ('old-id', 'Walk', '2026-01-02', 1000, 20, 'Notes')")
        self.database.commit()
        with self.database:
            self.database.execute('BEGIN')
            for statement in migration_statements():
                self.database.execute(statement)

    def tearDown(self):
        self.database.close()

    def upgrade(self):
        with self.database:
            self.database.execute('BEGIN')
            self.database.execute(self.constants['ADD_LATITUDE'])
            self.database.execute(self.constants['ADD_LONGITUDE'])

    def test_full_legacy_chain_keeps_rows_and_defaults_to_no_pin(self):
        self.upgrade()
        self.assertEqual(('old-id', None, None), self.database.execute('SELECT id, latitude, longitude FROM walks').fetchone())
        self.assertEqual(11, len(list(self.database.execute('PRAGMA table_info(walks)'))))

    def test_pin_can_be_saved_and_cleared_including_zero_coordinates(self):
        self.upgrade()
        self.database.execute('UPDATE walks SET latitude = 0.0, longitude = 0.0')
        self.assertEqual((0.0, 0.0), self.database.execute('SELECT latitude, longitude FROM walks').fetchone())
        self.database.execute('UPDATE walks SET latitude = NULL, longitude = NULL')
        self.assertEqual((None, None), self.database.execute('SELECT latitude, longitude FROM walks').fetchone())
        self.assertEqual(1, self.database.execute('SELECT COUNT(*) FROM walks').fetchone()[0])

    def test_version_two_activity_metadata_survives_upgrade(self):
        self.database.execute("UPDATE walks SET type = 'HIKE', location = 'Hill', effort = 'HARD'")
        self.database.commit()
        self.upgrade()
        self.assertEqual(('HIKE', 'Hill', 'HARD'), self.database.execute('SELECT type, location, effort FROM walks').fetchone())


if __name__ == '__main__':
    unittest.main(verbosity=2)

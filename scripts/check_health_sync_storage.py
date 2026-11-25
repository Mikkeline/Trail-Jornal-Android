"""Exercise actual migration and acknowledgement SQL with SQLite; not a Room runtime test."""
from pathlib import Path
import json
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/dev/michelle/trails/data'
CONSTANTS = dict(re.findall(r'internal const val (\w+) = "([^"]+)"', (SOURCE / 'TrailDatabase.kt').read_text()))
DAO = (SOURCE / 'HealthSyncDao.kt').read_text()
COMPLETE = re.search(r'@Query\("(UPDATE health_sync SET state = :state, lastSyncedAt[^\"]+)"\)', DAO).group(1)
FAIL = re.search(r'@Query\("(UPDATE health_sync SET state = :state, error[^\"]+)"\)', DAO).group(1)


class HealthStorageTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        schema = json.loads((ROOT / 'app/schemas/dev.michelle.trails.data.TrailDatabase/3.json').read_text())['database']
        for entity in schema['entities']:
            self.db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        self.db.execute("INSERT INTO walks(id,title,date,meters,minutes,notes,type,location) VALUES ('one','Park','2026-01-02',2000,30,'Private','OUTDOOR_WALK','Home')")
        self.db.commit()
        with self.db:
            for key in ['ADD_START_TIME', 'ADD_ZONE_ID', 'CREATE_HEALTH_SYNC', 'CREATE_HEALTH_SETTINGS']:
                self.db.execute(CONSTANTS[key])

    def tearDown(self):
        self.db.close()

    def job(self, revision=1):
        self.db.execute("INSERT OR REPLACE INTO health_sync VALUES ('one','UPSERT',?,100,1,0,'PENDING',0,NULL,NULL)", (revision,))

    def test_version_three_data_survives_without_invented_start_time(self):
        self.assertEqual(('Park', 2000, 30, 'Private', 'Home', None, None), self.db.execute('SELECT title,meters,minutes,notes,location,startTime,zoneId FROM walks').fetchone())
        self.assertEqual(13, len(list(self.db.execute('PRAGMA table_info(walks)'))))

    def test_new_activity_and_sync_job_can_commit_together(self):
        with self.db:
            self.db.execute("UPDATE walks SET startTime='09:00', zoneId='Asia/Kuala_Lumpur'")
            self.job()
        self.assertEqual(1, self.db.execute('SELECT count(*) FROM health_sync').fetchone()[0])

    def test_queue_failure_rolls_back_the_activity_change(self):
        self.db.execute("CREATE TRIGGER reject_job BEFORE INSERT ON health_sync BEGIN SELECT RAISE(ABORT, 'Queue failed'); END")
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                self.db.execute("UPDATE walks SET meters=4000")
                self.job()
        self.assertEqual(2000, self.db.execute('SELECT meters FROM walks').fetchone()[0])

    def test_stale_success_cannot_acknowledge_a_newer_edit(self):
        self.job(2)
        self.db.execute(COMPLETE, dict(id='one', revision=1, state='SYNCED', at=200))
        self.assertEqual(('PENDING', None), self.db.execute('SELECT state,lastSyncedAt FROM health_sync').fetchone())
        self.db.execute(COMPLETE, dict(id='one', revision=2, state='SYNCED', at=201))
        self.assertEqual(('SYNCED', 201), self.db.execute('SELECT state,lastSyncedAt FROM health_sync').fetchone())

    def test_stale_failure_cannot_overwrite_a_newer_undo(self):
        self.job(3)
        self.db.execute(FAIL, dict(id='one', revision=2, state='FAILED', error='Old failure', retryAt=500))
        self.assertEqual(('PENDING', 0, None), self.db.execute('SELECT state,attempts,error FROM health_sync').fetchone())

    def test_failed_current_revision_retains_retry_state(self):
        self.job()
        self.db.execute(FAIL, dict(id='one', revision=1, state='FAILED', error='Offline', retryAt=30000))
        self.assertEqual(('FAILED', 1, 30000, 'Offline'), self.db.execute('SELECT state,attempts,notBefore,error FROM health_sync').fetchone())


if __name__ == '__main__':
    unittest.main(verbosity=2)

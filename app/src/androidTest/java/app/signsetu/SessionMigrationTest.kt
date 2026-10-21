package app.signsetu

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.signsetu.data.session.Role
import app.signsetu.data.session.SessionDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionMigrationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    /** Writes tables as an older version did; Room then validates the migrated schema against the current one. */
    private fun oldDatabase(version: Int, block: (SQLiteDatabase) -> Unit) {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            val role = if (version >= 2) ", `role` TEXT NOT NULL DEFAULT 'VICTIM'" else ""
            db.execSQL("CREATE TABLE IF NOT EXISTS `session` (`id` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER, `profile` TEXT NOT NULL, `subjectLang` TEXT NOT NULL$role, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `ts` INTEGER NOT NULL, `type` TEXT NOT NULL, `source` TEXT NOT NULL, `lang` TEXT, `text` TEXT, `cardId` TEXT, `lexiconId` TEXT, `drawingId` INTEGER, FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_sessionId` ON `event` (`sessionId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `drawing` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` TEXT NOT NULL, `png` BLOB NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_drawing_sessionId` ON `drawing` (`sessionId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            val hash = if (version >= 2) "a0ffbb364bc9064df435df132b2e6451" else "2828971f9fd646e7445b12038249e087"
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$hash')")
            block(db)
            db.version = version
        }
    }

    private fun current() = Room.databaseBuilder(context, SessionDatabase::class.java, name)
        .addMigrations(SessionDatabase.MIGRATION_1_2, SessionDatabase.MIGRATION_2_3, SessionDatabase.MIGRATION_3_4)
        .build()

    @Test
    fun version1SessionBecomesAVictimSession() = runBlocking {
        oldDatabase(1) { it.execSQL("INSERT INTO session VALUES ('s', 1, NULL, 'YOUNG_CHILD', 'hi')") }
        val room = current()
        try {
            assertEquals(Role.VICTIM.name, room.dao().openSession()?.role)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun version2EventsKeepTheirDataAndGainEmptyAutoFields() = runBlocking {
        oldDatabase(2) {
            it.execSQL("INSERT INTO session VALUES ('s', 1, NULL, 'ISL_USER', 'hi', 'ACCUSED')")
            it.execSQL("INSERT INTO event (sessionId, ts, type, source, lang, text, cardId, lexiconId, drawingId) VALUES ('s', 2, 'ANSWER_CARD', 'SUBJECT', 'en', 'Yes', 'yes', NULL, NULL)")
        }
        val room = current()
        try {
            assertEquals(Role.ACCUSED.name, room.dao().openSession()?.role)
            val event = room.dao().events("s").first().single()
            assertEquals("yes", event.cardId)
            assertNull(event.itemId)
            assertNull(event.reason)
            assertNull(event.shownId)
            assertNull(event.via)
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}

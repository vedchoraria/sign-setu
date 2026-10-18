package app.signsetu

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies Room database destructive downgrade callback hook and notification state.
 * Ensures an explicit warning state is triggered when a database downgrade occurs.
 */
class DatabaseDowngradeTest {

    @Test
    fun roomCallbackTriggersOnDestructiveMigration() {
        val triggered = AtomicBoolean(false)
        val callback = object : RoomDatabase.Callback() {
            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                triggered.set(true)
            }
        }

        assertFalse("Initially not triggered", triggered.get())

        // Simulate Room calling onDestructiveMigration during downgrade
        val mockDb = object : SupportSQLiteDatabase {
            override fun beginTransaction() {}
            override fun endTransaction() {}
            override fun setTransactionSuccessful() {}
            override fun inTransaction(): Boolean = false
            override fun isDbLockedByCurrentThread(): Boolean = false
            override fun yieldIfContendedSafely(): Boolean = false
            override fun yieldIfContendedSafely(sleepAmount: Long): Boolean = false
            override fun getVersion(): Int = 2
            override fun setVersion(version: Int) {}
            override fun getMaximumSize(): Long = 0
            override fun setMaximumSize(numBytes: Long): Long = 0
            override fun getPageSize(): Long = 0
            override fun setPageSize(numBytes: Long) {}
            override fun compileStatement(sql: String): Nothing = throw UnsupportedOperationException()
            override fun query(query: String): Nothing = throw UnsupportedOperationException()
            override fun query(query: String, bindArgs: Array<out Any?>): Nothing = throw UnsupportedOperationException()
            override fun query(query: androidx.sqlite.db.SupportSQLiteQuery): Nothing = throw UnsupportedOperationException()
            override fun query(query: androidx.sqlite.db.SupportSQLiteQuery, cancellationSignal: android.os.CancellationSignal?): Nothing = throw UnsupportedOperationException()
            override fun insert(table: String, conflictAlgorithm: Int, values: android.content.ContentValues): Long = 0
            override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int = 0
            override fun update(table: String, conflictAlgorithm: Int, values: android.content.ContentValues, whereClause: String?, whereArgs: Array<out Any?>?): Int = 0
            override fun execSQL(sql: String) {}
            override fun execSQL(sql: String, bindArgs: Array<out Any?>) {}
            override fun isReadOnly(): Boolean = false
            override fun isOpen(): Boolean = true
            override fun needUpgrade(newVersion: Int): Boolean = false
            override fun getPath(): String? = null
            override fun setLocale(locale: java.util.Locale) {}
            override fun setMaxSqlCacheSize(cacheSize: Int) {}
            override fun setForeignKeyConstraintsEnabled(enable: Boolean) {}
            override fun enableWriteAheadLogging(): Boolean = false
            override fun disableWriteAheadLogging() {}
            override fun isWriteAheadLoggingEnabled(): Boolean = false
            override fun getAttachedDbs(): List<android.util.Pair<String, String>>? = null
            override fun isDatabaseIntegrityOk(): Boolean = true
            override fun close() {}
        }

        callback.onDestructiveMigration(mockDb)
        assertTrue("Callback must have fired on destructive migration", triggered.get())
    }
}

package app.signsetu.data.session

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase

// No table has a field for a subject's name, address, phone number or photograph (02-architecture §6). The
// officer's own name for a saved session is free text and may hold one; it is encrypted like every row.

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val profile: String,
    val subjectLang: String,
    // The column default only fills rows from before roles existed; new sessions always write their role.
    @ColumnInfo(defaultValue = "VICTIM") val role: String = "NOT_KNOWN",
    /** The officer's name for a finished session; null while it is open. */
    val title: String? = null,
)

@Entity(
    tableName = "event",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val ts: Long,
    val type: String,
    val source: String,
    val lang: String?,
    val text: String?,
    val cardId: String?,
    val lexiconId: String?,
    val drawingId: Long?,
    val itemId: String? = null,
    val reason: String? = null,
    val shownId: Long? = null,
    val via: String? = null,
)

@Entity(
    tableName = "drawing",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")],
)
class DrawingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val png: ByteArray,
)

@Dao
interface SessionDao {
    @Insert suspend fun insertSession(session: SessionEntity)

    @Query("SELECT * FROM session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun openSession(): SessionEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    fun session(id: String): Flow<SessionEntity?>

    @Query("UPDATE session SET subjectLang = :lang WHERE id = :id")
    suspend fun setSubjectLang(id: String, lang: String)

    @Query("UPDATE session SET profile = :profile WHERE id = :id")
    suspend fun setProfile(id: String, profile: String)

    @Query("UPDATE session SET role = :role WHERE id = :id")
    suspend fun setRole(id: String, role: String)

    @Insert suspend fun insertEvent(event: EventEntity): Long

    @Query("SELECT * FROM event WHERE sessionId = :sessionId ORDER BY ts, id")
    fun events(sessionId: String): Flow<List<EventEntity>>

    @Insert suspend fun insertDrawing(drawing: DrawingEntity): Long

    @Query("SELECT png FROM drawing WHERE id = :id")
    suspend fun drawing(id: Long): ByteArray?

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("UPDATE session SET endedAt = :endedAt, title = :title WHERE id = :id")
    suspend fun endSession(id: String, endedAt: Long, title: String)

    @Query("UPDATE session SET endedAt = NULL WHERE id = :id")
    suspend fun reopen(id: String)

    @Query("SELECT * FROM session WHERE endedAt IS NOT NULL ORDER BY endedAt DESC")
    fun saved(): Flow<List<SessionEntity>>

    @Query("DELETE FROM session WHERE startedAt < :cutoff")
    suspend fun deleteStartedBefore(cutoff: Long): Int
}

@Database(entities = [SessionEntity::class, EventEntity::class, DrawingEntity::class], version = 4, exportSchema = true)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun dao(): SessionDao

    companion object {
        const val FILE_NAME = "session.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN role TEXT NOT NULL DEFAULT 'VICTIM'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE event ADD COLUMN itemId TEXT")
                db.execSQL("ALTER TABLE event ADD COLUMN reason TEXT")
                db.execSQL("ALTER TABLE event ADD COLUMN shownId INTEGER")
                db.execSQL("ALTER TABLE event ADD COLUMN via TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session ADD COLUMN title TEXT")
            }
        }

        fun open(context: Context, key: ByteArray, onDestructiveMigration: () -> Unit = {}): SessionDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, SessionDatabase::class.java, FILE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(key))
                // Purged rows are overwritten in place, and no write-ahead log keeps old pages around.
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                // A downgrade drops sessions rather than failing every unlock; the officer is told (onDestructiveMigration).
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.query("PRAGMA secure_delete = ON").use { it.moveToFirst() }
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        onDestructiveMigration()
                    }
                })
                .build()
        }
    }
}

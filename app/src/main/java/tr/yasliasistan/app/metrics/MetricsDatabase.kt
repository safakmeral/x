package tr.yasliasistan.app.metrics

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import tr.yasliasistan.core.decision.DecisionSource
import tr.yasliasistan.core.metrics.ErrorCode
import tr.yasliasistan.core.metrics.MetricKind
import tr.yasliasistan.core.metrics.MetricOutcome
import tr.yasliasistan.core.metrics.MetricRecord
import tr.yasliasistan.core.metrics.MetricRow

/**
 * One stored metric (PLAN G-18). Only numbers and enums (stored by name), never text from the user,
 * contacts or the screen; MetricEntityTypeTest enforces this. [session] is an anonymous running number.
 */
@Entity(tableName = "metric", indices = [Index("atMs")])
data class MetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session: Int,
    val appVersion: Int,
    val kind: MetricKind,
    val atMs: Long,
    val durationMs: Long?,
    val source: DecisionSource?,
    val outcome: MetricOutcome?,
    val errorCode: ErrorCode?,
    val count: Int?,
) {
    fun toRow() = MetricRow(session, appVersion, MetricRecord(kind, atMs, durationMs, source, outcome, errorCode, count))

    companion object {
        fun of(session: Int, appVersion: Int, r: MetricRecord) =
            MetricEntity(0, session, appVersion, r.kind, r.atMs, r.durationMs, r.source, r.outcome, r.errorCode, r.count)
    }
}

@Dao
interface MetricDao {
    @Insert
    fun insert(entity: MetricEntity)

    @Query("SELECT MAX(session) FROM metric")
    fun maxSession(): Int?

    @Query("DELETE FROM metric WHERE atMs < :cutoffMs")
    fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM metric")
    fun deleteAll(): Int

    @Query("SELECT * FROM metric ORDER BY id")
    fun all(): List<MetricEntity>
}

@Database(entities = [MetricEntity::class], version = 1, exportSchema = false)
abstract class MetricsDatabase : RoomDatabase() {
    abstract fun metrics(): MetricDao

    companion object {
        /** App-private database file; backups and device transfer exclude it (data_extraction_rules.xml). */
        const val FILE_NAME = "metrics.db"

        fun open(context: Context): MetricsDatabase =
            Room.databaseBuilder(context.applicationContext, MetricsDatabase::class.java, FILE_NAME).build()
    }
}

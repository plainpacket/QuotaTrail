package app.quotatrail.storage.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import app.quotatrail.storage.local.dao.AlertStateDao
import app.quotatrail.storage.local.dao.ProviderAccountDao
import app.quotatrail.storage.local.dao.QuotaSnapshotDao
import app.quotatrail.storage.local.dao.RefreshAttemptDao
import app.quotatrail.storage.local.entity.AlertStateEntity
import app.quotatrail.storage.local.entity.ProviderAccountEntity
import app.quotatrail.storage.local.entity.QuotaSnapshotEntity
import app.quotatrail.storage.local.entity.RefreshAttemptEntity

const val QUOTA_TRAIL_DATABASE_SCHEMA_VERSION = 1

@Database(
    entities = [
        ProviderAccountEntity::class,
        QuotaSnapshotEntity::class,
        RefreshAttemptEntity::class,
        AlertStateEntity::class,
    ],
    version = QUOTA_TRAIL_DATABASE_SCHEMA_VERSION,
    exportSchema = true,
)
abstract class QuotaTrailDatabase : RoomDatabase() {
    abstract fun providerAccountDao(): ProviderAccountDao

    abstract fun quotaSnapshotDao(): QuotaSnapshotDao

    abstract fun refreshAttemptDao(): RefreshAttemptDao

    abstract fun alertStateDao(): AlertStateDao

    companion object {
        const val DATABASE_NAME = "quotatrail.db"
    }
}

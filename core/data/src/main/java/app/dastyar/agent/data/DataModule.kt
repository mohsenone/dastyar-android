package app.dastyar.agent.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DastyarDatabase =
        Room.databaseBuilder(context, DastyarDatabase::class.java, "dastyar.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN providerId TEXT")
            db.execSQL("ALTER TABLE conversations ADD COLUMN modelOverride TEXT")
            db.execSQL("ALTER TABLE routines ADD COLUMN triggerKind TEXT NOT NULL DEFAULT 'schedule'")
            db.execSQL("ALTER TABLE routines ADD COLUMN triggerFilter TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `model_cache` (
                    `providerId` TEXT NOT NULL,
                    `modelId` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `free` INTEGER NOT NULL,
                    `promptPrice` TEXT,
                    `completionPrice` TEXT,
                    `contextLength` INTEGER,
                    `vision` INTEGER,
                    `tools` INTEGER,
                    `note` TEXT,
                    `fetchedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`providerId`, `modelId`)
                )
                """.trimIndent(),
            )
        }
    }
}

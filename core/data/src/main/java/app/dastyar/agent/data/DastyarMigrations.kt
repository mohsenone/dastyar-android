package app.dastyar.agent.data

/**
 * Room upgrade steps for `dastyar.db`.
 * Versions 1, 2, and 3 have an explicit path. Anything else returns null
 * so a caller cannot pretend a destructive wipe is a migration.
 */
object DastyarMigrations {
    const val VERSION = 3

    val SQL_1_2 = listOf(
        "ALTER TABLE conversations ADD COLUMN providerId TEXT",
        "ALTER TABLE conversations ADD COLUMN modelOverride TEXT",
        "ALTER TABLE routines ADD COLUMN triggerKind TEXT NOT NULL DEFAULT 'schedule'",
        "ALTER TABLE routines ADD COLUMN triggerFilter TEXT NOT NULL DEFAULT ''",
    )

    val SQL_2_3 = listOf(
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

    /** Null when this version pair must not be migrated by wiping or by guessing. */
    fun upgradeSql(from: Int, to: Int = VERSION): List<String>? = when {
        from == to -> emptyList()
        from == 1 && to == 2 -> SQL_1_2
        from == 2 && to == 3 -> SQL_2_3
        from == 1 && to == 3 -> SQL_1_2 + SQL_2_3
        else -> null
    }
}

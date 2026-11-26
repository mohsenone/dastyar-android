package app.dastyar.agent.data

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DastyarMigrationsTest {
    @Test
    fun knownVersionsHaveAPathAndUnknownVersionsDoNot() {
        assertEquals(3, DastyarMigrations.VERSION)
        assertEquals(emptyList<String>(), DastyarMigrations.upgradeSql(3, 3))
        assertEquals(DastyarMigrations.SQL_2_3, DastyarMigrations.upgradeSql(2, 3))
        assertNull(DastyarMigrations.upgradeSql(4, 3))
        assertNull(DastyarMigrations.upgradeSql(0, 3))
    }

    @Test
    fun version3DataIsLeftInPlace() {
        mem { db ->
            db.exec(
                """
                CREATE TABLE conversations (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    pinned INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    providerId TEXT,
                    modelOverride TEXT
                )
                """.trimIndent(),
            )
            db.exec(
                """
                CREATE TABLE model_cache (
                    providerId TEXT NOT NULL,
                    modelId TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    free INTEGER NOT NULL,
                    promptPrice TEXT,
                    completionPrice TEXT,
                    contextLength INTEGER,
                    vision INTEGER,
                    tools INTEGER,
                    note TEXT,
                    fetchedAt INTEGER NOT NULL,
                    PRIMARY KEY(providerId, modelId)
                )
                """.trimIndent(),
            )
            db.exec("INSERT INTO conversations (id, title, pinned, createdAt, updatedAt, providerId, modelOverride) VALUES ('c1', 'Keep me', 1, 1, 2, 'p', 'm')")
            db.exec("INSERT INTO model_cache (providerId, modelId, displayName, free, fetchedAt) VALUES ('p', 'free-model', 'Free', 1, 9)")
            DastyarMigrations.upgradeSql(3, 3).orEmpty().forEach(db::exec)
            assertNull(DastyarMigrations.upgradeSql(4, 3))
            assertEquals("Keep me", db.text("SELECT title FROM conversations WHERE id = 'c1'"))
            assertEquals("free-model", db.text("SELECT modelId FROM model_cache WHERE providerId = 'p'"))
        }
    }

    @Test
    fun upgradeFromVersion1KeepsChatsAndAddsTheLaterColumns() {
        val steps = DastyarMigrations.upgradeSql(1, 3)
        assertEquals(DastyarMigrations.SQL_1_2 + DastyarMigrations.SQL_2_3, steps)
        mem { db ->
            db.exec(
                """
                CREATE TABLE conversations (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    pinned INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.exec(
                """
                CREATE TABLE routines (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    hour INTEGER NOT NULL,
                    minute INTEGER NOT NULL,
                    repeatDaily INTEGER NOT NULL,
                    enabled INTEGER NOT NULL,
                    allowUnattended INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.exec("CREATE TABLE memories (id TEXT NOT NULL PRIMARY KEY, fact TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            db.exec("INSERT INTO conversations (id, title, pinned, createdAt, updatedAt) VALUES ('c1', 'From 0.2', 0, 10, 11)")
            db.exec("INSERT INTO routines (id, title, prompt, hour, minute, repeatDaily, enabled, allowUnattended, createdAt) VALUES ('r1', 'Morning', 'hi', 8, 0, 1, 1, 0, 12)")
            db.exec("INSERT INTO memories (id, fact, createdAt) VALUES ('m1', 'likes tea', 13)")
            steps!!.forEach(db::exec)
            assertEquals("From 0.2", db.text("SELECT title FROM conversations WHERE id = 'c1'"))
            assertNull(db.text("SELECT providerId FROM conversations WHERE id = 'c1'"))
            assertEquals("schedule", db.text("SELECT triggerKind FROM routines WHERE id = 'r1'"))
            assertEquals("", db.text("SELECT triggerFilter FROM routines WHERE id = 'r1'"))
            assertEquals("likes tea", db.text("SELECT fact FROM memories WHERE id = 'm1'"))
            db.exec(
                """
                INSERT INTO model_cache (
                    providerId, modelId, displayName, free, promptPrice, completionPrice,
                    contextLength, vision, tools, note, fetchedAt
                ) VALUES ('p', 'm', 'Name', 1, NULL, NULL, NULL, NULL, NULL, NULL, 1)
                """.trimIndent(),
            )
            assertEquals("Name", db.text("SELECT displayName FROM model_cache WHERE modelId = 'm'"))
        }
    }

    private class Db(private val raw: java.sql.Connection) {
        fun exec(sql: String) {
            raw.createStatement().use { it.execute(sql) }
        }

        fun text(sql: String): String? = raw.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows -> if (rows.next()) rows.getString(1) else null }
        }
    }

    private fun mem(block: (Db) -> Unit) {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { block(Db(it)) }
    }
}

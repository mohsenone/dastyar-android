package app.dastyar.agent.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String? = null,
    val modelOverride: String? = null,
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId"), Index("createdAt")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val text: String,
    val toolCallsJson: String?,
    val toolCallId: String?,
    val toolName: String?,
    val imagePathsJson: String?,
    val createdAt: Long,
    val isError: Boolean,
)

@Entity(tableName = "audit", indices = [Index("createdAt"), Index("toolName")])
data class AuditEntity(
    @PrimaryKey val id: String,
    val conversationId: String?,
    val toolName: String,
    val argsSummary: String,
    val resultSummary: String,
    val risk: String,
    val success: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val fact: String,
    val createdAt: Long,
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    val hour: Int,
    val minute: Int,
    val repeatDaily: Boolean,
    val enabled: Boolean,
    val allowUnattended: Boolean,
    val createdAt: Long,
    /** schedule, sms, or notification. */
    val triggerKind: String = "schedule",
    val triggerFilter: String = "",
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val label: String,
    val model: String,
    val baseUrl: String,
    val isDefault: Boolean,
    val createdAt: Long,
)

data class ProviderProfile(
    val id: String,
    val kind: String,
    val label: String,
    val model: String,
    val baseUrl: String,
    val isDefault: Boolean,
    val apiKey: String,
    val accountId: String,
    val extraHeaders: Map<String, String>,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT DISTINCT c.* FROM conversations c
        LEFT JOIN messages m ON m.conversationId = c.id
        WHERE c.title LIKE '%' || :q || '%' OR m.text LIKE '%' || :q || '%'
        ORDER BY c.pinned DESC, c.updatedAt DESC
        """,
    )
    fun search(q: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, now: Long)

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun pin(id: String, pinned: Boolean)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC")
    fun observe(id: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC")
    suspend fun list(id: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteForConversation(id: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AuditEntity>>

    @Query(
        """
        SELECT * FROM audit
        WHERE (:tool = '' OR toolName LIKE '%' || :tool || '%')
          AND (:success IS NULL OR success = :success)
        ORDER BY createdAt DESC
        """,
    )
    fun observeFiltered(tool: String, success: Boolean?): Flow<List<AuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AuditEntity)

    @Query("SELECT * FROM audit ORDER BY createdAt DESC")
    suspend fun list(): List<AuditEntity>

    @Query("DELETE FROM audit")
    suspend fun deleteAll()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observe(): Flow<List<MemoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY hour, minute")
    fun observe(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines")
    suspend fun list(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun get(id: String): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY createdAt ASC")
    fun observe(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY createdAt ASC")
    suspend fun list(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: String): ProviderEntity?

    @Query("SELECT * FROM providers WHERE isDefault = 1 LIMIT 1")
    suspend fun defaultProvider(): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderEntity)

    @Query("UPDATE providers SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE providers SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: String)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE providers SET model = :model WHERE id = :id")
    suspend fun updateModel(id: String, model: String)
}

@Entity(tableName = "model_cache", primaryKeys = ["providerId", "modelId"])
data class ModelCacheEntity(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val free: Boolean,
    val promptPrice: String?,
    val completionPrice: String?,
    val contextLength: Int?,
    /** null unknown, 0 no, 1 yes. */
    val vision: Int?,
    /** null unknown, 0 no, 1 yes. */
    val tools: Int?,
    val note: String?,
    val fetchedAt: Long,
)

@Dao
abstract class ModelCacheDao {
    @Query("SELECT * FROM model_cache WHERE providerId = :providerId")
    abstract suspend fun list(providerId: String): List<ModelCacheEntity>

    @Query("DELETE FROM model_cache WHERE providerId = :providerId")
    abstract suspend fun clear(providerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(rows: List<ModelCacheEntity>)

    @Transaction
    open suspend fun replace(providerId: String, rows: List<ModelCacheEntity>) {
        clear(providerId)
        if (rows.isNotEmpty()) upsertAll(rows)
    }
}

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AuditEntity::class,
        MemoryEntity::class,
        RoutineEntity::class,
        ProviderEntity::class,
        ModelCacheEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class DastyarDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao
    abstract fun audit(): AuditDao
    abstract fun memories(): MemoryDao
    abstract fun routines(): RoutineDao
    abstract fun providers(): ProviderDao
    abstract fun modelCache(): ModelCacheDao
}

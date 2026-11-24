package app.dastyar.agent.data

import app.dastyar.agent.security.SecretVault
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

@Singleton
class ChatRepository @Inject constructor(
    private val db: DastyarDatabase,
) {
    fun observeConversations(): Flow<List<ConversationEntity>> = db.conversations().observeAll()

    fun search(query: String): Flow<List<ConversationEntity>> =
        if (query.isBlank()) db.conversations().observeAll() else db.conversations().search(query.trim())

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        db.messages().observe(conversationId)

    suspend fun messages(conversationId: String): List<MessageEntity> =
        db.messages().list(conversationId)

    suspend fun createConversation(title: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.conversations().upsert(
            ConversationEntity(id = id, title = title.ifBlank { "…" }, pinned = false, createdAt = now, updatedAt = now),
        )
        return id
    }

    suspend fun touch(conversationId: String, titleIfEmpty: String? = null) {
        val existing = db.conversations().get(conversationId) ?: return
        val title = if (existing.title == "…" && !titleIfEmpty.isNullOrBlank()) {
            titleIfEmpty.take(48)
        } else {
            existing.title
        }
        db.conversations().upsert(existing.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun rename(id: String, title: String) {
        db.conversations().rename(id, title.trim().ifBlank { "…" }, System.currentTimeMillis())
    }

    suspend fun pin(id: String, pinned: Boolean) = db.conversations().pin(id, pinned)

    suspend fun deleteConversation(id: String) {
        db.messages().deleteForConversation(id)
        db.conversations().delete(id)
    }

    suspend fun deleteAll() {
        db.messages().deleteAll()
        db.conversations().deleteAll()
    }

    suspend fun append(message: MessageEntity) {
        db.messages().insert(message)
        db.conversations().get(message.conversationId)?.let { conv ->
            db.conversations().upsert(conv.copy(updatedAt = message.createdAt))
        }
    }
}

@Singleton
class AuditRepository @Inject constructor(
    private val db: DastyarDatabase,
) {
    fun observe(tool: String, success: Boolean?): Flow<List<AuditEntity>> =
        db.audit().observeFiltered(tool, success)

    suspend fun insert(entry: AuditEntity) = db.audit().insert(entry)

    suspend fun exportText(): String {
        val rows = db.audit().list()
        return buildString {
            append("Dastyar audit\n")
            rows.forEach { row ->
                append(row.createdAt)
                append('\t')
                append(if (row.success) "ok" else "fail")
                append('\t')
                append(row.risk)
                append('\t')
                append(row.toolName)
                append('\t')
                append(row.argsSummary.replace('\n', ' '))
                append('\t')
                append(row.resultSummary.replace('\n', ' '))
                append('\n')
            }
        }
    }

    suspend fun deleteAll() = db.audit().deleteAll()
}

@Singleton
class MemoryRepository @Inject constructor(
    private val db: DastyarDatabase,
) {
    fun observe(): Flow<List<MemoryEntity>> = db.memories().observe()

    suspend fun latest(limit: Int = 30): List<MemoryEntity> = db.memories().latest(limit)

    suspend fun add(fact: String): String {
        val id = UUID.randomUUID().toString()
        db.memories().insert(MemoryEntity(id, fact.trim(), System.currentTimeMillis()))
        return id
    }

    suspend fun delete(id: String) = db.memories().delete(id)
}

@Singleton
class RoutineRepository @Inject constructor(
    private val db: DastyarDatabase,
) {
    fun observe(): Flow<List<RoutineEntity>> = db.routines().observe()

    suspend fun list(): List<RoutineEntity> = db.routines().list()

    suspend fun get(id: String): RoutineEntity? = db.routines().get(id)

    suspend fun save(entity: RoutineEntity) = db.routines().upsert(entity)

    suspend fun delete(id: String) = db.routines().delete(id)
}

@Singleton
class ProviderRepository @Inject constructor(
    private val db: DastyarDatabase,
    private val vault: SecretVault,
) {
    fun observe(): Flow<List<ProviderEntity>> = db.providers().observe()

    suspend fun listMeta(): List<ProviderEntity> = db.providers().list()

    suspend fun defaultProfile(): ProviderProfile? {
        val entity = db.providers().defaultProvider() ?: db.providers().list().firstOrNull() ?: return null
        return hydrate(entity)
    }

    suspend fun profile(id: String): ProviderProfile? {
        val entity = db.providers().get(id) ?: return null
        return hydrate(entity)
    }

    suspend fun save(
        id: String?,
        kind: String,
        label: String,
        model: String,
        baseUrl: String,
        apiKey: String?,
        accountId: String,
        extraHeaders: Map<String, String>,
        makeDefault: Boolean,
    ): String {
        val existing = id?.let { db.providers().get(it) }
        val resolvedId = existing?.id ?: UUID.randomUUID().toString()
        val previous = existing?.let { readSecret(it.id) }
        val key = when {
            !apiKey.isNullOrEmpty() -> apiKey
            previous != null -> previous.first
            else -> ""
        }
        if (makeDefault || db.providers().list().isEmpty()) db.providers().clearDefault()
        db.providers().upsert(
            ProviderEntity(
                id = resolvedId,
                kind = kind,
                label = label.trim().ifBlank { kind },
                model = model.trim(),
                baseUrl = baseUrl.trim(),
                isDefault = makeDefault || existing?.isDefault == true || db.providers().defaultProvider() == null,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        if (makeDefault) {
            db.providers().clearDefault()
            db.providers().markDefault(resolvedId)
        }
        writeSecret(resolvedId, key, accountId, extraHeaders)
        return resolvedId
    }

    suspend fun setDefault(id: String) {
        db.providers().clearDefault()
        db.providers().markDefault(id)
    }

    suspend fun delete(id: String) {
        db.providers().delete(id)
        vault.delete(secretId(id))
        if (db.providers().defaultProvider() == null) {
            db.providers().list().firstOrNull()?.let { db.providers().markDefault(it.id) }
        }
    }

    suspend fun deleteAllSecrets() {
        vault.clear()
    }

    private suspend fun hydrate(entity: ProviderEntity): ProviderProfile {
        val secret = readSecret(entity.id)
        return ProviderProfile(
            id = entity.id,
            kind = entity.kind,
            label = entity.label,
            model = entity.model,
            baseUrl = entity.baseUrl,
            isDefault = entity.isDefault,
            apiKey = secret?.first.orEmpty(),
            accountId = secret?.second.orEmpty(),
            extraHeaders = secret?.third ?: emptyMap(),
        )
    }

    private suspend fun readSecret(id: String): Triple<String, String, Map<String, String>>? {
        val raw = vault.get(secretId(id)) ?: return null
        val json = JSONObject(raw)
        val headers = mutableMapOf<String, String>()
        val headerObj = json.optJSONObject("headers")
        if (headerObj != null) {
            val keys = headerObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                headers[key] = headerObj.optString(key)
            }
        }
        return Triple(json.optString("apiKey"), json.optString("accountId"), headers)
    }

    private suspend fun writeSecret(
        id: String,
        apiKey: String,
        accountId: String,
        headers: Map<String, String>,
    ) {
        val headerJson = JSONObject()
        headers.forEach { (k, v) -> headerJson.put(k, v) }
        val json = JSONObject()
            .put("apiKey", apiKey)
            .put("accountId", accountId)
            .put("headers", headerJson)
        vault.put(secretId(id), json.toString())
    }

    private fun secretId(id: String) = "provider:$id"
}

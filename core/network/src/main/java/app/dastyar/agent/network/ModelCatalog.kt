package app.dastyar.agent.network

import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class ModelSource { LIVE, CACHE, BUILTIN }

enum class ModelFetchStatus { OK, EMPTY, NETWORK, HTTP, DISCONTINUED }

data class CatalogModel(
    val id: String,
    val displayName: String,
    val free: Boolean,
    val promptPrice: String? = null,
    val completionPrice: String? = null,
    val contextLength: Int? = null,
    /** null means the payload did not say. */
    val vision: Boolean? = null,
    /** null means the payload did not say. */
    val tools: Boolean? = null,
    val source: ModelSource = ModelSource.LIVE,
    /** "trial" when the provider is a trial, not a free tier. */
    val note: String? = null,
)

data class ModelFetchRequest(
    val kind: String,
    val baseUrl: String,
    val apiKey: String,
    val accountId: String = "",
    val extraHeaders: Map<String, String> = emptyMap(),
)

data class ModelFetchResult(
    val models: List<CatalogModel>,
    val status: ModelFetchStatus,
    val httpCode: Int? = null,
)

/**
 * Honest free-tier rules. A model is free only when a rule below says so.
 * Rate-limited developer access still counts as free. Unknown stays not free.
 */
object ModelFree {
    /** Documented Groq free-plan examples in the provider catalog. Other Groq ids stay unknown. */
    val GROQ_FREE_IDS = setOf(
        "openai/gpt-oss-120b",
        "openai/gpt-oss-20b",
        "qwen/qwen3.8-27b",
    )

    fun isFree(
        kind: String,
        id: String,
        promptPrice: String? = null,
        completionPrice: String? = null,
        baseUrl: String = "",
    ): Boolean {
        val modelId = id.trim()
        if (modelId.isEmpty() || kind == ProviderKinds.GITHUB_MODELS) return false
        val suffixFree = modelId.endsWith(":free", ignoreCase = true)
        return when (kind) {
            ProviderKinds.OPENROUTER ->
                suffixFree || modelId.equals("openrouter/free", ignoreCase = true) || pricedAtZero(promptPrice, completionPrice)
            ProviderKinds.GEMINI -> geminiFree(modelId, promptPrice, completionPrice)
            ProviderKinds.GROQ ->
                if (pricedNonZero(promptPrice, completionPrice)) false
                else suffixFree || pricedAtZero(promptPrice, completionPrice) || modelId.lowercase() in GROQ_FREE_IDS
            ProviderKinds.NVIDIA ->
                if (pricedNonZero(promptPrice, completionPrice)) false
                else suffixFree || pricedAtZero(promptPrice, completionPrice) || isOfficialNvidiaHost(baseUrl)
            ProviderKinds.CLOUDFLARE ->
                if (pricedNonZero(promptPrice, completionPrice)) false
                else suffixFree || pricedAtZero(promptPrice, completionPrice) || modelId.startsWith("@cf/")
            ProviderKinds.CEREBRAS -> suffixFree || pricedAtZero(promptPrice, completionPrice)
            else -> suffixFree || pricedAtZero(promptPrice, completionPrice)
        }
    }

    fun geminiFree(id: String, promptPrice: String?, completionPrice: String?): Boolean {
        val name = id.substringAfter("models/").lowercase()
        if (name.contains("pro") || name.contains("ultra") || name.contains("flagship")) return false
        if (pricedAtZero(promptPrice, completionPrice)) return true
        return name.contains("flash") || name.contains("lite")
    }

    fun pricedAtZero(promptPrice: String?, completionPrice: String?): Boolean {
        val prompt = parsePrice(promptPrice) ?: return false
        val completion = parsePrice(completionPrice) ?: return false
        return prompt == 0.0 && completion == 0.0
    }

    fun pricedNonZero(promptPrice: String?, completionPrice: String?): Boolean {
        val prompt = parsePrice(promptPrice)
        val completion = parsePrice(completionPrice)
        return (prompt != null && prompt > 0.0) || (completion != null && completion > 0.0)
    }

    fun parsePrice(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim().removePrefix("$").toDoubleOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return value
    }

    fun isOfficialNvidiaHost(baseUrl: String): Boolean =
        ModelEndpoints.hostOf(baseUrl) == "integrate.api.nvidia.com"
}

object ModelOrder {
    fun sort(models: List<CatalogModel>): List<CatalogModel> =
        models.sortedWith(
            compareByDescending<CatalogModel> { it.free }
                .thenBy { it.displayName.lowercase() }
                .thenBy { it.id.lowercase() },
        )

    /** Search keeps the free-then-paid grouping. */
    fun filter(models: List<CatalogModel>, query: String): List<CatalogModel> {
        val q = query.trim()
        val matched = if (q.isEmpty()) {
            models
        } else {
            models.filter { it.id.contains(q, ignoreCase = true) || it.displayName.contains(q, ignoreCase = true) }
        }
        return sort(matched)
    }

    /**
     * Live list wins. Otherwise the cache. Otherwise the built-in list.
     * Returns the source label: live, cache, builtin, or empty.
     */
    fun choose(
        live: List<CatalogModel>?,
        cached: List<CatalogModel>,
        builtin: List<CatalogModel>,
    ): Pair<List<CatalogModel>, String> = when {
        live != null && live.isNotEmpty() -> sort(live) to "live"
        cached.isNotEmpty() -> sort(cached) to "cache"
        builtin.isNotEmpty() -> sort(builtin) to "builtin"
        else -> emptyList<CatalogModel>() to "empty"
    }
}

object ModelEndpoints {
    fun normalize(base: String): String = base.trim().trimEnd('/')

    fun hostOf(base: String): String {
        val noScheme = base.trim().substringAfter("://", base.trim())
        return noScheme.substringBefore("/").substringBefore(":").lowercase()
    }

    fun openAiModels(base: String): String {
        val b = normalize(base)
        return if (b.endsWith("/models")) b else "$b/models"
    }

    fun openRouterModels(base: String): String {
        val b = normalize(base).ifBlank { "https://openrouter.ai/api/v1" }
        return openAiModels(b)
    }

    fun geminiModels(base: String): String {
        val b = normalize(base).ifBlank { "https://generativelanguage.googleapis.com/v1beta" }
        return if (b.endsWith("/models")) b else "$b/models"
    }

    fun anthropicModels(base: String): String {
        val b = normalize(base).ifBlank { "https://api.anthropic.com" }
        return when {
            b.endsWith("/models") -> b
            b.endsWith("/v1") -> "$b/models"
            else -> "$b/v1/models"
        }
    }

    fun cloudflareModels(base: String, accountId: String): String {
        val account = accountId.trim()
        val b = normalize(base).ifBlank { "https://api.cloudflare.com" }
        val root = if (b.contains("/client/v4")) b.substringBefore("/client/v4") else b
        return "$root/client/v4/accounts/${encode(account)}/ai/models/search"
    }

    fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

/**
 * Ids copied from the provider catalog defaults. No extra flagship names are invented.
 * Anthropic has a live list; this fallback is only the saved model plus these defaults.
 */
object BuiltinModels {
    fun forKind(kind: String): List<CatalogModel> {
        val rows = when (kind) {
            ProviderKinds.GEMINI -> listOf(row(kind, "gemini-3.8-flash", "Gemini 3.8 Flash"))
            ProviderKinds.OPENROUTER -> listOf(row(kind, "openrouter/free", "OpenRouter free router"))
            ProviderKinds.GROQ -> listOf(
                row(kind, "openai/gpt-oss-120b", "openai/gpt-oss-120b"),
                row(kind, "openai/gpt-oss-20b", "openai/gpt-oss-20b"),
                row(kind, "qwen/qwen3.8-27b", "qwen/qwen3.8-27b"),
            )
            ProviderKinds.CLOUDFLARE -> listOf(
                row(kind, "@cf/meta/llama-3.1-8b-instruct", "@cf/meta/llama-3.1-8b-instruct"),
            )
            ProviderKinds.DEEPSEEK -> listOf(row(kind, "deepseek-chat", "deepseek-chat"))
            else -> emptyList()
        }
        return ModelOrder.sort(rows.map { it.copy(source = ModelSource.BUILTIN) })
    }

    /** Adds the model already saved on the provider when the static list does not name it. */
    fun fallback(kind: String, savedModel: String, baseUrl: String = ""): List<CatalogModel> {
        val builtin = forKind(kind)
        val saved = savedModel.trim()
        if (saved.isEmpty() || builtin.any { it.id == saved }) return builtin
        val extra = row(kind, saved, saved, baseUrl).copy(source = ModelSource.BUILTIN)
        return ModelOrder.sort(listOf(extra) + builtin)
    }

    private fun row(kind: String, id: String, name: String, baseUrl: String = ""): CatalogModel {
        val free = ModelFree.isFree(kind, id, null, null, baseUrl)
        return CatalogModel(
            id = id,
            displayName = name,
            free = free,
            note = if (kind == ProviderKinds.CEREBRAS && !free) "trial" else null,
            source = ModelSource.BUILTIN,
        )
    }
}

object ModelListParser {
    fun parse(kind: String, baseUrl: String, body: String): List<CatalogModel> {
        val json = JSONObject(body)
        val raw = when (kind) {
            ProviderKinds.GEMINI -> gemini(json)
            ProviderKinds.OPENROUTER -> openRouter(json)
            ProviderKinds.CLOUDFLARE -> cloudflare(json)
            ProviderKinds.ANTHROPIC -> anthropic(json)
            else -> openAi(json)
        }
        return raw.map { it.classify(kind, baseUrl) }.distinctBy { it.id }
    }

    fun nextGeminiToken(body: String): String? =
        JSONObject(body).optString("nextPageToken").takeIf { it.isNotBlank() }

    fun nextAnthropic(body: String): String? {
        val json = JSONObject(body)
        if (!json.optBoolean("has_more")) return null
        return json.optString("last_id").takeIf { it.isNotBlank() }
    }

    fun cloudflareHasMore(body: String, page: Int, pageSize: Int): Boolean {
        val info = JSONObject(body).optJSONObject("result_info") ?: return false
        val total = info.optInt("total_count", -1)
        if (total < 0) return false
        return page * pageSize < total
    }

    private fun openAi(json: JSONObject): List<RawModel> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return data.objects().mapNotNull { item ->
            val id = item.optString("id").ifBlank { item.optString("name") }
            if (id.isBlank()) return@mapNotNull null
            RawModel(
                id = id,
                displayName = item.optString("name").ifBlank { item.optString("display_name").ifBlank { id } },
                contextLength = positive(item, "context_length") ?: positive(item, "context_window"),
            )
        }
    }

    private fun openRouter(json: JSONObject): List<RawModel> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return data.objects().mapNotNull { item ->
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val pricing = item.optJSONObject("pricing")
            val arch = item.optJSONObject("architecture")
            RawModel(
                id = id,
                displayName = item.optString("name").ifBlank { id },
                promptPrice = priceString(pricing, "prompt"),
                completionPrice = priceString(pricing, "completion"),
                contextLength = positive(item, "context_length"),
                vision = visionFromArchitecture(arch),
                tools = toolsFromParameters(item.optJSONArray("supported_parameters")),
            )
        }
    }

    private fun gemini(json: JSONObject): List<RawModel> {
        val data = json.optJSONArray("models") ?: return emptyList()
        return data.objects().mapNotNull { item ->
            val rawName = item.optString("name")
            val id = rawName.removePrefix("models/").trim()
            if (id.isBlank()) return@mapNotNull null
            val methods = item.optJSONArray("supportedGenerationMethods")
            val tools = if (methods == null || methods.length() == 0) {
                null
            } else {
                val names = (0 until methods.length()).map { methods.optString(it) }
                names.any { it == "generateContent" || it == "streamGenerateContent" }.let { chat ->
                    if (chat) null else false
                }
            }
            RawModel(
                id = id,
                displayName = item.optString("displayName").ifBlank { id },
                contextLength = positive(item, "inputTokenLimit"),
                tools = tools,
            )
        }
    }

    private fun anthropic(json: JSONObject): List<RawModel> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return data.objects().mapNotNull { item ->
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            val caps = item.optJSONObject("capabilities")
            val image = caps?.optJSONObject("image_input")
            val vision = if (image != null && image.has("supported")) image.optBoolean("supported") else null
            val toolObj = caps?.optJSONObject("tool_use") ?: caps?.optJSONObject("tools")
            val tools = if (toolObj != null && toolObj.has("supported")) toolObj.optBoolean("supported") else null
            val context = item.optInt("max_input_tokens", 0).takeIf { it > 0 }
            RawModel(
                id = id,
                displayName = item.optString("display_name").ifBlank { id },
                contextLength = context,
                vision = vision,
                tools = tools,
            )
        }
    }

    private fun cloudflare(json: JSONObject): List<RawModel> {
        val result = json.opt("result")
        val rows = when (result) {
            is JSONArray -> result.objects()
            is JSONObject -> result.optJSONArray("models")?.objects().orEmpty()
            else -> emptyList()
        }
        return rows.mapNotNull { item ->
            val id = item.optString("name").ifBlank { item.optString("id") }
            if (id.isBlank() || !id.contains("/")) return@mapNotNull null
            val props = properties(item)
            RawModel(
                id = id,
                displayName = id,
                contextLength = props["context_window"]?.toIntOrNull() ?: props["context"]?.toIntOrNull(),
                vision = props["vision"]?.toBooleanStrictOrNull() ?: props["image_input"]?.toBooleanStrictOrNull(),
                tools = props["function_calling"]?.toBooleanStrictOrNull()
                    ?: props["tool_calling"]?.toBooleanStrictOrNull(),
                promptPrice = props["prompt_price"],
                completionPrice = props["completion_price"],
            )
        }
    }

    private fun properties(item: JSONObject): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val array = item.optJSONArray("properties") ?: return out
        for (i in 0 until array.length()) {
            val prop = array.optJSONObject(i) ?: continue
            val key = prop.optString("property_id").ifBlank { prop.optString("name") }.ifBlank { prop.optString("id") }
            if (key.isBlank()) continue
            val raw = if (prop.has("value") && !prop.isNull("value")) prop.opt("value") else null
            val value = raw?.toString().orEmpty()
            if (value.isNotBlank()) out[key.lowercase()] = value
        }
        return out
    }

    private fun visionFromArchitecture(arch: JSONObject?): Boolean? {
        if (arch == null) return null
        val inputs = arch.optJSONArray("input_modalities")
        if (inputs != null) {
            for (i in 0 until inputs.length()) {
                if (inputs.optString(i).contains("image", ignoreCase = true)) return true
            }
            return false
        }
        if (!arch.has("modality")) return null
        return arch.optString("modality").contains("image", ignoreCase = true)
    }

    private fun toolsFromParameters(params: JSONArray?): Boolean? {
        if (params == null) return null
        for (i in 0 until params.length()) {
            val name = params.optString(i)
            if (name == "tools" || name == "tool_choice") return true
        }
        return false
    }

    private fun priceString(obj: JSONObject?, key: String): String? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        return obj.opt(key)?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun positive(obj: JSONObject, key: String): Int? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optInt(key, -1)
        return value.takeIf { it > 0 }
    }

    private data class RawModel(
        val id: String,
        val displayName: String,
        val promptPrice: String? = null,
        val completionPrice: String? = null,
        val contextLength: Int? = null,
        val vision: Boolean? = null,
        val tools: Boolean? = null,
    ) {
        fun classify(kind: String, baseUrl: String): CatalogModel {
            val free = ModelFree.isFree(kind, id, promptPrice, completionPrice, baseUrl)
            return CatalogModel(
                id = id,
                displayName = displayName.ifBlank { id },
                free = free,
                promptPrice = promptPrice,
                completionPrice = completionPrice,
                contextLength = contextLength,
                vision = vision,
                tools = tools,
                source = ModelSource.LIVE,
                note = if (kind == ProviderKinds.CEREBRAS && !free) "trial" else null,
            )
        }
    }
}

private class ModelHttpException(val code: Int) : Exception("http $code")

@Singleton
class ModelCatalog @Inject constructor() {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(request: ModelFetchRequest): ModelFetchResult = withContext(Dispatchers.IO) {
        if (request.kind == ProviderKinds.GITHUB_MODELS) {
            return@withContext ModelFetchResult(emptyList(), ModelFetchStatus.DISCONTINUED)
        }
        try {
            val models = when (request.kind) {
                ProviderKinds.GEMINI -> fetchGemini(request)
                ProviderKinds.OPENROUTER -> fetchSingle(request, ModelEndpoints.openRouterModels(request.baseUrl), bearer(request))
                ProviderKinds.CLOUDFLARE -> fetchCloudflare(request)
                ProviderKinds.ANTHROPIC -> fetchAnthropic(request)
                else -> fetchSingle(request, ModelEndpoints.openAiModels(request.baseUrl), bearer(request))
            }
            val sorted = ModelOrder.sort(models.distinctBy { it.id })
            if (sorted.isEmpty()) ModelFetchResult(emptyList(), ModelFetchStatus.EMPTY)
            else ModelFetchResult(sorted, ModelFetchStatus.OK)
        } catch (error: ModelHttpException) {
            ModelFetchResult(emptyList(), ModelFetchStatus.HTTP, error.code)
        } catch (_: Exception) {
            ModelFetchResult(emptyList(), ModelFetchStatus.NETWORK, null)
        }
    }

    private fun fetchSingle(request: ModelFetchRequest, url: String, headers: Map<String, String>): List<CatalogModel> {
        if (url.isBlank() || ModelEndpoints.hostOf(url).isBlank()) return emptyList()
        val (code, body) = get(url, headers)
        if (code !in 200..299) throw ModelHttpException(code)
        return ModelListParser.parse(request.kind, request.baseUrl, body)
    }

    private fun fetchGemini(request: ModelFetchRequest): List<CatalogModel> {
        val headers = linkedMapOf<String, String>()
        request.extraHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) headers[k] = v }
        if (request.apiKey.isNotBlank()) headers["x-goog-api-key"] = request.apiKey
        if (!headers.containsKey("Accept")) headers["Accept"] = "application/json"
        val root = ModelEndpoints.geminiModels(request.baseUrl)
        val all = mutableListOf<CatalogModel>()
        var token: String? = null
        repeat(MAX_PAGES) {
            val pageToken = token
            val url = buildString {
                append(root)
                append("?pageSize=100")
                if (!pageToken.isNullOrBlank()) {
                    append("&pageToken=")
                    append(ModelEndpoints.encode(pageToken))
                }
            }
            val (code, body) = get(url, headers)
            if (code !in 200..299) {
                if (all.isEmpty()) throw ModelHttpException(code)
                return all
            }
            all += ModelListParser.parse(ProviderKinds.GEMINI, request.baseUrl, body)
            token = ModelListParser.nextGeminiToken(body)
            if (token.isNullOrBlank()) return all
        }
        return all
    }

    private fun fetchAnthropic(request: ModelFetchRequest): List<CatalogModel> {
        val headers = linkedMapOf<String, String>()
        request.extraHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) headers[k] = v }
        if (request.apiKey.isNotBlank()) headers["x-api-key"] = request.apiKey
        headers["anthropic-version"] = "2023-06-01"
        if (!headers.containsKey("Accept")) headers["Accept"] = "application/json"
        val root = ModelEndpoints.anthropicModels(request.baseUrl)
        val all = mutableListOf<CatalogModel>()
        var after: String? = null
        repeat(MAX_PAGES) {
            val cursor = after
            val url = buildString {
                append(root)
                append("?limit=100")
                if (!cursor.isNullOrBlank()) {
                    append("&after_id=")
                    append(ModelEndpoints.encode(cursor))
                }
            }
            val (code, body) = get(url, headers)
            if (code !in 200..299) {
                if (all.isEmpty()) throw ModelHttpException(code)
                return all
            }
            all += ModelListParser.parse(ProviderKinds.ANTHROPIC, request.baseUrl, body)
            after = ModelListParser.nextAnthropic(body)
            if (after.isNullOrBlank()) return all
        }
        return all
    }

    private fun fetchCloudflare(request: ModelFetchRequest): List<CatalogModel> {
        if (request.accountId.isBlank()) return emptyList()
        val headers = bearer(request)
        val root = ModelEndpoints.cloudflareModels(request.baseUrl, request.accountId)
        val all = mutableListOf<CatalogModel>()
        var page = 1
        repeat(MAX_PAGES) {
            val url = "$root?per_page=50&page=$page"
            val (code, body) = get(url, headers)
            if (code !in 200..299) {
                if (all.isEmpty()) throw ModelHttpException(code)
                return all
            }
            val batch = ModelListParser.parse(ProviderKinds.CLOUDFLARE, request.baseUrl, body)
            if (batch.isEmpty()) return all
            all += batch
            val info = try {
                JSONObject(body).optJSONObject("result_info")
            } catch (_: Exception) {
                null
            }
            val more = if (info != null) ModelListParser.cloudflareHasMore(body, page, 50) else batch.size >= 50
            if (!more) return all
            page += 1
        }
        return all
    }

    private fun bearer(request: ModelFetchRequest): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        request.extraHeaders.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) headers[k] = v }
        if (request.apiKey.isNotBlank()) headers["Authorization"] = "Bearer ${request.apiKey}"
        if (request.kind == ProviderKinds.OPENROUTER) {
            if (!headers.containsKey("HTTP-Referer")) headers["HTTP-Referer"] = "https://github.com/mohsenone/dastyar-android"
            if (!headers.containsKey("X-Title")) headers["X-Title"] = "Dastyar"
        }
        if (!headers.containsKey("Accept")) headers["Accept"] = "application/json"
        return headers
    }

    private fun get(url: String, headers: Map<String, String>): Pair<Int, String> {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) builder.header(key, value)
        }
        http.newCall(builder.build()).execute().use { response ->
            return response.code to response.body?.string().orEmpty()
        }
    }

    private companion object {
        const val MAX_PAGES = 6
    }
}

private fun JSONArray.objects(): List<JSONObject> = buildList {
    for (i in 0 until length()) optJSONObject(i)?.let { add(it) }
}

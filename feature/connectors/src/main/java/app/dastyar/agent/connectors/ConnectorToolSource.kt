package app.dastyar.agent.connectors

import app.dastyar.agent.tools.DynamicTool
import app.dastyar.agent.tools.DynamicToolSource
import app.dastyar.agent.tools.RiskLevel
import app.dastyar.agent.tools.ToolContext
import app.dastyar.agent.tools.ToolResult
import app.dastyar.agent.tools.ToolSpec
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

@Singleton
class ConnectorToolSource @Inject constructor(
    private val store: ConnectorStore,
    private val runtime: ConnectorRuntime,
) : DynamicToolSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var mcpTools: List<DynamicTool> = emptyList()
    @Volatile private var mcpKey: String = ""

    init {
        scope.launch {
            store.flow.collect { snap ->
                val key = if (ConnectorIds.MCP !in snap.enabled) "" else snap.mcp.joinToString("|") { "${it.id}:${it.url}:${it.transport}" }
                if (key == mcpKey) return@collect
                mcpKey = key
                mcpTools = if (key.isEmpty()) emptyList() else loadMcp()
            }
        }
    }

    override fun current(): List<DynamicTool> {
        val snap = store.cached()
        val on = snap.enabled
        return buildList {
            if (ConnectorIds.GOOGLE in on) addAll(google())
            if (ConnectorIds.GITHUB in on) addAll(github())
            if (ConnectorIds.TELEGRAM in on) addAll(telegram())
            if (ConnectorIds.NOTION in on) addAll(notion())
            if (ConnectorIds.WEBHOOK in on) add(webhook())
            if (ConnectorIds.SEARCH in on) add(search())
            if (ConnectorIds.RSS in on) addAll(rss())
            if (ConnectorIds.WEATHER in on) add(weather())
            if (ConnectorIds.MCP in on) addAll(mcpTools)
            if (ConnectorIds.SHIZUKU in on) addAll(shizuku())
        }
    }

    fun reloadMcp() {
        mcpKey = ""
        scope.launch {
            val snap = store.current()
            mcpKey = snap.mcp.joinToString("|") { "${it.id}:${it.url}:${it.transport}" }
            mcpTools = if (ConnectorIds.MCP in snap.enabled) loadMcp() else emptyList()
        }
    }

    private suspend fun loadMcp(): List<DynamicTool> {
        val pairs = runCatching { runtime.mcpTools() }.getOrDefault(emptyList())
        val used = mutableSetOf<String>()
        return pairs.map { (server, remote) ->
            val name = McpNames.unique(McpNames.toolName(server.id, remote.name), used)
            dyn(
                name,
                remote.description.ifBlank { "MCP tool ${remote.name} on ${server.name}." } + " Remote MCP server ${server.name}.",
                remote.inputSchema.ifBlank { obj() },
                RiskLevel.CONFIRM,
                { args, _ -> "${server.name} / ${remote.name}\n${args.toString().take(240)}" },
            ) { args, _ -> ToolResult(true, runtime.mcpCall(server, remote.name, args.toString())) }
        }
    }

    private fun google() = listOf(
        dyn("google_gmail_search", "Search the signed-in Gmail account. Read only. Needs the Gmail scope.", obj("query" to str()), RiskLevel.CONFIRM, { a, _ -> "Gmail search ${a.optString("query")}" }) { a, _ -> ToolResult(true, runtime.gmailSearch(a.optString("query"))) },
        dyn("google_gmail_send", "Send an email through the Gmail API. Always confirmed. Needs gmail.send, which may require Google verification.", obj("to" to str(), "subject" to str(), "body" to str()), RiskLevel.DANGEROUS, { a, _ -> "Send email to ${a.optString("to")}\n${a.optString("subject")}\n${a.optString("body")}" }) { a, _ -> ToolResult(true, runtime.gmailSend(a.optString("to"), a.optString("subject"), a.optString("body"))) },
        dyn("google_calendar_list", "List upcoming primary-calendar events.", obj(), RiskLevel.CONFIRM, { _, _ -> "List calendar events" }) { _, _ -> ToolResult(true, runtime.calendarList()) },
        dyn("google_calendar_create", "Create a primary-calendar event. start and end are RFC3339 datetimes.", obj("title" to str(), "start" to str(), "end" to str()), RiskLevel.CONFIRM, { a, _ -> "Create event ${a.optString("title")}" }) { a, _ -> ToolResult(true, runtime.calendarCreate(a.optString("title"), a.optString("start"), a.optString("end"))) },
        dyn("google_calendar_update", "Update a calendar event by id. Confirmed because it changes an existing event.", obj("event_id" to str(), "title" to str(), "start" to str(), "end" to str()), RiskLevel.DANGEROUS, { a, _ -> "Update event ${a.optString("event_id")}" }) { a, _ -> ToolResult(true, runtime.calendarUpdate(a.optString("event_id"), a.optString("title"), a.optString("start"), a.optString("end"))) },
        dyn("google_calendar_delete", "Delete a calendar event by id.", obj("event_id" to str()), RiskLevel.DANGEROUS, { a, _ -> "Delete event ${a.optString("event_id")}" }) { a, _ -> ToolResult(true, runtime.calendarDelete(a.optString("event_id"))) },
        dyn("google_drive_list", "List Drive file metadata. Partial: the Drive scope may need Google verification.", obj("query" to str()), RiskLevel.CONFIRM, { a, _ -> "Drive ${a.optString("query")}" }) { a, _ -> ToolResult(true, runtime.driveList(a.optString("query"))) },
        dyn("google_drive_download", "Download a Drive file into app storage. Partial if the scope was not granted.", obj("file_id" to str()), RiskLevel.CONFIRM, { a, _ -> "Download ${a.optString("file_id")}" }) { a, _ -> ToolResult(true, runtime.driveDownload(a.optString("file_id"))) },
        dyn("google_drive_upload", "Upload a file from app storage to Drive. Confirmed. Partial if the scope was not granted.", obj("path" to str(), "name" to str()), RiskLevel.DANGEROUS, { a, _ -> "Upload ${a.optString("path")}" }) { a, _ -> ToolResult(true, runtime.driveUpload(a.optString("path"), a.optString("name"))) },
        dyn("google_contacts_search", "Search Google contacts. Partial: the People API scope may need verification.", obj("query" to str()), RiskLevel.CONFIRM, { a, _ -> "Contacts ${a.optString("query")}" }) { a, _ -> ToolResult(true, runtime.contactsSearch(a.optString("query"))) },
        dyn("google_contacts_write", "Create a Google contact. Confirmed. Partial if the scope was not granted.", obj("name" to str(), "phone" to str(), "email" to str()), RiskLevel.DANGEROUS, { a, _ -> "Create contact ${a.optString("name")}" }) { a, _ -> ToolResult(true, runtime.contactsCreate(a.optString("name"), a.optString("phone"), a.optString("email"))) },
    )

    private fun github() = listOf(
        dyn("github_list_repos", "List repositories for the token's user.", obj(), RiskLevel.CONFIRM, { _, _ -> "List GitHub repos" }) { _, _ -> ToolResult(true, runtime.github("/user/repos?per_page=20&sort=updated")) },
        dyn("github_list_issues", "List open issues for owner/repo.", obj("owner" to str(), "repo" to str()), RiskLevel.CONFIRM, { a, _ -> "${a.optString("owner")}/${a.optString("repo")} issues" }) { a, _ -> ToolResult(true, runtime.github("/repos/${a.optString("owner")}/${a.optString("repo")}/issues?state=open&per_page=15")) },
        dyn("github_list_pulls", "List open pull requests.", obj("owner" to str(), "repo" to str()), RiskLevel.CONFIRM, { a, _ -> "${a.optString("owner")}/${a.optString("repo")} pulls" }) { a, _ -> ToolResult(true, runtime.github("/repos/${a.optString("owner")}/${a.optString("repo")}/pulls?state=open&per_page=15")) },
        dyn("github_read_file", "Read a file from a repository. Content is the GitHub API JSON, including base64 for small files.", obj("owner" to str(), "repo" to str(), "path" to str()), RiskLevel.CONFIRM, { a, _ -> a.optString("path") }) { a, _ -> ToolResult(true, runtime.github("/repos/${a.optString("owner")}/${a.optString("repo")}/contents/${a.optString("path")}")) },
        dyn("github_create_issue", "Create a GitHub issue. Confirmed.", obj("owner" to str(), "repo" to str(), "title" to str(), "body" to str()), RiskLevel.DANGEROUS, { a, _ -> "Issue ${a.optString("title")}" }) { a, _ ->
            val payload = JSONObject().put("title", a.optString("title")).put("body", a.optString("body")).toString()
            ToolResult(true, runtime.githubWrite("/repos/${a.optString("owner")}/${a.optString("repo")}/issues", payload))
        },
        dyn("github_comment", "Comment on an issue or pull request. Confirmed.", obj("owner" to str(), "repo" to str(), "number" to """{"type":"integer"}""", "body" to str()), RiskLevel.DANGEROUS, { a, _ -> "Comment #${a.optInt("number")}" }) { a, _ ->
            val payload = JSONObject().put("body", a.optString("body")).toString()
            ToolResult(true, runtime.githubWrite("/repos/${a.optString("owner")}/${a.optString("repo")}/issues/${a.optInt("number")}/comments", payload))
        },
    )

    private fun telegram() = listOf(
        dyn(
            "telegram_bot_updates",
            "List recent updates the bot can see. This is the Bot API, not silent control of the user's personal Telegram account.",
            obj(),
            RiskLevel.CONFIRM,
            { _, _ -> "Telegram bot updates" },
        ) { _, _ -> ToolResult(true, runtime.telegramUpdates()) },
        dyn(
            "telegram_bot_send",
            "Send a text from the bot to a chat id the user provides. Confirmed. This does not control the personal Telegram app.",
            obj("chat_id" to str(), "text" to str()),
            RiskLevel.DANGEROUS,
            { a, _ -> "Bot message to ${a.optString("chat_id")}\n${a.optString("text")}" },
        ) { a, _ -> ToolResult(true, runtime.telegramSend(a.optString("chat_id"), a.optString("text"))) },
        dyn(
            "telegram_bot_send_file",
            "Send a file that is already in app storage from the bot to a chat id. Confirmed. This is the Bot API, not the personal Telegram account.",
            obj("chat_id" to str(), "path" to str(), "caption" to str()),
            RiskLevel.DANGEROUS,
            { a, _ -> "Bot file to ${a.optString("chat_id")}\n${a.optString("path")}" },
        ) { a, _ -> ToolResult(true, runtime.telegramSendFile(a.optString("chat_id"), a.optString("path"), a.optString("caption"))) },
    )

    private fun notion() = listOf(
        dyn("notion_search", "Search the Notion integration's pages.", obj("query" to str()), RiskLevel.CONFIRM, { a, _ -> "Notion ${a.optString("query")}" }) { a, _ -> ToolResult(true, runtime.notionSearch(a.optString("query"))) },
        dyn("notion_read", "Read child blocks of a Notion page or block id.", obj("block_id" to str()), RiskLevel.CONFIRM, { a, _ -> a.optString("block_id") }) { a, _ -> ToolResult(true, runtime.notionRead(a.optString("block_id"))) },
        dyn("notion_append", "Append a paragraph to a Notion block. Confirmed.", obj("block_id" to str(), "text" to str()), RiskLevel.DANGEROUS, { a, _ -> "Append to ${a.optString("block_id")}" }) { a, _ -> ToolResult(true, runtime.notionAppend(a.optString("block_id"), a.optString("text"))) },
        dyn("notion_create_page", "Create a child page under a parent page id. Confirmed.", obj("parent_id" to str(), "title" to str()), RiskLevel.DANGEROUS, { a, _ -> "Notion page ${a.optString("title")}" }) { a, _ -> ToolResult(true, runtime.notionCreate(a.optString("parent_id"), a.optString("title"))) },
    )

    private fun webhook() = dyn(
        "webhook_request",
        "Send an HTTP request to a URL. HTTPS is allowed. HTTP only if that host is on the web allowlist. Confirmed.",
        obj("url" to str(), "method" to str(), "body" to str(), "header" to str()),
        RiskLevel.DANGEROUS,
        { a, _ -> "${a.optString("method", "POST")} ${a.optString("url")}" },
    ) { a, _ -> ToolResult(true, runtime.webhook(a.optString("method", "POST"), a.optString("url"), a.optString("header"), a.optString("body"))) }

    private fun search() = dyn(
        "web_search",
        "Search the web with the key or SearXNG base saved in Connectors. Does not invent a free quota.",
        obj("query" to str()),
        RiskLevel.CONFIRM,
        { a, _ -> a.optString("query") },
    ) { a, _ -> ToolResult(true, runtime.webSearch(a.optString("query"))) }

    private fun rss() = listOf(
        dyn("rss_list", "List saved RSS or Atom feed URLs.", obj(), RiskLevel.SAFE, { _, _ -> "List feeds" }) { _, _ ->
            val feeds = store.cached().rss
            ToolResult(true, feeds.joinToString("\n").ifBlank { "No feeds." })
        },
        dyn("rss_add", "Save an http(s) feed URL.", obj("url" to str()), RiskLevel.CONFIRM, { a, _ -> a.optString("url") }) { a, _ ->
            val url = RssUrls.normalize(a.optString("url")) ?: return@dyn ToolResult(false, "Feed URL must be http(s).")
            val next = (store.cached().rss + url).distinct()
            store.setRss(next)
            ToolResult(true, "Saved $url")
        },
        dyn("rss_fetch", "Fetch the latest items from one feed URL.", obj("url" to str()), RiskLevel.SAFE, { a, _ -> a.optString("url") }) { a, _ ->
            ToolResult(true, runtime.rssFetch(a.optString("url").ifBlank { store.cached().rss.firstOrNull().orEmpty() }))
        },
    )

    private fun weather() = dyn(
        "weather",
        "Current conditions and a short forecast. Open-Meteo needs no key. OpenWeather is used only when that option and a key are saved. Pass a place name or lat and lon.",
        obj("place" to str(), "lat" to str(), "lon" to str()),
        RiskLevel.SAFE,
        { a, _ -> a.optString("place").ifBlank { "${a.optString("lat")},${a.optString("lon")}" } },
    ) { a, _ -> ToolResult(true, runtime.weather(a.optString("place"), a.optString("lat"), a.optString("lon"))) }

    private fun shizuku() = listOf(
        dyn("shizuku_status", "Say whether optional Shizuku is installed, running, and authorized. Does not grant anything.", obj(), RiskLevel.SAFE, { _, _ -> "Shizuku status" }) { _, _ ->
            ToolResult(true, ShizukuBridge.status())
        },
        dyn("shizuku_settings_get", "Read one Android setting through Shizuku. Confirmed. Namespace is system, secure, or global.", obj("namespace" to str(), "key" to str()), RiskLevel.CONFIRM, { a, _ -> "settings get ${a.optString("namespace")} ${a.optString("key")}" }) { a, _ ->
            ToolResult(true, ShizukuBridge.exec(listOf("settings", "get", a.optString("namespace"), a.optString("key"))))
        },
        dyn("shizuku_settings_put", "Write one Android setting through Shizuku. Confirmed. Does nothing if Shizuku is not authorized.", obj("namespace" to str(), "key" to str(), "value" to str()), RiskLevel.DANGEROUS, { a, _ -> "settings put ${a.optString("namespace")} ${a.optString("key")}=${a.optString("value")}" }) { a, _ ->
            ToolResult(true, ShizukuBridge.exec(listOf("settings", "put", a.optString("namespace"), a.optString("key"), a.optString("value"))))
        },
        dyn("shizuku_pm_grant", "Grant one runtime permission the user named, through Shizuku. Confirmed.", obj("package" to str(), "permission" to str()), RiskLevel.DANGEROUS, { a, _ -> "pm grant ${a.optString("package")} ${a.optString("permission")}" }) { a, _ ->
            ToolResult(true, ShizukuBridge.exec(listOf("pm", "grant", a.optString("package"), a.optString("permission"))))
        },
        dyn("shizuku_pm_revoke", "Revoke one runtime permission the user named, through Shizuku. Confirmed.", obj("package" to str(), "permission" to str()), RiskLevel.DANGEROUS, { a, _ -> "pm revoke ${a.optString("package")} ${a.optString("permission")}" }) { a, _ ->
            ToolResult(true, ShizukuBridge.exec(listOf("pm", "revoke", a.optString("package"), a.optString("permission"))))
        },
        dyn("shizuku_component", "Start a component or force-stop a package through Shizuku. action is start or force-stop. Confirmed.", obj("action" to str(), "target" to str()), RiskLevel.DANGEROUS, { a, _ -> "am ${a.optString("action")} ${a.optString("target")}" }) { a, _ ->
            val action = a.optString("action")
            val target = a.optString("target")
            val args = if (action == "start") listOf("am", "start", "-n", target) else listOf("am", "force-stop", target)
            ToolResult(true, ShizukuBridge.exec(args))
        },
    )

    private fun dyn(
        name: String,
        description: String,
        schema: String,
        risk: RiskLevel,
        confirm: (JSONObject, String) -> String,
        run: suspend (JSONObject, ToolContext) -> ToolResult,
    ) = DynamicTool(ToolSpec(name, description, schema), risk, confirm, run)

    private fun str() = """{"type":"string"}"""
    private fun obj(vararg props: Pair<String, String>): String {
        if (props.isEmpty()) return """{"type":"object","properties":{}}"""
        val body = props.joinToString(",") { (key, value) -> "\"$key\":$value" }
        return """{"type":"object","properties":{$body}}"""
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectorBindModule {
    @Binds
    abstract fun dynamic(source: ConnectorToolSource): DynamicToolSource
}

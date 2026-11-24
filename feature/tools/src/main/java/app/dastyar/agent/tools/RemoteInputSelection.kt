package app.dastyar.agent.tools

/** One RemoteInput on a notification action. [source] is "standard" or "wearable". */
data class ReplyAction(
    val source: String,
    val actionIndex: Int,
    val inputIndex: Int,
    val resultKey: String,
    val freeForm: Boolean,
)

object RemoteInputSelection {
    /** First free-form input. Prefer the notification itself over WearableExtender actions. */
    fun pick(actions: List<ReplyAction>): ReplyAction? {
        val free = actions.filter { it.freeForm && it.resultKey.isNotBlank() }
        return free.firstOrNull { it.source == "standard" } ?: free.firstOrNull()
    }
}

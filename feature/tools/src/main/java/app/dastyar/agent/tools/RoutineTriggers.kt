package app.dastyar.agent.tools

/** Hooks the app process uses to start a routine. The listener never sends a message by itself. */
object RoutineTriggers {
    @Volatile
    var onNotification: ((packageName: String, title: String, text: String) -> Unit)? = null

    @Volatile
    var onSms: ((from: String, body: String) -> Unit)? = null
}

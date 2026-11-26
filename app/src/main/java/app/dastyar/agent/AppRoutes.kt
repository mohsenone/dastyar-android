package app.dastyar.agent

/**
 * Destinations that used to live in the bottom bar, plus the screens those
 * destinations can open. A restored route that is not in [known] goes home
 * so a stale back stack cannot select a missing item.
 */
object AppRoutes {
    const val CHAT = "chat"
    const val PROVIDERS = "providers"
    const val CONNECTORS = "connectors"
    const val ROUTINES = "routines"
    const val SETTINGS = "settings"

    val menu = listOf(CHAT, PROVIDERS, CONNECTORS, ROUTINES, SETTINGS)

    private val known = menu + listOf(
        "onboarding",
        "permissions",
        "privacy",
        "audit",
        "memories",
        "disclosure",
    )

    fun base(route: String?): String? = route?.substringBefore('?')?.substringBefore('/')?.takeIf { it.isNotBlank() }

    fun menuRoute(route: String?): Boolean = base(route) in menu

    /** Home when the saved route is missing. Keeps a known route, including connectors. */
    fun restoreOrHome(route: String?): String {
        val leaf = base(route) ?: return CHAT
        if (leaf !in known) return CHAT
        return if (leaf == "permissions") "permissions?onboarding=0" else leaf
    }
}

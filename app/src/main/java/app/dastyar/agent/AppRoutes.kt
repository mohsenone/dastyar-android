package app.dastyar.agent

enum class BackAction {
    CloseDrawer,
    CloseVoice,
    CloseOverlay,
    Pop,
    GoChat,
    FinishActivity,
}

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

    /** Chat keeps the menu icon. Every other real screen shows an up arrow. */
    fun showsUp(route: String?): Boolean {
        val leaf = base(route) ?: return false
        return leaf != CHAT && leaf != "onboarding"
    }

    /**
     * System back. Pop is left to the navigation controller so a sheet inside the
     * current screen can close first. GoChat covers a stack that lost its parent.
     */
    fun backAction(
        menuDrawerOpen: Boolean,
        chatsDrawerOpen: Boolean,
        voiceOpen: Boolean,
        overlayOpen: Boolean,
        route: String?,
        canPop: Boolean,
    ): BackAction {
        if (menuDrawerOpen || chatsDrawerOpen) return BackAction.CloseDrawer
        if (overlayOpen) return BackAction.CloseOverlay
        if (voiceOpen) return BackAction.CloseVoice
        if (showsUp(route)) return if (canPop) BackAction.Pop else BackAction.GoChat
        return if (canPop) BackAction.Pop else BackAction.FinishActivity
    }

    fun interceptsSystemBack(action: BackAction): Boolean = when (action) {
        BackAction.CloseDrawer, BackAction.CloseVoice, BackAction.CloseOverlay, BackAction.GoChat -> true
        BackAction.Pop, BackAction.FinishActivity -> false
    }

    fun voiceStageOpen(handsFree: Boolean, phaseName: String): Boolean =
        handsFree || (phaseName.isNotBlank() && !phaseName.equals("Idle", ignoreCase = true))

    /** Home when the saved route is missing. Keeps a known route, including connectors. */
    fun restoreOrHome(route: String?): String {
        val leaf = base(route) ?: return CHAT
        if (leaf !in known) return CHAT
        return if (leaf == "permissions") "permissions?onboarding=0" else leaf
    }
}

package app.dastyar.agent.tools

/**
 * Remembers "don't ask again" for this process. A first request is not permanent:
 * Android also returns false from shouldShowRequestPermissionRationale before any prompt.
 * Call [record] only after the system dialog returns.
 */
object PermissionMemory {
    private val permanent = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun isPermanent(permission: String): Boolean = permission in permanent

    fun record(results: Map<String, Boolean>, canAskAgain: (String) -> Boolean) {
        results.forEach { (permission, granted) ->
            if (granted || canAskAgain(permission)) permanent.remove(permission) else permanent.add(permission)
        }
    }

    fun reconcile(granted: (String) -> Boolean) {
        permanent.filter(granted).forEach { permanent.remove(it) }
    }

    fun reset() = permanent.clear()
}

/** Optional Shizuku. The tools module does not depend on the Shizuku library. */
object ShizukuStatusHook {
    const val MISSING = "missing"
    const val UNAUTHORIZED = "unauthorized"
    const val READY = "ready"
    const val UNKNOWN = "unknown"

    @Volatile
    var read: () -> String = { UNKNOWN }
}

object AccessGroups {
    const val MESSAGES = "messages"
    const val CALLS = "calls"
    const val CONTACTS = "contacts"
    const val APPS = "apps"
    const val NOTIFICATIONS = "notifications"
    const val FILES = "files"
    const val LOCATION = "location"
    const val CAPTURE = "capture"
    const val SYSTEM = "system"
}

object PermissionTargets {
    const val RUNTIME = "runtime"
    const val APP_INFO = "app_info"
    const val ACCESSIBILITY = "accessibility"
    const val LISTENER = "listener"
    const val OVERLAY = "overlay"
    const val ALL_FILES = "all_files"
    const val SAF = "saf"
    const val EXACT_ALARM = "exact_alarm"
    const val USAGE = "usage"
    const val BATTERY = "battery"
    const val WRITE_SETTINGS = "write_settings"
    const val DND = "dnd"
    const val SHIZUKU = "shizuku"
}

object MediaAccess {
    fun requestPermissions(sdk: Int): List<String> = when {
        sdk >= 34 -> listOf(
            Perms.READ_MEDIA_IMAGES,
            Perms.READ_MEDIA_VIDEO,
            Perms.READ_MEDIA_AUDIO,
            Perms.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        sdk >= 33 -> listOf(Perms.READ_MEDIA_IMAGES, Perms.READ_MEDIA_VIDEO, Perms.READ_MEDIA_AUDIO)
        else -> listOf(Perms.READ_EXTERNAL_STORAGE)
    }

    fun state(sdk: Int, granted: (String) -> Boolean): AccessState {
        if (sdk < 33) {
            return if (granted(Perms.READ_EXTERNAL_STORAGE)) AccessState.GRANTED else AccessState.MISSING
        }
        val images = granted(Perms.READ_MEDIA_IMAGES)
        val video = granted(Perms.READ_MEDIA_VIDEO)
        val partial = sdk >= 34 && granted(Perms.READ_MEDIA_VISUAL_USER_SELECTED)
        return if ((images && video) || partial) AccessState.GRANTED else AccessState.MISSING
    }

    /** Android 14+ "Select photos" grants a subset instead of the whole library. */
    fun limited(sdk: Int, granted: (String) -> Boolean): Boolean {
        if (sdk < 34 || !granted(Perms.READ_MEDIA_VISUAL_USER_SELECTED)) return false
        return !(granted(Perms.READ_MEDIA_IMAGES) && granted(Perms.READ_MEDIA_VIDEO))
    }
}

object LocationAccess {
    fun foregroundGranted(granted: (String) -> Boolean): Boolean =
        granted(Perms.ACCESS_FINE_LOCATION) || granted(Perms.ACCESS_COARSE_LOCATION)

    fun backgroundState(sdk: Int, granted: (String) -> Boolean): AccessState {
        val foreground = foregroundGranted(granted)
        if (sdk < 29) return if (foreground) AccessState.GRANTED else AccessState.MISSING
        if (granted(Perms.ACCESS_BACKGROUND_LOCATION)) return AccessState.GRANTED
        if (!foreground) return AccessState.MISSING
        return AccessState.NEEDS_SETTINGS
    }

    /** Background location is a second step. Never put it on the first prompt. */
    fun canRequestBackground(sdk: Int, foreground: Boolean): Boolean = sdk >= 29 && foreground
}

object PermissionPlanner {
    val wizardGroups = listOf(
        AccessGroups.MESSAGES,
        AccessGroups.CALLS,
        AccessGroups.CONTACTS,
        AccessGroups.APPS,
        AccessGroups.NOTIFICATIONS,
        AccessGroups.FILES,
        AccessGroups.LOCATION,
        AccessGroups.CAPTURE,
        AccessGroups.SYSTEM,
    )

    /** Missing items only. All-files is not nagged (SAF is enough). Shizuku is optional and honest. */
    fun fixQueue(items: List<AccessItem>): List<AccessItem> =
        items.filter { it.nag && it.state != AccessState.GRANTED }
}

object RuntimeGrant {
    fun state(
        granted: Boolean,
        restricted: Boolean,
        sdk: Int,
        missing: List<String>,
        permanent: (String) -> Boolean,
    ): AccessState = when {
        granted -> AccessState.GRANTED
        restricted && RestrictedSettings.applies(sdk) -> AccessState.MAYBE_RESTRICTED
        missing.isNotEmpty() && missing.all(permanent) -> AccessState.NEEDS_SETTINGS
        else -> AccessState.MISSING
    }
}

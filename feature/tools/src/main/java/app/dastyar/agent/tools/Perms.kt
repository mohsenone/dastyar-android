package app.dastyar.agent.tools

/** Permission name constants shared by diagnostics, tools, and JVM tests. */
object Perms {
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val READ_SMS = "android.permission.READ_SMS"
    const val SEND_SMS = "android.permission.SEND_SMS"
    const val RECEIVE_SMS = "android.permission.RECEIVE_SMS"
    const val RECEIVE_MMS = "android.permission.RECEIVE_MMS"
    const val RECEIVE_WAP_PUSH = "android.permission.RECEIVE_WAP_PUSH"
    const val CALL_PHONE = "android.permission.CALL_PHONE"
    const val READ_CALL_LOG = "android.permission.READ_CALL_LOG"
    const val READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
    const val READ_CONTACTS = "android.permission.READ_CONTACTS"
    const val WRITE_CONTACTS = "android.permission.WRITE_CONTACTS"
    const val READ_CALENDAR = "android.permission.READ_CALENDAR"
    const val WRITE_CALENDAR = "android.permission.WRITE_CALENDAR"
    const val CAMERA = "android.permission.CAMERA"
    const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"

    val SMS_GROUP = listOf(READ_SMS, SEND_SMS, RECEIVE_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH)

    /** Runtime permissions Android 13+ can block for sideloaded apps until restricted settings are allowed. */
    val RESTRICTED_RUNTIME = setOf(
        READ_SMS,
        SEND_SMS,
        RECEIVE_SMS,
        RECEIVE_MMS,
        RECEIVE_WAP_PUSH,
        READ_CALL_LOG,
    )
}

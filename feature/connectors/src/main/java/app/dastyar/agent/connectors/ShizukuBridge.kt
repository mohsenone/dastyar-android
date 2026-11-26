package app.dastyar.agent.connectors

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Optional Shizuku. The app runs without it. Commands are an argument array, not a shell string.
 * newProcess is private in Shizuku 13.1.5; we call that same method and keep it from being stripped.
 */
object ShizukuBridge {
    fun status(): String = when {
        !ping() -> "missing"
        !authorized() -> "unauthorized"
        else -> "ready"
    }

    fun ping(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun authorized(): Boolean = ping() && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        if (!ping() || authorized()) return
        runCatching { Shizuku.requestPermission(1) }
    }

    fun exec(args: List<String>): String {
        if (!ping()) return "Shizuku is not running. Install Shizuku, start it, and authorize Dastyar."
        if (!authorized()) return "Shizuku is running but Dastyar is not authorized. Open Shizuku and allow Dastyar."
        val reason = validate(args) ?: return run(args)
        return reason
    }

    fun validate(args: List<String>): String? {
        if (args.size < 2) return "Missing command."
        if (args.any { it.any { ch -> ch == '\n' || ch == '\u0000' } }) return "Rejected."
        return when (args[0]) {
            "settings" -> {
                if (args[1] !in setOf("get", "put")) return "Only settings get or put."
                if (args.getOrNull(2) !in setOf("system", "secure", "global")) return "Namespace must be system, secure, or global."
                if (args[1] == "get" && args.size != 4) return "settings get needs a namespace and a key."
                if (args[1] == "put" && args.size != 5) return "settings put needs a namespace, a key, and a value."
                if (!KEY.matches(args[3])) return "The settings key looks invalid."
                null
            }
            "pm" -> {
                if (args[1] !in setOf("grant", "revoke")) return "Only pm grant or revoke."
                if (args.size != 4) return "pm needs a package and a permission."
                if (!PKG.matches(args[2]) || !KEY.matches(args[3])) return "Package or permission looks invalid."
                null
            }
            "am" -> {
                if (args[1] == "force-stop" && args.size == 3 && PKG.matches(args[2])) return null
                if (args[1] == "start" && args.getOrNull(2) == "-n" && args.size == 4 && COMPONENT.matches(args[3])) return null
                "Only am force-stop <package> or am start -n package/class."
            }
            else -> "Only settings, pm, and am are exposed."
        }
    }

    private fun run(args: List<String>): String = try {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        val process = method.invoke(null, args.toTypedArray(), null, null) as Process
        val finished = process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            process.destroy()
            "Timed out"
        } else {
            val out = process.inputStream.bufferedReader().readText().take(8_000)
            val err = process.errorStream.bufferedReader().readText().take(2_000)
            "exit=${process.exitValue()}\n$out\n$err".trim()
        }
    } catch (error: Exception) {
        "Shizuku command failed: ${error.message ?: error.javaClass.simpleName}"
    }

    private val KEY = Regex("^[A-Za-z0-9._:-]+$")
    private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    private val COMPONENT = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9._$]+$")
}

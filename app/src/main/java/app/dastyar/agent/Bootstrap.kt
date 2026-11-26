package app.dastyar.agent

import android.content.Context
import app.dastyar.agent.data.AppLanguage

internal object Bootstrap {
    private const val FILE = "bootstrap"

    fun language(context: Context): String =
        AppLanguage.normalize(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("language", null))

    fun writeLanguage(context: Context, language: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("language", AppLanguage.normalize(language))
            .commit()
    }

    fun onboardingDone(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("onboarding_done", false)

    fun writeOnboarding(context: Context, done: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("onboarding_done", done).apply()
    }
}

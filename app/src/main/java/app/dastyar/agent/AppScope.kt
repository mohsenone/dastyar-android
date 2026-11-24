package app.dastyar.agent

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Singleton
class AppScope @Inject constructor() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

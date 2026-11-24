package app.dastyar.agent

import app.dastyar.agent.data.ChatRepository
import app.dastyar.agent.data.ProviderRepository
import app.dastyar.agent.data.RoutineRepository
import app.dastyar.agent.data.SettingsStore
import app.dastyar.agent.loop.AgentLoop
import app.dastyar.agent.loop.ConfirmationBus
import app.dastyar.agent.tools.RoutineSchedulerPort
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntry {
    fun loop(): AgentLoop
    fun routines(): RoutineRepository
    fun chats(): ChatRepository
    fun settings(): SettingsStore
    fun scheduler(): RoutineSchedulerPort
    fun scope(): AppScope
    fun confirm(): ConfirmationBus
    fun foreground(): ForegroundGate
    fun providers(): ProviderRepository
}

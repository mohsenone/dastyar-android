package app.dastyar.agent

import app.dastyar.agent.loop.RunForeground
import app.dastyar.agent.security.UserAuthenticator
import app.dastyar.agent.tools.RoutineSchedulerPort
import app.dastyar.agent.tools.UiBridge
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds
    @Singleton
    abstract fun authenticator(impl: BiometricAuthenticator): UserAuthenticator

    @Binds
    @Singleton
    abstract fun ui(impl: AppUiBridge): UiBridge

    @Binds
    @Singleton
    abstract fun foreground(impl: ForegroundGate): RunForeground

    @Binds
    @Singleton
    abstract fun scheduler(impl: AlarmRoutineScheduler): RoutineSchedulerPort
}

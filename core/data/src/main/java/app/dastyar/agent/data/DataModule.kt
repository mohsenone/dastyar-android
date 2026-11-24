package app.dastyar.agent.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): DastyarDatabase =
        Room.databaseBuilder(context, DastyarDatabase::class.java, "dastyar.db")
            .fallbackToDestructiveMigration()
            .build()
}

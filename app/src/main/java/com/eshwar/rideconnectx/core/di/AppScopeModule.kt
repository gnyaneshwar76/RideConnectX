package com.eshwar.rideconnectx.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A coroutine scope that lives as long as the process.
 *
 * Needed for work that must finish even though the caller went away — the
 * clearest case being sign-in: creating the Firebase user flips the auth state,
 * which navigates off the Sign In screen, which clears the AuthViewModel and
 * cancels its viewModelScope. Anything still suspended at that moment dies with
 * it. Cloud writes that follow a successful sign-in run here instead.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

package com.eshwar.rideconnectx.core.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.ktx.Firebase
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.eshwar.rideconnectx.BuildConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(@ApplicationContext context: Context): FirebaseAuth? =
        if (!BuildConfig.CLOUD_ENABLED) null
        else if (FirebaseApp.getApps(context).isEmpty()) {
            Log.w("RCX-Firebase", "No FirebaseApp (no google-services.json): guest mode only")
            null
        } else Firebase.auth

    @Provides
    @Singleton
    fun provideFirestore(@ApplicationContext context: Context): FirebaseFirestore? =
        if (!BuildConfig.CLOUD_ENABLED || FirebaseApp.getApps(context).isEmpty()) null
        else newFirestore()

    /** Offline persistence: reads resolve from cache without signal; writes queue until it returns. */
    fun newFirestore(): FirebaseFirestore = Firebase.firestore.apply {
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
    }
}

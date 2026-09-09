package com.talkswithtanha.twt.core.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.talkswithtanha.twt.core.data.AcademyRepository
import com.talkswithtanha.twt.core.data.AccessTokenRepository
import com.talkswithtanha.twt.core.data.ChatRepository
import com.talkswithtanha.twt.core.data.ExchangeRateRepository
import com.talkswithtanha.twt.core.data.FirebaseAcademyRepository
import com.talkswithtanha.twt.core.data.FirebaseAccessTokenRepository
import com.talkswithtanha.twt.core.data.FirebaseChatRepository
import com.talkswithtanha.twt.core.data.FirebaseExchangeRateRepository
import com.talkswithtanha.twt.core.data.FirebaseSignalFollowRepository
import com.talkswithtanha.twt.core.data.FirebaseSignalRepository
import com.talkswithtanha.twt.core.data.FirebaseSupportConfigRepository
import com.talkswithtanha.twt.core.data.FirebaseUserRepository
import com.talkswithtanha.twt.core.data.SignalFollowRepository
import com.talkswithtanha.twt.core.data.SignalRepository
import com.talkswithtanha.twt.core.data.SupportConfigRepository
import com.talkswithtanha.twt.core.data.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun auth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun firestore(): FirebaseFirestore = Firebase.firestore.apply {
        // Offline persistence, on by default on Android, made explicit.
        //
        // It is what lets the signals feed and a chat room open instantly on a
        // cold start rather than after a round trip. The one place it must not
        // be trusted is reading an access token, which is why that read asks for
        // `Source.SERVER` by name.
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings { })
        }
    }

    @Provides
    @Singleton
    fun messaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    /**
     * The scope application-scoped listeners live in.
     *
     * A `SupervisorJob` so one repository's listener failing does not cancel
     * every other listener in the app, and no lifecycle owner because these are
     * meant to outlive every screen.
     */
    @Provides
    @Singleton
    fun applicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun userRepository(impl: FirebaseUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun accessTokenRepository(impl: FirebaseAccessTokenRepository): AccessTokenRepository

    @Binds
    @Singleton
    abstract fun signalRepository(impl: FirebaseSignalRepository): SignalRepository

    @Binds
    @Singleton
    abstract fun signalFollowRepository(impl: FirebaseSignalFollowRepository): SignalFollowRepository

    @Binds
    @Singleton
    abstract fun chatRepository(impl: FirebaseChatRepository): ChatRepository

    @Binds
    @Singleton
    abstract fun exchangeRateRepository(impl: FirebaseExchangeRateRepository): ExchangeRateRepository

    @Binds
    @Singleton
    abstract fun supportConfigRepository(impl: FirebaseSupportConfigRepository): SupportConfigRepository

    @Binds
    @Singleton
    abstract fun academyRepository(impl: FirebaseAcademyRepository): AcademyRepository
}
